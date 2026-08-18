package eu.kanade.presentation.more.settings.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.components.allAuraPalettes
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_SHAPE
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScreen
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.auroraCardStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.settingsTitleColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.ui.home.components.AvatarFrameDecorations
import eu.kanade.tachiyomi.ui.home.components.avatarGlitch
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.ui.graphics.drawscope.scale as drawScopeScale

/**
 * Settings > Appearance > Cosméticos: the sole cosmetic-selection surface (card grids,
 * icons, descriptions) for every selector formerly hosted by the removed Treasury screen,
 * except theme (Appearance already has its own theme selector). A fixed identity preview
 * card (avatar frame, nickname effect, home badge, profile title) stays pinned above the
 * scrollable selectors.
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

@Composable
internal fun profileTitleDisplayName(titleId: String): String {
    return when (titleId) {
        "title_trinity_initiate" -> stringResource(AYMR.strings.treasury_title_trinity_initiate_title)
        "title_finisher" -> stringResource(AYMR.strings.treasury_title_finisher_title)
        "title_closer" -> stringResource(AYMR.strings.treasury_title_closer_title)
        "title_deep_reader" -> stringResource(AYMR.strings.treasury_title_deep_reader_title)
        "title_rank_4" -> stringResource(AYMR.strings.treasury_title_rank_4_title)
        else -> titleId.removePrefix("title_").replace("_", " ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }
}

@Composable
internal fun TreasuryIdentityPreviewCard(
    name: String,
    avatarUrl: String,
    activeNicknameStyle: eu.kanade.tachiyomi.ui.home.NicknameStyle,
    avatarFrameStyleKey: String,
    homeBadgeStyleKey: String,
    profileTitleKey: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val defaultUserName = stringResource(AYMR.strings.treasury_default_user_name)
    val decoratedName = remember(name, defaultUserName) {
        name.trim().ifEmpty { defaultUserName }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .auroraCardStyle(colors, AURORA_SETTINGS_CARD_SHAPE, applyDarkRimLight = true)
            .semantics(mergeDescendants = true) {
                contentDescription = decoratedName
            },
        shape = AURORA_SETTINGS_CARD_SHAPE,
        colors = CardDefaults.cardColors(
            containerColor = if (!colors.isDark && !colors.isEInk) {
                Color.Transparent
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (colors.isEInk) {
            BorderStroke(
                width = 1.dp,
                color = resolveAuroraMoreCardBorderColor(colors),
            )
        } else {
            null
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val infiniteTransition = rememberInfiniteTransition(label = "identity-preview-blob")
            val wavePhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * kotlin.math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 6000,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "identity-preview-blob-phase",
            )

            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height
                val cosPhase = kotlin.math.cos(wavePhase.toDouble()).toFloat()
                val sinPhase = kotlin.math.sin(wavePhase.toDouble()).toFloat()
                val alphaMultiplier = if (colors.isDark) 0.14f else 0.08f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TreasuryViolet.copy(alpha = 0.85f * alphaMultiplier),
                            Color.Transparent,
                        ),
                        center = Offset(
                            width * 0.25f + width * 0.12f * cosPhase,
                            height * 0.50f + height * 0.25f * sinPhase,
                        ),
                        radius = size.minDimension * 0.65f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TreasuryCyan.copy(alpha = 0.65f * alphaMultiplier),
                            Color.Transparent,
                        ),
                        center = Offset(
                            width * 0.75f - width * 0.15f * cosPhase,
                            height * 0.50f - height * 0.20f * sinPhase,
                        ),
                        radius = size.minDimension * 0.70f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TreasuryGold.copy(alpha = 0.55f * alphaMultiplier),
                            Color.Transparent,
                        ),
                        center = Offset(
                            width * 0.50f + width * 0.10f * sinPhase,
                            height * 0.40f + height * 0.15f * cosPhase,
                        ),
                        radius = size.minDimension * 0.50f,
                    ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val auraTransition = rememberInfiniteTransition(label = "avatar-aura")
                    val auraRotation by auraTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 4000,
                                easing = androidx.compose.animation.core.LinearEasing,
                            ),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "aura-rotation",
                    )

                    if (avatarFrameStyleKey != "none") {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1f
                                    scaleY = 1f
                                    rotationZ = auraRotation
                                },
                        ) {
                            val radius = size.minDimension / 2f - 2.dp.toPx()
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        TreasuryViolet,
                                        TreasuryCyan,
                                        TreasuryGold,
                                        TreasuryViolet,
                                    ),
                                    center = center,
                                ),
                                radius = radius,
                                style = Stroke(width = 3.dp.toPx()),
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = 0.25f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = radius + 8.dp.toPx(),
                                ),
                            )
                        }
                    }

                    val avatarModifier = Modifier
                        .size(if (avatarFrameStyleKey != "none") 62.dp else 70.dp)
                        .clip(CircleShape)
                        .avatarGlitch(avatarFrameStyleKey)

                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = avatarModifier,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = avatarModifier,
                            tint = colors.accent,
                        )
                    }

                    AvatarFrameDecorations(
                        styleKey = avatarFrameStyleKey,
                        accentColor = colors.accent,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    eu.kanade.tachiyomi.ui.home.StyledNicknameText(
                        text = decoratedName,
                        nicknameStyle = activeNicknameStyle,
                        badgeStyleKey = homeBadgeStyleKey,
                    )
                    if (profileTitleKey != "none") {
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            colors.accent,
                                            colors.accent.copy(alpha = 0.4f),
                                        ),
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .background(
                                    color = colors.accent.copy(alpha = 0.07f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = profileTitleDisplayName(profileTitleKey),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val TreasuryGold = Color(0xFFA8841C)
internal val TreasuryViolet = Color(0xFF9C7CFF)
internal val TreasuryCyan = Color(0xFF0095AE)

internal data class TreasuryPreset(
    val unlockableId: String,
    val title: String,
    val description: String,
    // Загадка-подсказка, показываемая вместо сухого "Requires: ...", пока награда заблокирована.
    val lockedRiddle: String? = null,
    val accentColor: Color,
    val isActive: () -> Boolean,
    val onApply: () -> Unit,
    val onDeactivate: () -> Unit,
)

@Composable
internal fun TreasurySectionStage(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.12f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = settingsTitleColor(),
                    lineHeight = 28.sp,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                )
            }
        }
        content()
    }
}

internal fun getRewardIconResourceId(rewardId: String, context: android.content.Context): Int {
    val formattedId = when (rewardId) {
        "special_background_petal_storm" -> "ic_reward_background_petal_storm"
        "special_background_neon_orbit" -> "ic_reward_background_neon_orbit"
        "title_trinity_initiate" -> "ic_reward_nickname_rank_sigils"
        "title_finisher" -> "ic_reward_badge_finisher"
        "title_closer" -> "ic_reward_badge_finisher"
        "title_deep_reader" -> "ic_reward_badge_immersion"
        "title_rank_4" -> "ic_reward_nickname_rank_sigils"
        "profile_nickname_effect_aurora_crown" -> "ic_reward_nickname_aurora_crown"
        "profile_nickname_effect_glitch_rune" -> "ic_reward_nickname_glitch_rune"
        "profile_nickname_effect_glitch_rune_red" -> "ic_reward_nickname_glitch_rune_red"
        "profile_nickname_effect_cipher" -> "ic_reward_nickname_cipher"
        "profile_nickname_effect_trinity_prism" -> "ic_reward_nickname_trinity_prism"
        "profile_nickname_effect_shadow_crown" -> "ic_reward_nickname_shadow_crown"
        "profile_nickname_effect_rank_sigils" -> "ic_reward_nickname_rank_sigils"
        "avatar_frame_neon" -> "ic_reward_frame_neon"
        "avatar_frame_hologram" -> "ic_reward_frame_hologram"
        "avatar_frame_prismatic" -> "ic_reward_frame_prismatic"
        "avatar_frame_glitch_red" -> "ic_reward_frame_glitch_red"
        "home_badge_orbit" -> "ic_reward_badge_orbit"
        "home_badge_crown" -> "ic_reward_badge_crown"
        "home_badge_shuriken" -> "ic_reward_badge_shuriken"
        "home_badge_trinity" -> "ic_reward_badge_trinity"
        "home_badge_finisher" -> "ic_reward_badge_finisher"
        "home_badge_immersion" -> "ic_reward_badge_immersion"
        "home_badge_ascendant" -> "ic_reward_badge_ascendant"
        "avatar_frame_trinity_orbit" -> "ic_reward_frame_trinity_orbit"
        "avatar_frame_deep_archive" -> "ic_reward_frame_deep_archive"
        "avatar_frame_hybrid_scroll" -> "ic_reward_frame_hybrid_scroll"
        "avatar_frame_ascendant" -> "ic_reward_frame_ascendant"
        "special_background_trinity_constellation" -> "ic_reward_background_trinity_constellation"
        "special_background_deep_space_archive" -> "ic_reward_background_deep_space_archive"
        "special_background_shadow_realm" -> "ic_reward_background_shadow_realm"
        "special_background_event_horizon_library" -> "ic_reward_background_event_horizon_library"
        "special_background_void_weeping_red" -> "ic_reward_background_void_weeping_red"
        "special_tab_glow" -> "ic_reward_tab_glow"
        "special_navbar_aurora_celestial" -> "ic_reward_navbar_aurora_celestial"
        "special_navbar_lattice_circuit" -> "ic_reward_navbar_lattice_circuit"
        else -> "ic_reward_$rewardId"
    }

    return try {
        val resourceId = context.resources.getIdentifier(
            formattedId,
            "drawable",
            context.packageName,
        )
        if (resourceId != 0) {
            resourceId
        } else {
            dev.h80r.mugen.R.drawable.ic_badge_default
        }
    } catch (e: Exception) {
        dev.h80r.mugen.R.drawable.ic_badge_default
    }
}

@Composable
private fun Modifier.springPress(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "spring-press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        awaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = { onClick() },
            )
        }
}

@Composable
internal fun TreasuryAuraSelector(
    uiPreferences: UiPreferences,
    unlockableManager: UnlockableManager,
    unlockedUnlockables: Set<String>,
    amoled: Boolean,
) {
    val enabledAuras by uiPreferences.enabledAuras().collectAsStateWithLifecycle()
    val auraPalettes = remember { allAuraPalettes() }
    val context = LocalContext.current

    TreasurySectionStage(
        title = stringResource(AYMR.strings.treasury_auras),
        subtitle = stringResource(AYMR.strings.treasury_auras_subtitle),
        accent = TreasuryCyan,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            auraPalettes.forEachIndexed { index, aura ->
                val isUnlocked = unlockedUnlockables.contains(aura.id)
                val isEnabled = enabledAuras.contains(aura.id)
                val achievementTitle = stringResource(AYMR.strings.treasury_fallback_achievement)
                val rewardIconResId = remember(aura.id) { getRewardIconResourceId(aura.id, context) }

                TreasuryAuraChannel(
                    index = index,
                    title = stringResource(aura.titleRes),
                    description = if (isUnlocked) {
                        stringResource(aura.descriptionRes)
                    } else {
                        stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                    },
                    iconResId = rewardIconResId,
                    accent = aura.accentColor,
                    isUnlocked = isUnlocked,
                    isEnabled = isEnabled,
                    amoled = amoled,
                    onToggle = {
                        uiPreferences.enabledAuras().set(
                            if (isEnabled) {
                                emptySet()
                            } else {
                                setOf(aura.id)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun TreasuryAuraChannel(
    index: Int,
    title: String,
    description: String,
    iconResId: Int,
    accent: Color,
    isUnlocked: Boolean,
    isEnabled: Boolean,
    amoled: Boolean,
    onToggle: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aura-channel-$title")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 9000 + index * 700)),
        label = "aura-rotation-$title",
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200 + index * 220),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aura-breathe-$title",
    )

    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 12.dp, bottomEnd = 30.dp, bottomStart = 12.dp)
    val colors = AuroraTheme.colors
    val isAmoled = colors.isDark && amoled
    val baseBg1 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF0D0D16)
    } else {
        Color.White
    }
    val baseBg2 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF08080E)
    } else {
        Color(0xFFF9F9FB)
    }

    val bgBrush = if (!colors.isDark && !colors.isEInk) {
        if (isEnabled) {
            Brush.verticalGradient(
                listOf(
                    accent.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        }
    } else if (isEnabled) {
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = if (colors.isDark) 0.08f else 0.04f),
                baseBg1,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                baseBg1,
                baseBg2,
            ),
        )
    }
    val borderBrush = if (!colors.isDark && !colors.isEInk) {
        if (isEnabled) {
            Brush.verticalGradient(
                listOf(
                    accent.copy(alpha = 0.60f),
                    accent.copy(alpha = 0.30f),
                    accent.copy(alpha = 0.15f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.12f),
                ),
            )
        }
    } else if (isEnabled) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.40f),
                accent.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)
                } else {
                    Color.Black.copy(alpha = 0.02f)
                },
            ),
        )
    }
    val borderWidth = if (isEnabled) 1.5.dp else 1.dp

    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val indentDp = if (screenWidth < 400) 8.dp else 22.dp

    val activeDesc = stringResource(AYMR.strings.treasury_toggle_active)
    val availableDesc = stringResource(AYMR.strings.treasury_toggle_available)
    val lockedDesc = stringResource(AYMR.strings.treasury_toggle_locked_with_desc, description)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (index % 2 == 0) 0.dp else indentDp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "$title, " +
                    if (isEnabled) {
                        activeDesc
                    } else if (isUnlocked) {
                        availableDesc
                    } else {
                        lockedDesc
                    }
            }
            .graphicsLayer {
                alpha = if (isUnlocked) 1f else 0.50f
            }
            .springPress(enabled = isUnlocked, onClick = onToggle)
            .drawBehind {
                if (!colors.isDark && !colors.isEInk) {
                    val outline = shape.createOutline(size, layoutDirection, this)

                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()

                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Neutral shadow
                    val neutralScaleX = (size.width - neutralInset * 2) / size.width
                    val neutralScaleY = (size.height - neutralInset * 2) / size.height
                    drawScopeScale(scaleX = neutralScaleX, scaleY = neutralScaleY, pivot = center) {
                        translate(left = 0f, top = neutralOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = Color.Black.copy(alpha = 0.035f),
                            )
                        }
                    }

                    // 2. Warm shadow (accent color)
                    val warmScaleX = (size.width - warmInset * 2) / size.width
                    val warmScaleY = (size.height - warmInset * 2) / size.height
                    drawScopeScale(scaleX = warmScaleX, scaleY = warmScaleY, pivot = center) {
                        translate(left = 0f, top = warmOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = accent.copy(alpha = 0.025f),
                            )
                        }
                    }
                }
            }
            .background(bgBrush, shape)
            .border(borderWidth, borderBrush, shape)
            .clickable(enabled = isUnlocked, onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = if (isEnabled) rotation else 0f },
            ) {
                val pulse = if (isEnabled) breathe else 1f
                drawCircle(
                    color = accent.copy(alpha = 0.24f * pulse),
                    radius = size.minDimension * 0.46f,
                    style = Stroke(width = 1.6.dp.toPx()),
                )
                drawLine(
                    color = accent.copy(alpha = 0.60f * pulse),
                    start = Offset(size.width * 0.50f, 0f),
                    end = Offset(size.width * 0.50f, size.height * 0.20f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .scale(if (isEnabled) 1.08f else 1f),
                tint = if (isUnlocked) Color.Unspecified else Color.Gray,
            )
            if (!isUnlocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(AYMR.strings.treasury_cd_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = settingsTitleColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isEnabled) {
                    val checkScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1200),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "check-scale",
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(AYMR.strings.treasury_cd_active),
                        tint = accent,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            },
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = if (isEnabled) 0.72f else 0.18f)),
            )
        }
    }
}

@Composable
internal fun TreasuryToggleSelector(
    title: String,
    subtitle: String,
    presets: List<TreasuryPreset>,
    unlockedUnlockables: Set<String>,
    amoled: Boolean,
) {
    val context = LocalContext.current
    val stageAccent = presets.firstOrNull()?.accentColor ?: TreasuryViolet

    TreasurySectionStage(
        title = title,
        subtitle = subtitle,
        accent = stageAccent,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Ultra-secret navbar cosmetics: no slot until unlocked.
            val ultraSecretNavbarIds = setOf(
                "special_navbar_aurora_celestial",
                "special_navbar_lattice_circuit",
            )
            val visiblePresets = presets.filter { preset ->
                preset.unlockableId !in ultraSecretNavbarIds ||
                    unlockedUnlockables.contains(preset.unlockableId)
            }

            visiblePresets.forEachIndexed { index, preset ->
                val isUnlocked = unlockedUnlockables.contains(preset.unlockableId)
                val isActive = isUnlocked && preset.isActive()
                val achievementTitle = stringResource(AYMR.strings.treasury_fallback_achievement)
                val rewardIconResId = remember(preset.unlockableId) {
                    getRewardIconResourceId(preset.unlockableId, context)
                }

                val description = if (isUnlocked) {
                    preset.description
                } else {
                    preset.lockedRiddle
                        ?: stringResource(AYMR.strings.treasury_requires_achievement, achievementTitle)
                }

                TreasuryArtifactShard(
                    index = index,
                    preset = preset,
                    iconResId = rewardIconResId,
                    isUnlocked = isUnlocked,
                    isActive = isActive,
                    description = description,
                    amoled = amoled,
                    onToggle = {
                        if (isActive) {
                            preset.onDeactivate()
                        } else {
                            preset.onApply()
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun TreasuryArtifactShard(
    index: Int,
    preset: TreasuryPreset,
    iconResId: Int,
    isUnlocked: Boolean,
    isActive: Boolean,
    description: String,
    amoled: Boolean,
    onToggle: () -> Unit,
) {
    val isCelestialSecretLocked =
        !isUnlocked && preset.unlockableId in setOf("special_navbar_aurora_celestial", "special_navbar_lattice_circuit")
    val effectiveTitle = if (isCelestialSecretLocked) "???" else preset.title

    val infiniteTransition = rememberInfiniteTransition(label = "artifact-${preset.unlockableId}")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400 + index * 180),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "artifact-glow-${preset.unlockableId}",
    )
    val shape = when (index % 3) {
        0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 14.dp)
        1 -> RoundedCornerShape(topStart = 10.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp)
        else -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 4.dp, bottomStart = 28.dp)
    }

    val colors = AuroraTheme.colors
    val isAmoled = colors.isDark && amoled
    val baseBg1 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF0D0D16)
    } else {
        Color.White
    }
    val baseBg2 = if (colors.isDark) {
        if (isAmoled) Color.Black else Color(0xFF08080E)
    } else {
        Color(0xFFF9F9FB)
    }

    val bgBrush = if (!colors.isDark && !colors.isEInk) {
        if (isActive) {
            Brush.verticalGradient(
                listOf(
                    preset.accentColor.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color.White.copy(alpha = 0.68f),
                    Color.White.copy(alpha = 0.60f),
                ),
            )
        }
    } else if (isActive) {
        Brush.horizontalGradient(
            listOf(
                preset.accentColor.copy(alpha = if (colors.isDark) 0.08f else 0.04f),
                baseBg1,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                baseBg1,
                baseBg2,
            ),
        )
    }
    val borderBrush = if (!colors.isDark && !colors.isEInk) {
        if (isActive) {
            Brush.verticalGradient(
                listOf(
                    preset.accentColor.copy(alpha = 0.60f),
                    preset.accentColor.copy(alpha = 0.30f),
                    preset.accentColor.copy(alpha = 0.15f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.12f),
                ),
            )
        }
    } else if (isActive) {
        Brush.linearGradient(
            listOf(
                preset.accentColor.copy(alpha = 0.40f),
                preset.accentColor.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
                if (colors.isDark) {
                    if (isAmoled) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)
                } else {
                    Color.Black.copy(alpha = 0.02f)
                },
            ),
        )
    }
    val borderWidth = if (isActive) 1.5.dp else 1.dp

    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val indentDp = if (screenWidth < 400) 8.dp else 18.dp

    val activeDesc = stringResource(AYMR.strings.treasury_toggle_active)
    val availableDesc = stringResource(AYMR.strings.treasury_toggle_available)
    val lockedDesc = stringResource(AYMR.strings.treasury_toggle_locked_with_desc, description)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (index % 2 == 0) 0.dp else indentDp,
                end = if (index % 2 == 0) indentDp else 0.dp,
            )
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "$effectiveTitle, " +
                    if (isActive) {
                        activeDesc
                    } else if (isUnlocked) {
                        availableDesc
                    } else {
                        lockedDesc
                    }
            }
            .graphicsLayer {
                alpha = if (isUnlocked) 1f else 0.48f
            }
            .springPress(enabled = isUnlocked, onClick = onToggle)
            .drawBehind {
                if (!colors.isDark && !colors.isEInk) {
                    val outline = shape.createOutline(size, layoutDirection, this)

                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()

                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    val center = Offset(size.width / 2f, size.height / 2f)

                    // 1. Neutral shadow
                    val neutralScaleX = (size.width - neutralInset * 2) / size.width
                    val neutralScaleY = (size.height - neutralInset * 2) / size.height
                    drawScopeScale(scaleX = neutralScaleX, scaleY = neutralScaleY, pivot = center) {
                        translate(left = 0f, top = neutralOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = Color.Black.copy(alpha = 0.035f),
                            )
                        }
                    }

                    // 2. Warm shadow (accent color)
                    val warmScaleX = (size.width - warmInset * 2) / size.width
                    val warmScaleY = (size.height - warmInset * 2) / size.height
                    drawScopeScale(scaleX = warmScaleX, scaleY = warmScaleY, pivot = center) {
                        translate(left = 0f, top = warmOffsetY) {
                            drawOutline(
                                outline = outline,
                                color = preset.accentColor.copy(alpha = 0.025f),
                            )
                        }
                    }
                }
            }
            .background(bgBrush, shape)
            .border(borderWidth, borderBrush, shape)
            .clickable(enabled = isUnlocked, onClick = onToggle)
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val glowAlpha = if (isActive) glow else 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(preset.accentColor.copy(alpha = 0.24f * glowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.18f),
                    radius = size.minDimension * 0.72f,
                ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.18f * glow),
                        radius = size.minDimension * 0.48f,
                    )
                    drawCircle(
                        color = preset.accentColor.copy(alpha = 0.44f),
                        radius = size.minDimension * 0.44f,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    tint = if (isUnlocked) Color.Unspecified else Color.Gray,
                )
                if (!isUnlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(AYMR.strings.treasury_cd_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = effectiveTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = settingsTitleColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        val checkScale by infiniteTransition.animateFloat(
                            initialValue = 0.85f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1200),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "check-scale",
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(AYMR.strings.treasury_cd_active),
                            tint = preset.accentColor,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                },
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                    color = if (isUnlocked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
