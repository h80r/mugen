package tachiyomi.data.achievement

import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Every `unlockableId`/`rewards[].id` value present in
 * `app/src/main/assets/achievements/achievements.json` (excluding the
 * tombstoned [UnlockableManager] REMOVED_UNLOCKABLE_IDS), independently
 * transcribed here so the fresh-install test below validates the
 * production allowlist rather than restating it.
 */
private val ALL_ACHIEVEMENT_REWARD_IDS = setOf(
    // Easter eggs: Aurora Heart, Lattice Resonance, Void Broadcast
    "theme_AURORA_PRIME",
    "special_navbar_aurora_celestial",
    "theme_LATTICE_PROTOCOL",
    "special_navbar_lattice_circuit",
    "theme_void_red",
    "profile_nickname_effect_glitch_rune_red",
    "aura_void_broadcast_red",
    "avatar_frame_glitch_red",
    "special_background_void_weeping_red",

    // Themes
    "theme_SAKURA_NOIR",
    "theme_ONYX_GOLD",
    "theme_NEBULA_TIDE",
    "theme_EVENT_HORIZON",

    // Auras
    "aura_level_up",
    "aura_harem",
    "aura_matrix",
    "aura_trinity_orbit",
    "aura_deep_focus",
    "aura_shadow_monarch",
    "aura_ascendant_gold",

    // Titles
    "title_trinity_initiate",
    "title_trinity_master",
    "title_trinity_legend",
    "title_three_realms_collector",
    "title_event_horizon_cartographer",
    "title_finisher",
    "title_closer",
    "title_romance",
    "title_horror",
    "title_isekai",
    "title_sol",
    "title_shadow_monarch",
    "title_weeb",
    "title_focus_reader",
    "title_deep_reader",
    "title_immersion_adept",
    "title_immersion_master",
    "title_hybrid_reader",
    "title_cross_format_scholar",
    "title_anime_novel_master",
    "title_cross_media_beginner",
    "title_cross_media_enthusiast",
    "title_cross_media_champion",
    "title_rank_1",
    "title_rank_2",
    "title_rank_3",
    "title_rank_4",
    "title_rank_5",
    "title_rank_6",
    "title_rank_7",
    "title_rank_8",
    "title_rank_9",
    "title_rank_10",

    // Avatar frames
    "avatar_frame_hologram",
    "avatar_frame_neon",
    "avatar_frame_prismatic",
    "avatar_frame_trinity_orbit",
    "avatar_frame_deep_archive",
    "avatar_frame_hybrid_scroll",
    "avatar_frame_ascendant",

    // Home badges
    "home_badge_shuriken",
    "home_badge_orbit",
    "home_badge_crown",
    "home_badge_trinity",
    "home_badge_finisher",
    "home_badge_immersion",
    "home_badge_ascendant",

    // Profile nickname effects
    "profile_nickname_effect_aurora_crown",
    "profile_nickname_effect_glitch_rune",
    "profile_nickname_effect_cipher",
    "profile_nickname_effect_trinity_prism",
    "profile_nickname_effect_shadow_crown",
    "profile_nickname_effect_rank_sigils",

    // Special backgrounds / navbar / tab
    "special_background_petal_storm",
    "special_background_neon_orbit",
    "special_background_event_horizon_library",
    "special_background_trinity_constellation",
    "special_background_shadow_realm",
    "special_background_deep_space_archive",
    "special_tab_glow",
)

class UnlockableManagerTest {

    @Test
    fun `every achievement reward id is available on a fresh install`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs)

        ALL_ACHIEVEMENT_REWARD_IDS.forEach { id ->
            manager.isUnlockableAvailable(id) shouldBe true
        }
    }

    @Test
    fun `easter-egg reward ids remain available without ever being explicitly unlocked`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs)

        manager.isUnlockableAvailable("theme_void_red") shouldBe true
        manager.isUnlockableAvailable("aura_void_broadcast_red") shouldBe true
    }

    @Test
    fun `removed legacy unlockables stay excluded from the default allowlist`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs)

        manager.isUnlockableAvailable("theme_achievement_gold") shouldBe false
        manager.isUnlockableAvailable("theme_achievement_sapphire") shouldBe false
    }

    @Test
    fun `getUnlockedUnlockables includes default-unlocked achievement rewards on a fresh install`() {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs)

        val unlocked = manager.getUnlockedUnlockables()

        ALL_ACHIEVEMENT_REWARD_IDS.forEach { id ->
            unlocked.contains(id) shouldBe true
        }
    }

    @Test
    fun `observeUnlockedUnlockables emits default-unlocked achievement rewards on a fresh install`() = runTest {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs)

        val firstEmission = manager.observeUnlockedUnlockables().first()

        ALL_ACHIEVEMENT_REWARD_IDS.forEach { id ->
            firstEmission.contains(id) shouldBe true
        }
    }

    @Test
    fun `getUnlockableNameRes returns correct StringResource reference`() {
        val prefs = InMemorySharedPreferences()
        val manager = UnlockableManager(prefs)

        val goldThemeRes = manager.getUnlockableNameRes("theme_ONYX_GOLD")
        goldThemeRes shouldBe tachiyomi.i18n.MR.strings.unlockable_theme_ONYX_GOLD

        manager.getUnlockableNameRes("theme_EVENT_HORIZON") shouldBe
            tachiyomi.i18n.MR.strings.unlockable_theme_EVENT_HORIZON
        manager.getUnlockableNameRes("special_background_event_horizon_library") shouldBe
            tachiyomi.i18n.MR.strings.unlockable_special_background_event_horizon_library

        val invalidRes = manager.getUnlockableNameRes("nonexistent_reward")
        invalidRes shouldBe null
    }
}

/**
 * Tiny in-memory [SharedPreferences] implementation used for unit tests.
 * Implements only the methods exercised by [UnlockableManager].
 */
internal class InMemorySharedPreferences : SharedPreferences {
    private val backing = linkedMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = backing.toMap()

    override fun getString(key: String, defValue: String?): String? =
        backing[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST")
        (backing[key] as? Set<String>)
            ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        (backing[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        (backing[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        (backing[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        (backing[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = backing.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class InMemoryEditor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply {
            removals += key
            pending.remove(key)
        }
        override fun clear() = apply {
            clearAll = true
            pending.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changed = mutableSetOf<String>()
            if (clearAll) {
                backing.keys.forEach { changed += it }
                backing.clear()
            }
            removals.forEach {
                if (backing.remove(it) != null) changed += it
            }
            pending.forEach { (k, v) ->
                if (backing[k] != v) {
                    changed += k
                }
                backing[k] = v
            }
            changed.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
        }
    }
}
