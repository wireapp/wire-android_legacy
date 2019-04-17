package com.waz.zclient.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import io.reactivex.Observable
import java.io.File
import kotlin.concurrent.fixedRateTimer

interface AudioService {
    companion object {
        data class RecordingProgress(val maxAmplitude: Int)
    }

    /**
     * Returns Observable which will emmit one element when audio focus is granted and
     * fail when audio focus will be lost.
     */
    fun withAudioFocus(streamType: Int = AudioManager.STREAM_MUSIC,
                       durationHint: Int = AudioManager.AUDIOFOCUS_GAIN): Observable<Unit>

    /**
     * Returns Observable which is responsible for audio recording. Audio recording starts when
     * somebody subscribes to it and stops on unsubscribe.
     */
    fun recordAudio(targetFile: File,
                    audioSource: Int = MediaRecorder.AudioSource.MIC,
                    audioEncoder: Int = MediaRecorder.AudioEncoder.HE_AAC,
                    outputFormat: Int = MediaRecorder.OutputFormat.MPEG_4): Observable<RecordingProgress>
}

class AudioServiceImpl(private val context: Context): AudioService {

    override fun withAudioFocus(streamType: Int, durationHint: Int): Observable<Unit> =
        Observable.create { emitter ->
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val listener = AudioManager.OnAudioFocusChangeListener {
                when (it) {
                    AudioManager.AUDIOFOCUS_LOSS or AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                        emitter.onError(RuntimeException("Audio focus lost. Current: $it."))
                    else -> {}
                }
            }
            emitter.setCancellable { manager.abandonAudioFocus(listener) }
            when (manager.requestAudioFocus(listener, streamType, durationHint)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED ->
                    emitter.onNext(Unit)
                AudioManager.AUDIOFOCUS_REQUEST_FAILED ->
                    emitter.onError(RuntimeException("Audio focus request failed."))
            }
        }

    override fun recordAudio(targetFile: File,
                             audioSource: Int,
                             audioEncoder: Int,
                             outputFormat: Int): Observable<AudioService.Companion.RecordingProgress> =
        Observable.create { emitter ->
            val recorder = MediaRecorder().apply {
                setAudioSource(audioSource)
                setOutputFormat(outputFormat)
                setAudioEncoder(audioEncoder)
                setOutputFile(targetFile.toString())
            }

            try { recorder.prepare() } catch (ex: Exception) {
                emitter.onError(RuntimeException("Can not prepare recorder.", ex))
            }
            recorder.start()
            val maxAmplitudeTask = fixedRateTimer(
                "extracting_audio_levels_$targetFile",
                false,
                0L,
                50
            ) {
                emitter.onNext(AudioService.Companion.RecordingProgress(recorder.maxAmplitude))
            }

            emitter.setCancellable {
                maxAmplitudeTask.cancel()
                recorder.release()
            }
        }

}
