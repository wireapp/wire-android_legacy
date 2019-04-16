package com.waz.zclient.audio

import android.content.Context
import android.media.AudioManager

object AudioFocusHelper {

    interface Handler {
        fun abandonAudioFocus()
    }

    class AudioFocusHandler<T>(val manager: AudioManager,
                               val onGranted: (Handler) -> T,
                               val onLost: (T) -> Unit,
                               val onFailure: (Exception) -> Unit): AudioManager.OnAudioFocusChangeListener, Handler {

        private var onGrantedResult: T? = null

        fun start() {
            when (manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)) {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    onFailure(RuntimeException("Can not request audio focus."))
                    abandonAudioFocus()
                }
            }
        }

        override fun onAudioFocusChange(focusChange: Int) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN or AudioManager.AUDIOFOCUS_GAIN_TRANSIENT ->
                    onGrantedResult = onGranted(this)
                else -> {
                    val result = onGrantedResult
                    if (result != null) onLost(result)
                    else onFailure(RuntimeException("Audio focus was not granted."))
                    abandonAudioFocus()
                }
            }
        }

        override fun abandonAudioFocus() {
            manager.abandonAudioFocus(this)
        }
    }

    fun <T> withAudioFocus(context: Context,
                           onGranted: (Handler) -> T,
                           onLost: (T?) -> Unit,
                           onFailure: (Exception) -> Unit) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioFocusHandler(manager, onGranted, onLost, onFailure).start()
    }

}
