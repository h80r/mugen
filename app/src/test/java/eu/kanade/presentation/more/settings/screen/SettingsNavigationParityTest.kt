package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsNavigationParityTest {

    @Test
    fun `main settings navigation contains the five domain groups`() {
        val keys = mainSettingsNavigationItems().map { it.key }

        keys shouldContainExactly listOf(
            "reading",
            "library_data",
            "appearance",
            "connections",
            "system",
        )
    }

    @Test
    fun `main settings navigation keys remain unique`() {
        val keys = mainSettingsNavigationItems().map { it.key }

        keys.distinct().size shouldBe keys.size
    }

    @Test
    fun `player settings navigation keys remain unique`() {
        val keys = playerSettingsNavigationItems().map { it.key }

        keys.distinct().size shouldBe keys.size
    }

    @Test
    fun `settings search route list includes every domain leaf`() {
        val routeClasses = settingsSearchRouteScreens(includePlayerSettings = false).map { it::class.simpleName }

        routeClasses shouldContainExactly listOf(
            "SettingsAppearanceScreen",
            "SettingsLibraryScreen",
            "SettingsReaderScreen",
            "SettingsDownloadScreen",
            "SettingsTrackingScreen",
            "SettingsBrowseScreen",
            "SettingsDataScreen",
            "SettingsSecurityScreen",
            "SettingsNovelReaderTabScreen",
            "SettingsNovelReaderTabScreen",
            "SettingsNovelReaderTabScreen",
            "SettingsNovelReaderTabScreen",
            "SettingsNovelReaderTabScreen",
            "SettingsAdvancedTabScreen",
            "SettingsAdvancedTabScreen",
            "SettingsAdvancedTabScreen",
        )
    }

    @Test
    fun `settings search route list with player appends player routes`() {
        val routeClasses = settingsSearchRouteScreens(includePlayerSettings = true).map { it::class.simpleName }

        routeClasses shouldHaveSize 26
        routeClasses.takeLast(10) shouldContainExactly listOf(
            "PlayerSettingsPlayerScreen",
            "PlayerSettingsLayoutMainScreen",
            "PlayerSettingsLayoutScreen",
            "PlayerSettingsLayoutScreen",
            "PlayerSettingsGesturesScreen",
            "PlayerSettingsDecoderScreen",
            "PlayerSettingsSubtitleScreen",
            "PlayerSettingsAudioScreen",
            "PlayerSettingsTorrentScreen",
            "PlayerSettingsAdvancedScreen",
        )
    }
}
