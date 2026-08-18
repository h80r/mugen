package tachiyomi.data.achievement

import android.content.SharedPreferences
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import logcat.LogPriority
import logcat.logcat
import tachiyomi.i18n.MR

/**
 * Manages unlockable content that is unlocked via achievements.
 * Handles themes, badges, display preferences, and other unlockables.
 */
class UnlockableManager(
    private val preferences: SharedPreferences,
) {

    companion object {
        private const val PREFIX = "unlocked_"

        /**
         * Tombstones for unlockables removed from the game (achievements v25).
         * Old backups / stale DB rows may still carry these ids; they must never
         * be granted again.
         */
        private val REMOVED_UNLOCKABLE_IDS = setOf(
            "theme_achievement_gold",
            "theme_achievement_sapphire",
        )

        /**
         * Every reward id granted by any achievement in achievements.json
         * (`unlockableId` + `rewards[].id` across all entries, easter eggs
         * included). The achievement engine that grants these is being
         * removed, so these are always-unlocked to avoid stranding the
         * cosmetics behind an unreachable quest.
         */
        private val ACHIEVEMENT_UNLOCKABLE_IDS = setOf(
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
    }

    /**
     * Check if an unlockable is unlocked
     */
    fun isUnlockableUnlocked(unlockableId: String): Boolean {
        return preferences.getBoolean("$PREFIX$unlockableId", false)
    }

    /**
     * Mark an unlockable as unlocked
     */
    fun setUnlockableUnlocked(unlockableId: String) {
        preferences.edit(commit = true) {
            putBoolean("$PREFIX$unlockableId", true)
        }
        logcat(LogPriority.INFO) { "Unlockable unlocked: $unlockableId" }
    }

    /**
     * Remove a previously granted unlockable (legacy cleanup).
     */
    fun removeUnlockable(unlockableId: String) {
        val key = "$PREFIX$unlockableId"
        if (preferences.contains(key)) {
            preferences.edit(commit = true) {
                remove(key)
            }
            logcat(LogPriority.INFO) { "Unlockable removed: $unlockableId" }
        }
    }

    /**
     * Get all unlocked unlockables, including ids that are always-unlocked
     * via [isDefaultUnlockable] (they are never written to prefs, so callers
     * that only scan stored keys would otherwise miss them).
     */
    fun getUnlockedUnlockables(): Set<String> {
        val allKeys = preferences.all.keys
        val storedUnlocked = allKeys
            .filter { it.startsWith(PREFIX) }
            .filter { preferences.getBoolean(it, false) }
            .map { it.removePrefix(PREFIX) }
            .toSet()
        return storedUnlocked + ACHIEVEMENT_UNLOCKABLE_IDS
    }

    /**
     * Observe all unlocked unlockables as a reactive Flow.
     * Emits a new set whenever any unlockable pref changes.
     */
    fun observeUnlockedUnlockables(): Flow<Set<String>> = callbackFlow {
        fun snapshot(): Set<String> {
            val allKeys = preferences.all.keys
            val storedUnlocked = allKeys
                .filter { it.startsWith(PREFIX) }
                .filter { preferences.getBoolean(it, false) }
                .map { it.removePrefix(PREFIX) }
                .toSet()
            return storedUnlocked + ACHIEVEMENT_UNLOCKABLE_IDS
        }
        // Emit current state immediately.
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(PREFIX)) {
                trySend(snapshot())
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /**
     * Check if a theme is available (unlocked)
     */
    fun isThemeAvailable(themeId: String): Boolean {
        val normalizedThemeId = themeId.removePrefix("theme_")
        val canonicalThemeId = normalizedThemeId.uppercase()
        val unlockableId = "theme_$normalizedThemeId"

        if (isDefaultUnlockable(unlockableId)) return true

        return isUnlockableUnlocked(unlockableId) ||
            isUnlockableUnlocked("theme_$canonicalThemeId") ||
            isUnlockableUnlocked("theme_${canonicalThemeId.lowercase()}")
    }

    /**
     * Generic availability check for Treasury cosmetics. UI should prefer this
     * method for aura/profile/avatar/home/special unlockables so all reward
     * types use the same source of truth.
     */
    fun isUnlockableAvailable(unlockableId: String): Boolean {
        if (isDefaultUnlockable(unlockableId)) return true
        return isUnlockableUnlocked(unlockableId)
    }

    private fun isDefaultUnlockable(unlockableId: String): Boolean {
        return unlockableId.startsWith("default_") ||
            unlockableId.startsWith("theme_default_") ||
            unlockableId.startsWith("badge_default_") ||
            unlockableId.startsWith("display_default_") ||
            unlockableId in ACHIEVEMENT_UNLOCKABLE_IDS
    }

    /**
     * Get unlockable display name (localized) as a StringResource.
     */
    fun getUnlockableNameRes(unlockableId: String): StringResource? {
        return when (unlockableId) {
            // Themes
            "theme_master" -> MR.strings.unlockable_theme_master
            "theme_ONYX_GOLD" -> MR.strings.unlockable_theme_ONYX_GOLD
            "theme_SAKURA_NOIR" -> MR.strings.unlockable_theme_SAKURA_NOIR
            "theme_NEBULA_TIDE" -> MR.strings.unlockable_theme_NEBULA_TIDE
            "theme_EVENT_HORIZON" -> MR.strings.unlockable_theme_EVENT_HORIZON
            "theme_void_red" -> MR.strings.unlockable_theme_void_red
            "theme_AURORA_PRIME" -> MR.strings.unlockable_theme_AURORA_PRIME
            "theme_LATTICE_PROTOCOL" -> MR.strings.unlockable_theme_LATTICE_PROTOCOL

            // Auras
            "aura_harem" -> MR.strings.unlockable_aura_harem
            "aura_level_up" -> MR.strings.unlockable_aura_level_up
            "aura_matrix" -> MR.strings.unlockable_aura_matrix
            "aura_trinity_orbit" -> MR.strings.unlockable_aura_trinity_orbit
            "aura_deep_focus" -> MR.strings.unlockable_aura_deep_focus
            "aura_shadow_monarch" -> MR.strings.unlockable_aura_shadow_monarch
            "aura_ascendant_gold" -> MR.strings.unlockable_aura_ascendant_gold
            "aura_void_broadcast_red" -> MR.strings.unlockable_aura_void_broadcast_red

            // Profile presets
            "profile_nickname_effect_aurora_crown" -> MR.strings.unlockable_profile_nickname_effect_aurora_crown
            "profile_nickname_effect_glitch_rune" -> MR.strings.unlockable_profile_nickname_effect_glitch_rune
            "profile_nickname_effect_cipher" -> MR.strings.unlockable_profile_nickname_effect_cipher
            "profile_nickname_effect_glitch_rune_red" -> MR.strings.unlockable_profile_nickname_effect_glitch_rune_red

            // Avatar presets
            "avatar_frame_neon" -> MR.strings.unlockable_avatar_frame_neon
            "avatar_frame_hologram" -> MR.strings.unlockable_avatar_frame_hologram
            "avatar_frame_prismatic" -> MR.strings.unlockable_avatar_frame_prismatic
            "avatar_frame_glitch_red" -> MR.strings.unlockable_avatar_frame_glitch_red

            // Home presets
            "home_badge_orbit" -> MR.strings.unlockable_home_badge_orbit
            "home_badge_crown" -> MR.strings.unlockable_home_badge_crown
            "home_badge_shuriken" -> MR.strings.unlockable_home_badge_shuriken

            // Special visual rewards
            "special_background_petal_storm" -> MR.strings.unlockable_special_background_petal_storm
            "special_background_neon_orbit" -> MR.strings.unlockable_special_background_neon_orbit
            "special_background_event_horizon_library" -> MR.strings.unlockable_special_background_event_horizon_library
            "special_background_void_weeping_red" -> MR.strings.unlockable_special_background_void_weeping_red
            "special_tab_glow" -> MR.strings.unlockable_special_tab_glow
            "special_navbar_aurora_celestial" -> MR.strings.unlockable_special_navbar_aurora_celestial
            "special_navbar_lattice_circuit" -> MR.strings.unlockable_special_navbar_lattice_circuit
            else -> null
        }
    }

    /**
     * Get unlockable display name fallback string.
     */
    fun getUnlockableName(unlockableId: String): String {
        return unlockableId
            .removePrefix("theme_")
            .removePrefix("aura_")
            .removePrefix("title_")
            .removePrefix("special_background_")
            .removePrefix("special_")
            .removePrefix("profile_nickname_effect_")
            .removePrefix("avatar_frame_")
            .removePrefix("home_badge_")
            .replace("_", " ")
            .capitalize()
    }

    /**
     * Get unlockable type (theme, badge, display, etc.)
     */
    fun getUnlockableType(unlockableId: String): UnlockableType {
        return when {
            unlockableId.startsWith("theme_") -> UnlockableType.THEME
            unlockableId.startsWith("aura_") -> UnlockableType.AURA
            unlockableId.startsWith("title_") -> UnlockableType.TITLE
            unlockableId.startsWith("badge_") -> UnlockableType.BADGE
            unlockableId.startsWith("display_") -> UnlockableType.DISPLAY
            unlockableId.startsWith("profile_") -> UnlockableType.PROFILE
            unlockableId.startsWith("avatar_") -> UnlockableType.AVATAR
            unlockableId.startsWith("reader_") -> UnlockableType.READER
            unlockableId.startsWith("home_") -> UnlockableType.HOME
            unlockableId.startsWith("special_") -> UnlockableType.SPECIAL
            else -> UnlockableType.UNKNOWN
        }
    }

}

/**
 * Types of unlockables
 */
enum class UnlockableType {
    THEME,
    AURA,
    TITLE,
    BADGE,
    DISPLAY,
    PROFILE,
    AVATAR,
    READER,
    HOME,
    SPECIAL,
    UNKNOWN,
}

private inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
