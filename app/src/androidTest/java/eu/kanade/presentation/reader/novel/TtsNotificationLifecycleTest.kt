package eu.kanade.presentation.reader.novel

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsNotificationLifecycleTest {

    @Test
    fun notificationDoesNotAppearWhenOpeningChaptersWithTtsDisabled() {
        // TODO: Implement test
    }

    @Test
    fun notificationDoesNotAppearWhenTtsIsEnabledButNotActivelyPlaying() {
        // TODO: Implement test
    }

    @Test
    fun notificationAppearsWhenTtsIsEnabledAndPlaying() {
        // TODO: Implement test
    }

    @Test
    fun notificationRemainsWhenTtsIsDisabledWhilePaused() {
        // TODO: Implement test
    }

    @Test
    fun notificationDisappearsWhenExplicitlyStoppedWhilePaused() {
        // TODO: Implement test
    }

    @Test
    fun notificationDoesNotReappearWhenSwipedAwayAndTtsDisabled() {
        // TODO: Implement test
    }
}
