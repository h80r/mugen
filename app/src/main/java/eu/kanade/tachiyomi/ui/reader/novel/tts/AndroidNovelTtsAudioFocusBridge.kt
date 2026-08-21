package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat

class AndroidNovelTtsAudioFocusBridge(
    private val context: Context,
) : NovelTtsAudioFocusBridge {
    private val audioManager = runCatching {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }.getOrNull()
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    override fun requestAudioFocus(onFocusChange: (NovelTtsAudioFocusChange) -> Unit): Boolean {
        val audioManager = audioManager ?: return true
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            onFocusChange(focusChange.toNovelTtsAudioFocusChange())
        }
        // Long-form spoken playback behaves like media playback: request full focus
        // (not a transient duck) so music apps pause instead of playing underneath.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(listener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandonAudioFocus() {
        val audioManager = audioManager ?: return
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    override fun registerHeadsetDisconnectListener(onDisconnect: () -> Unit) {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    onDisconnect()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiver = receiver
    }

    override fun unregisterHeadsetDisconnectListener() {
        val receiver = noisyReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        noisyReceiver = null
    }

    private fun Int.toNovelTtsAudioFocusChange(): NovelTtsAudioFocusChange {
        return when (this) {
            AudioManager.AUDIOFOCUS_GAIN -> NovelTtsAudioFocusChange.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> NovelTtsAudioFocusChange.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> NovelTtsAudioFocusChange.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> NovelTtsAudioFocusChange.LOSS_TRANSIENT_CAN_DUCK
            else -> NovelTtsAudioFocusChange.LOSS_TRANSIENT
        }
    }
}
