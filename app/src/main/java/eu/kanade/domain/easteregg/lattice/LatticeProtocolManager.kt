package eu.kanade.domain.easteregg.lattice

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Глобальный выключатель пасхалки. */
object LatticeConfig {
    const val ENABLED = true
}

/**
 * Несущие поверхности. Пороги latch — [LatticeVaultData.THRESHOLDS]
 * (forge), не хардкод в enum.
 */
enum class LatticeCarrier(val id: Int) {
    ANIME(1),
    MANGA(2),
    NOVEL(3),
    ;

    val target: Int
        get() = LatticeVaultData.THRESHOLDS.getOrElse(ordinal) { 1 }.coerceIn(1, 9)
}

/**
 * Оркестратор «Резонанса Каркаса».
 *
 * Prefs-ключи намеренно непрозрачны. PBKDF2 всегда на [Dispatchers.IO] + mutex.
 */
class LatticeProtocolManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("render_pipeline", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cryptoMutex = Mutex()

    @Volatile
    private var lastAttemptAt = 0L

    @Volatile
    private var lastHoldAt = 0L

    @Volatile
    private var cachedBoard: LatticeBoard? = null

    fun onAppStart() {
        if (!LatticeConfig.ENABLED) return
        migrateIfNeeded()
        val count = startCount() + 1
        prefs.edit().putInt(KEY_START_COUNT, count).apply()

        // One soft ambient whisper on first permeable session (never after synthesis).
        if (sessionEligible() &&
            !isSynthesisDone() &&
            !prefs.getBoolean(KEY_WHISPER_SHOWN, false) &&
            latchedCarrierCount() == 0
        ) {
            prefs.edit().putBoolean(KEY_WHISPER_SHOWN, true).apply()
            LatticeSignalBus.emitWhisper(WHISPER_LINE)
        }

        if (!isSynthesisDone() &&
            latchedCarrierCount() == LatticeCarrier.entries.size &&
            isPermeableStart(count)
        ) {
            scope.launch {
                if (openBoard() != null) LatticeSignalBus.emitBreach()
            }
        }
    }

    private fun migrateIfNeeded() {
        if (prefs.getInt(KEY_REV, 0) != LatticeVaultData.VERSION) {
            prefs.edit()
                .putInt(KEY_REV, LatticeVaultData.VERSION)
                .remove(KEY_C1)
                .remove(KEY_C2)
                .remove(KEY_C3)
                .remove(KEY_TOPOLOGY)
                // keep synth_done / residual / whisper / start_count intentionally
                .apply()
            cachedBoard = null
        }
    }

    private fun startCount() = prefs.getInt(KEY_START_COUNT, 0)

    fun isSynthesisDone() = prefs.getBoolean(KEY_SYNTH_DONE, false)

    fun sessionEligible(): Boolean {
        if (!LatticeConfig.ENABLED || isSynthesisDone()) return false
        val count = startCount()
        return count >= MIN_STARTS && (latchedCarrierCount() > 0 || isPermeableStart(count))
    }

    private fun isPermeableStart(count: Int): Boolean {
        val mix = count xor (LatticeVaultData.VERSION and 0x7fff)
        return mix % 3 == 2
    }

    fun carrierCount(carrier: LatticeCarrier): Int = prefs.getInt(keyFor(carrier), 0)

    fun isCarrierLatched(carrier: LatticeCarrier): Boolean =
        carrierCount(carrier) >= carrier.target

    fun latchedCarrierCount(): Int = LatticeCarrier.entries.count { isCarrierLatched(it) }

    fun visibleCarriers(): Set<LatticeCarrier> =
        if (sessionEligible()) {
            LatticeCarrier.entries.filterNot { isCarrierLatched(it) }.toSet()
        } else {
            emptySet()
        }

    /**
     * Резонансное удержание. Anti-spam 400ms.
     * @return true, если именно это удержание захватило несущую.
     */
    fun onCarrierHold(carrier: LatticeCarrier): Boolean {
        if (!LatticeConfig.ENABLED || isSynthesisDone()) return false
        if (isCarrierLatched(carrier)) return false
        val now = System.currentTimeMillis()
        if (now - lastHoldAt < HOLD_COOLDOWN_MS) return false
        lastHoldAt = now
        prefs.edit().putInt(keyFor(carrier), carrierCount(carrier) + 1).apply()
        val latched = isCarrierLatched(carrier)
        if (latched) {
            LatticeSignalBus.emitLatch()
            scope.launch { maybeBreach() }
        }
        return latched
    }

    private suspend fun maybeBreach() {
        if (latchedCarrierCount() < LatticeCarrier.entries.size) return
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < ATTEMPT_COOLDOWN_MS) return
        lastAttemptAt = now
        if (openBoard() != null) LatticeSignalBus.emitBreach()
    }

    suspend fun openBoard(): LatticeBoard? {
        cachedBoard?.let { return it }
        return cryptoMutex.withLock {
            cachedBoard?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val canonical = LatticeCanonical.carriers(
                    carrierCount(LatticeCarrier.ANIME),
                    carrierCount(LatticeCarrier.MANGA),
                    carrierCount(LatticeCarrier.NOVEL),
                )
                val raw = LatticeVault.tryOpen(canonical, LatticeVaultData.STAGE_A) ?: return@withContext null
                val board = LatticeBoard.fromJson(raw.decodeToString()) ?: return@withContext null
                prefs.getString(KEY_TOPOLOGY, null)?.let { board.restoreRotations(it) }
                cachedBoard = board
                board
            }
        }
    }

    fun persistRotations(board: LatticeBoard) {
        prefs.edit().putString(KEY_TOPOLOGY, board.serializeRotations()).apply()
    }

    /**
     * Stage B: open reward vault with current topology.
     * Does **not** share the openBoard rate-limit (that was causing silent
     * one-shot failures when the circuit closed soon after a breach open).
     * Idempotent if already synthesised.
     */
    suspend fun trySynthesize(board: LatticeBoard): Boolean {
        if (isSynthesisDone()) return true
        return cryptoMutex.withLock {
            if (isSynthesisDone()) return@withLock true
            withContext(Dispatchers.IO) {
                val canonical = board.topologyCanonical()
                val raw = LatticeVault.tryOpen(canonical, LatticeVaultData.STAGE_B)
                    ?: return@withContext false
                val payload = LatticePayload.fromJson(raw.decodeToString()) ?: return@withContext false
                prefs.edit().putBoolean(KEY_SYNTH_DONE, true).apply()
                // Callback may touch DI / UI scopes — never swallow.
                runCatching { LatticeSignalBus.onUnlocked?.invoke(payload) }
                LatticeSignalBus.emitSynthesis()
                true
            }
        }
    }

    fun shouldShowResidual(): Boolean =
        isSynthesisDone() && !prefs.getBoolean(KEY_RESIDUAL_SHOWN, false)

    fun markResidualShown() {
        prefs.edit().putBoolean(KEY_RESIDUAL_SHOWN, true).apply()
    }

    fun debugForceBreach() {
        // Allow re-solving the grid in debug without a full reset of unlockables.
        val e = prefs.edit()
        LatticeCarrier.entries.forEach { e.putInt(keyFor(it), it.target) }
        e.putInt(KEY_START_COUNT, MIN_STARTS + 1)
            .remove(KEY_TOPOLOGY)
            .remove(KEY_SYNTH_DONE)
            .remove(KEY_RESIDUAL_SHOWN)
            .apply()
        cachedBoard = null
        lastAttemptAt = 0L
        scope.launch {
            if (openBoard() != null) LatticeSignalBus.emitBreach()
        }
    }

    fun debugReset() {
        prefs.edit().clear().apply()
        cachedBoard = null
        lastAttemptAt = 0L
        lastHoldAt = 0L
    }

    private fun keyFor(carrier: LatticeCarrier): String = when (carrier) {
        LatticeCarrier.ANIME -> KEY_C1
        LatticeCarrier.MANGA -> KEY_C2
        LatticeCarrier.NOVEL -> KEY_C3
    }

    companion object {
        private const val KEY_REV = "core_pipeline_rev"
        private const val KEY_C1 = "render_cache_c1"
        private const val KEY_C2 = "render_cache_c2"
        private const val KEY_C3 = "render_cache_c3"
        private const val KEY_TOPOLOGY = "render_cache_topology"
        private const val KEY_SYNTH_DONE = "pipeline_synth_done"
        private const val KEY_RESIDUAL_SHOWN = "pipeline_residual_shown"
        private const val KEY_START_COUNT = "pipeline_start_count"
        private const val KEY_WHISPER_SHOWN = "pipeline_whisper_shown"

        private const val MIN_STARTS = 5
        private const val ATTEMPT_COOLDOWN_MS = 2000L
        private const val HOLD_COOLDOWN_MS = 400L
        private const val WHISPER_LINE = "FRAME DRIFT DETECTED"

        @Volatile
        private var instance: LatticeProtocolManager? = null

        fun get(context: Context): LatticeProtocolManager =
            instance ?: synchronized(this) {
                instance ?: LatticeProtocolManager(context.applicationContext).also { instance = it }
            }
    }
}
