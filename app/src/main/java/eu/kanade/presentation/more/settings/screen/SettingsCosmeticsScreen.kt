package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScreen
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.LocalBackPress
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Settings > Appearance > Cosméticos: reuses Treasury's own visual selector components
 * (card grids, icons, descriptions) for every cosmetic Treasury controls except theme
 * (Appearance already has its own theme selector), with lock/unlock gating neutralized
 * — a synthetic "everything unlocked" input, not a code fork — so it works ahead of
 * `unlock-easter-egg-cosmetics-by-default`. A fixed identity preview card (avatar frame,
 * nickname effect, home badge, profile title) stays pinned above the scrollable selectors,
 * mirroring Treasury's own preview but excluding theme/aura, which don't factor into that card.
 */
object SettingsCosmeticsScreen : Screen {

    @Composable
    override fun Content() {
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        val state = rememberLazyListState()

        val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
        val name by userProfilePreferences.name().collectAsState()
        val avatarUrl by userProfilePreferences.avatarUrl().collectAsState()
        val avatarFrameStyleKey by userProfilePreferences.avatarFrameStyle().collectAsState()
        val homeBadgeStyleKey by userProfilePreferences.homeBadgeStyle().collectAsState()
        val profileTitleKey by userProfilePreferences.profileTitle().collectAsState()
        val nicknameEffectKey by userProfilePreferences.nicknameEffect().collectAsState()

        val nicknameFontPreset = remember(userProfilePreferences) {
            eu.kanade.tachiyomi.ui.home.NicknameFontPreset.fromKey(userProfilePreferences.nicknameFont().get())
        }
        val nicknameColorPreset = remember(userProfilePreferences) {
            eu.kanade.tachiyomi.ui.home.NicknameColorPreset.fromKey(userProfilePreferences.nicknameColor().get())
        }
        val activeNicknameStyle = remember(nicknameFontPreset, nicknameColorPreset, nicknameEffectKey) {
            eu.kanade.tachiyomi.ui.home.NicknameStyle(
                font = nicknameFontPreset,
                fontSize = userProfilePreferences.nicknameFontSize().get(),
                color = nicknameColorPreset,
                outline = userProfilePreferences.nicknameOutline().get(),
                outlineWidth = userProfilePreferences.nicknameOutlineWidth().get(),
                glow = userProfilePreferences.nicknameGlow().get(),
                effect = eu.kanade.tachiyomi.ui.home.NicknameEffectPreset.fromKey(nicknameEffectKey),
                customColorHex = userProfilePreferences.nicknameCustomColorHex().get(),
            )
        }

        SettingsScaffold(
            title = stringResource(AYMR.strings.pref_cosmetics_title),
            uiStyle = uiStyle,
            onBackPressed = resolveSearchableSettingsBackPress(
                handleBack = handleBack,
                navigatorPop = navigator::pop,
            ),
            topBarCanScroll = { state.canScroll() },
        ) { contentPadding ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
            ) {
                TreasuryIdentityPreviewCard(
                    name = name,
                    avatarUrl = avatarUrl,
                    activeNicknameStyle = activeNicknameStyle,
                    avatarFrameStyleKey = avatarFrameStyleKey,
                    homeBadgeStyleKey = homeBadgeStyleKey,
                    profileTitleKey = profileTitleKey,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                PreferenceScreen(
                    items = getPreferences(),
                    modifier = Modifier.weight(1f),
                    state = state,
                    contentPadding = PaddingValues(
                        top = 0.dp,
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                )
            }
        }
    }

    @Composable
    private fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val userProfilePreferences = remember { Injekt.get<UserProfilePreferences>() }
        val unlockableManager = remember { Injekt.get<UnlockableManager>() }

        val amoled by uiPreferences.themeDarkAmoled().collectAsState()

        // Every unlockable id ever checked by the reused Treasury components — passing all
        // of them back as "unlocked" makes every isUnlocked check inside those components
        // evaluate true, without touching their code.
        val allUnlockedForPreview = remember {
            setOf(
                "aura_void_broadcast_red", "aura_harem", "aura_matrix", "aura_level_up",
                "aura_trinity_orbit", "aura_deep_focus", "aura_shadow_monarch", "aura_ascendant_gold",
                "special_background_petal_storm", "special_background_neon_orbit",
                "special_background_trinity_constellation", "special_background_deep_space_archive",
                "special_background_shadow_realm", "special_background_event_horizon_library",
                "special_background_void_weeping_red",
                "special_tab_glow", "special_navbar_aurora_celestial", "special_navbar_lattice_circuit",
                "title_trinity_initiate", "title_finisher", "title_closer", "title_deep_reader", "title_rank_4",
                "profile_nickname_effect_aurora_crown", "profile_nickname_effect_glitch_rune",
                "profile_nickname_effect_glitch_rune_red", "profile_nickname_effect_cipher",
                "profile_nickname_effect_trinity_prism", "profile_nickname_effect_shadow_crown",
                "profile_nickname_effect_rank_sigils",
                "avatar_frame_glitch_red", "avatar_frame_neon", "avatar_frame_hologram",
                "avatar_frame_prismatic", "avatar_frame_trinity_orbit", "avatar_frame_deep_archive",
                "avatar_frame_hybrid_scroll", "avatar_frame_ascendant",
                "home_badge_orbit", "home_badge_crown", "home_badge_shuriken", "home_badge_trinity",
                "home_badge_finisher", "home_badge_immersion", "home_badge_ascendant",
            )
        }

        return listOf(
            getAuraGroup(uiPreferences, unlockableManager, allUnlockedForPreview, amoled),
            getSpecialBackgroundGroup(uiPreferences, unlockableManager, allUnlockedForPreview, amoled),
            getTabCustomizationGroup(uiPreferences, allUnlockedForPreview, amoled),
            getProfileTitleGroup(userProfilePreferences, allUnlockedForPreview, amoled),
            getProfileEffectGroup(userProfilePreferences, unlockableManager, allUnlockedForPreview, amoled),
            getAvatarFrameGroup(userProfilePreferences, unlockableManager, allUnlockedForPreview, amoled),
            getHomeBadgeGroup(userProfilePreferences, unlockableManager, allUnlockedForPreview, amoled),
        )
    }

    @Composable
    private fun getAuraGroup(
        uiPreferences: UiPreferences,
        unlockableManager: UnlockableManager,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.treasury_auras),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.treasury_auras),
                ) {
                    TreasuryAuraSelector(
                        uiPreferences = uiPreferences,
                        unlockableManager = unlockableManager,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }

    @Composable
    private fun getSpecialBackgroundGroup(
        uiPreferences: UiPreferences,
        unlockableManager: UnlockableManager,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        val specialBackgroundStyleKey by uiPreferences.specialBackgroundStyle().collectAsState()

        val presets = listOf(
            TreasuryPreset(
                unlockableId = "special_background_petal_storm",
                title = unlockableManager.getUnlockableNameRes("special_background_petal_storm")
                    ?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("special_background_petal_storm"),
                description = stringResource(AYMR.strings.treasury_reward_petal_storm_background_description),
                accentColor = Color(0xFFFF8FB1),
                isActive = { specialBackgroundStyleKey == "petal_storm" },
                onApply = { uiPreferences.specialBackgroundStyle().set("petal_storm") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_neon_orbit",
                title = unlockableManager.getUnlockableNameRes("special_background_neon_orbit")
                    ?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("special_background_neon_orbit"),
                description = stringResource(AYMR.strings.treasury_reward_neon_orbit_background_description),
                accentColor = Color(0xFF6EF6FF),
                isActive = { specialBackgroundStyleKey == "neon_orbit" },
                onApply = { uiPreferences.specialBackgroundStyle().set("neon_orbit") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_trinity_constellation",
                title = stringResource(AYMR.strings.treasury_bg_trinity_constellation_title),
                description = stringResource(AYMR.strings.treasury_bg_trinity_constellation_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { specialBackgroundStyleKey == "trinity_constellation" },
                onApply = { uiPreferences.specialBackgroundStyle().set("trinity_constellation") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_deep_space_archive",
                title = stringResource(AYMR.strings.treasury_bg_deep_space_archive_title),
                description = stringResource(AYMR.strings.treasury_bg_deep_space_archive_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { specialBackgroundStyleKey == "deep_space_archive" },
                onApply = { uiPreferences.specialBackgroundStyle().set("deep_space_archive") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_shadow_realm",
                title = stringResource(AYMR.strings.treasury_bg_shadow_realm_title),
                description = stringResource(AYMR.strings.treasury_bg_shadow_realm_desc),
                accentColor = Color(0xFFB36BFF),
                isActive = { specialBackgroundStyleKey == "shadow_realm" },
                onApply = { uiPreferences.specialBackgroundStyle().set("shadow_realm") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_event_horizon_library",
                title = stringResource(AYMR.strings.treasury_bg_event_horizon_title),
                description = stringResource(AYMR.strings.treasury_bg_event_horizon_desc),
                accentColor = Color(0xFF1A4FE0),
                isActive = { specialBackgroundStyleKey == "event_horizon_library" },
                onApply = { uiPreferences.specialBackgroundStyle().set("event_horizon_library") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "special_background_void_weeping_red",
                title = stringResource(AYMR.strings.treasury_reward_void_weeping_red_title),
                description = stringResource(AYMR.strings.treasury_reward_void_weeping_red_description),
                accentColor = Color(0xFFFF1E27),
                isActive = { specialBackgroundStyleKey == "void_weeping_red" },
                onApply = { uiPreferences.specialBackgroundStyle().set("void_weeping_red") },
                onDeactivate = { uiPreferences.specialBackgroundStyle().set("none") },
            ),
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.treasury_background_effects),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.treasury_background_effects),
                ) {
                    TreasuryToggleSelector(
                        title = stringResource(AYMR.strings.treasury_background_effects),
                        subtitle = stringResource(AYMR.strings.treasury_background_effects_subtitle),
                        presets = presets,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }

    @Composable
    private fun getTabCustomizationGroup(
        uiPreferences: UiPreferences,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        val showTabGlow by uiPreferences.showTabGlow().collectAsState()
        val showCelestialNavbar by uiPreferences.showCelestialNavbar().collectAsState()
        val showCircuitNavbar by uiPreferences.showCircuitNavbar().collectAsState()

        val presets = listOf(
            TreasuryPreset(
                unlockableId = "special_tab_glow",
                title = stringResource(MR.strings.reward_special_tab_glow_title),
                description = stringResource(MR.strings.reward_special_tab_glow_desc),
                accentColor = Color(0xFF00E5FF),
                isActive = { showTabGlow },
                onApply = { uiPreferences.showTabGlow().set(true) },
                onDeactivate = { uiPreferences.showTabGlow().set(false) },
            ),
            TreasuryPreset(
                unlockableId = "special_navbar_aurora_celestial",
                title = stringResource(MR.strings.reward_special_navbar_aurora_celestial_title),
                description = stringResource(MR.strings.reward_special_navbar_aurora_celestial_desc),
                accentColor = Color(0xFF7C4DFF),
                isActive = { showCelestialNavbar },
                onApply = { uiPreferences.showCelestialNavbar().set(true) },
                onDeactivate = { uiPreferences.showCelestialNavbar().set(false) },
            ),
            TreasuryPreset(
                unlockableId = "special_navbar_lattice_circuit",
                title = stringResource(MR.strings.reward_special_navbar_lattice_circuit_title),
                description = stringResource(MR.strings.reward_special_navbar_lattice_circuit_desc),
                accentColor = Color(0xFF0095AE),
                isActive = { showCircuitNavbar },
                onApply = { uiPreferences.showCircuitNavbar().set(true) },
                onDeactivate = { uiPreferences.showCircuitNavbar().set(false) },
            ),
        )

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.treasury_tab_customization),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.treasury_tab_customization),
                ) {
                    TreasuryToggleSelector(
                        title = stringResource(MR.strings.treasury_tab_customization),
                        subtitle = stringResource(MR.strings.treasury_tab_customization),
                        presets = presets,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }

    @Composable
    private fun getProfileTitleGroup(
        userProfilePreferences: UserProfilePreferences,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        val profileTitleKey by userProfilePreferences.profileTitle().collectAsState()

        val presets = listOf(
            TreasuryPreset(
                unlockableId = "title_trinity_initiate",
                title = stringResource(AYMR.strings.treasury_title_trinity_initiate_title),
                description = stringResource(AYMR.strings.treasury_title_trinity_initiate_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { profileTitleKey == "title_trinity_initiate" },
                onApply = { userProfilePreferences.profileTitle().set("title_trinity_initiate") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_finisher",
                title = stringResource(AYMR.strings.treasury_title_finisher_title),
                description = stringResource(AYMR.strings.treasury_title_finisher_desc),
                accentColor = Color(0xFFFFD36E),
                isActive = { profileTitleKey == "title_finisher" },
                onApply = { userProfilePreferences.profileTitle().set("title_finisher") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_closer",
                title = stringResource(AYMR.strings.treasury_title_closer_title),
                description = stringResource(AYMR.strings.treasury_title_closer_desc),
                accentColor = Color(0xFFFFB86B),
                isActive = { profileTitleKey == "title_closer" },
                onApply = { userProfilePreferences.profileTitle().set("title_closer") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_deep_reader",
                title = stringResource(AYMR.strings.treasury_title_deep_reader_title),
                description = stringResource(AYMR.strings.treasury_title_deep_reader_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { profileTitleKey == "title_deep_reader" },
                onApply = { userProfilePreferences.profileTitle().set("title_deep_reader") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "title_rank_4",
                title = stringResource(AYMR.strings.treasury_title_rank_4_title),
                description = stringResource(AYMR.strings.treasury_title_rank_4_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { profileTitleKey == "title_rank_4" },
                onApply = { userProfilePreferences.profileTitle().set("title_rank_4") },
                onDeactivate = { userProfilePreferences.profileTitle().set("none") },
            ),
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.treasury_profile_titles),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.treasury_profile_titles),
                ) {
                    TreasuryToggleSelector(
                        title = stringResource(AYMR.strings.treasury_profile_titles),
                        subtitle = stringResource(AYMR.strings.treasury_profile_titles),
                        presets = presets,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }

    @Composable
    private fun getProfileEffectGroup(
        userProfilePreferences: UserProfilePreferences,
        unlockableManager: UnlockableManager,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        val nicknameEffectKey by userProfilePreferences.nicknameEffect().collectAsState()

        val presets = listOf(
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_aurora_crown",
                title = unlockableManager.getUnlockableNameRes("profile_nickname_effect_aurora_crown")
                    ?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("profile_nickname_effect_aurora_crown"),
                description = stringResource(AYMR.strings.treasury_reward_aurora_crown_description),
                accentColor = Color(0xFFFFD54F),
                isActive = { nicknameEffectKey == "aurora_crown" },
                onApply = { userProfilePreferences.nicknameEffect().set("aurora_crown") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_glitch_rune",
                title = unlockableManager.getUnlockableNameRes("profile_nickname_effect_glitch_rune")
                    ?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("profile_nickname_effect_glitch_rune"),
                description = stringResource(AYMR.strings.treasury_reward_glitch_rune_description),
                accentColor = Color(0xFF40C4FF),
                isActive = { nicknameEffectKey == "glitch_rune" },
                onApply = { userProfilePreferences.nicknameEffect().set("glitch_rune") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_glitch_rune_red",
                title = stringResource(AYMR.strings.treasury_reward_glitch_rune_red_title),
                description = stringResource(AYMR.strings.treasury_reward_glitch_rune_red_description),
                accentColor = Color(0xFFFF003C),
                isActive = { nicknameEffectKey == "glitch_rune_red" },
                onApply = { userProfilePreferences.nicknameEffect().set("glitch_rune_red") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_cipher",
                title = unlockableManager.getUnlockableNameRes("profile_nickname_effect_cipher")
                    ?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("profile_nickname_effect_cipher"),
                description = stringResource(AYMR.strings.treasury_reward_cipher_description),
                accentColor = Color(0xFF69F0AE),
                isActive = { nicknameEffectKey == "cipher" },
                onApply = { userProfilePreferences.nicknameEffect().set("cipher") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_trinity_prism",
                title = stringResource(AYMR.strings.treasury_nickname_trinity_prism_title),
                description = stringResource(AYMR.strings.treasury_nickname_trinity_prism_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { nicknameEffectKey == "trinity_prism" },
                onApply = { userProfilePreferences.nicknameEffect().set("trinity_prism") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_shadow_crown",
                title = stringResource(AYMR.strings.treasury_nickname_shadow_crown_title),
                description = stringResource(AYMR.strings.treasury_nickname_shadow_crown_desc),
                accentColor = Color(0xFFB36BFF),
                isActive = { nicknameEffectKey == "shadow_crown" },
                onApply = { userProfilePreferences.nicknameEffect().set("shadow_crown") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "profile_nickname_effect_rank_sigils",
                title = stringResource(AYMR.strings.treasury_nickname_rank_sigils_title),
                description = stringResource(AYMR.strings.treasury_nickname_rank_sigils_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { nicknameEffectKey == "rank_sigils" },
                onApply = { userProfilePreferences.nicknameEffect().set("rank_sigils") },
                onDeactivate = { userProfilePreferences.nicknameEffect().set("none") },
            ),
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.treasury_profile_effects),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.treasury_profile_effects),
                ) {
                    TreasuryToggleSelector(
                        title = stringResource(AYMR.strings.treasury_profile_effects),
                        subtitle = stringResource(AYMR.strings.treasury_profile_effects),
                        presets = presets,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }

    @Composable
    private fun getAvatarFrameGroup(
        userProfilePreferences: UserProfilePreferences,
        unlockableManager: UnlockableManager,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        val avatarFrameStyleKey by userProfilePreferences.avatarFrameStyle().collectAsState()

        val presets = listOf(
            TreasuryPreset(
                unlockableId = "avatar_frame_glitch_red",
                title = stringResource(AYMR.strings.treasury_reward_glitch_frame_red_title),
                description = stringResource(AYMR.strings.treasury_reward_glitch_frame_red_description),
                accentColor = Color(0xFFFF003C),
                isActive = { avatarFrameStyleKey == "glitch_red" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("glitch_red") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_neon",
                title = unlockableManager.getUnlockableNameRes("avatar_frame_neon")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("avatar_frame_neon"),
                description = stringResource(AYMR.strings.treasury_reward_neon_frame_description),
                accentColor = Color(0xFF00E5FF),
                isActive = { avatarFrameStyleKey == "neon" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("neon") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_hologram",
                title = unlockableManager.getUnlockableNameRes("avatar_frame_hologram")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("avatar_frame_hologram"),
                description = stringResource(AYMR.strings.treasury_reward_hologram_frame_description),
                accentColor = Color(0xFFB388FF),
                isActive = { avatarFrameStyleKey == "hologram" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("hologram") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_prismatic",
                title = unlockableManager.getUnlockableNameRes("avatar_frame_prismatic")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("avatar_frame_prismatic"),
                description = stringResource(AYMR.strings.treasury_reward_prismatic_frame_description),
                accentColor = Color(0xFFFF8A65),
                isActive = { avatarFrameStyleKey == "prismatic" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("prismatic") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_trinity_orbit",
                title = stringResource(AYMR.strings.treasury_frame_trinity_orbit_title),
                description = stringResource(AYMR.strings.treasury_frame_trinity_orbit_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { avatarFrameStyleKey == "trinity_orbit" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("trinity_orbit") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_deep_archive",
                title = stringResource(AYMR.strings.treasury_frame_deep_archive_title),
                description = stringResource(AYMR.strings.treasury_frame_deep_archive_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { avatarFrameStyleKey == "deep_archive" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("deep_archive") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_hybrid_scroll",
                title = stringResource(AYMR.strings.treasury_frame_hybrid_scroll_title),
                description = stringResource(AYMR.strings.treasury_frame_hybrid_scroll_desc),
                accentColor = Color(0xFFFFB86B),
                isActive = { avatarFrameStyleKey == "hybrid_scroll" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("hybrid_scroll") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "avatar_frame_ascendant",
                title = stringResource(AYMR.strings.treasury_frame_ascendant_title),
                description = stringResource(AYMR.strings.treasury_frame_ascendant_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { avatarFrameStyleKey == "ascendant" },
                onApply = { userProfilePreferences.avatarFrameStyle().set("ascendant") },
                onDeactivate = { userProfilePreferences.avatarFrameStyle().set("none") },
            ),
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.treasury_avatar_frames),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.treasury_avatar_frames),
                ) {
                    TreasuryToggleSelector(
                        title = stringResource(AYMR.strings.treasury_avatar_frames),
                        subtitle = stringResource(AYMR.strings.treasury_avatar_frames_subtitle),
                        presets = presets,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }

    @Composable
    private fun getHomeBadgeGroup(
        userProfilePreferences: UserProfilePreferences,
        unlockableManager: UnlockableManager,
        unlockedUnlockables: Set<String>,
        amoled: Boolean,
    ): Preference.PreferenceGroup {
        val homeBadgeStyleKey by userProfilePreferences.homeBadgeStyle().collectAsState()

        val presets = listOf(
            TreasuryPreset(
                unlockableId = "home_badge_orbit",
                title = unlockableManager.getUnlockableNameRes("home_badge_orbit")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("home_badge_orbit"),
                description = stringResource(AYMR.strings.treasury_reward_orbit_badge_description),
                accentColor = Color(0xFF64B5F6),
                isActive = { homeBadgeStyleKey == "orbit" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("orbit") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_crown",
                title = unlockableManager.getUnlockableNameRes("home_badge_crown")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("home_badge_crown"),
                description = stringResource(AYMR.strings.treasury_reward_crown_badge_description),
                accentColor = Color(0xFFFFC107),
                isActive = { homeBadgeStyleKey == "crown" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("crown") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_shuriken",
                title = unlockableManager.getUnlockableNameRes("home_badge_shuriken")?.let { stringResource(it) }
                    ?: unlockableManager.getUnlockableName("home_badge_shuriken"),
                description = stringResource(AYMR.strings.treasury_reward_shuriken_badge_description),
                accentColor = Color(0xFFEF5350),
                isActive = { homeBadgeStyleKey == "shuriken" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("shuriken") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_trinity",
                title = stringResource(AYMR.strings.treasury_badge_trinity_title),
                description = stringResource(AYMR.strings.treasury_badge_trinity_desc),
                accentColor = Color(0xFF9C7CFF),
                isActive = { homeBadgeStyleKey == "trinity" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("trinity") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_finisher",
                title = stringResource(AYMR.strings.treasury_badge_finisher_title),
                description = stringResource(AYMR.strings.treasury_badge_finisher_desc),
                accentColor = Color(0xFFFFD36E),
                isActive = { homeBadgeStyleKey == "finisher" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("finisher") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_immersion",
                title = stringResource(AYMR.strings.treasury_badge_immersion_title),
                description = stringResource(AYMR.strings.treasury_badge_immersion_desc),
                accentColor = Color(0xFF5DE7D8),
                isActive = { homeBadgeStyleKey == "immersion" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("immersion") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
            TreasuryPreset(
                unlockableId = "home_badge_ascendant",
                title = stringResource(AYMR.strings.treasury_badge_ascendant_title),
                description = stringResource(AYMR.strings.treasury_badge_ascendant_desc),
                accentColor = Color(0xFFFFE08A),
                isActive = { homeBadgeStyleKey == "ascendant" },
                onApply = { userProfilePreferences.homeBadgeStyle().set("ascendant") },
                onDeactivate = { userProfilePreferences.homeBadgeStyle().set("none") },
            ),
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.treasury_home_hub_rewards),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.treasury_home_hub_rewards),
                ) {
                    TreasuryToggleSelector(
                        title = stringResource(AYMR.strings.treasury_home_hub_rewards),
                        subtitle = stringResource(AYMR.strings.treasury_home_hub_rewards_subtitle),
                        presets = presets,
                        unlockedUnlockables = unlockedUnlockables,
                        amoled = amoled,
                    )
                },
            ),
        )
    }
}
