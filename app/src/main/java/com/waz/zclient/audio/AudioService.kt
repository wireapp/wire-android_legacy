package com.waz.zclient.audio

import android.content.Context
import android.media.*
import io.reactivex.Observable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import kotlin.concurrent.fixedRateTimer

interface AudioService {
    companion object {
        data class RecordingProgress(val maxAmplitude: Int)

        object Pcm {
            const val sampleRate = 44100
            const val inputChannel = AudioFormat.CHANNEL_IN_MONO
            const val outputChannel = AudioFormat.CHANNEL_OUT_MONO
            const val sampleFormat = AudioFormat.ENCODING_PCM_16BIT
            const val readBufferSize = 1 shl 13
            val recorderBufferSize =
                Math.max(1 shl 16, AudioRecord.getMinBufferSize(sampleRate, inputChannel, sampleFormat))

            fun durationInMillisFromByteCount(byteCount: Long): Long =
                durationFromInMillisFromSampleCount(byteCount / Short.SIZE_BYTES)
            fun durationFromInMillisFromSampleCount(sampleCount: Long): Long =
                sampleCount * 1000L / sampleRate
        }
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
                    audioEncoder: Int = MediaRecorder.AudioEncoder.AAC,
                    outputFormat: Int = MediaRecorder.OutputFormat.AAC_ADTS): Observable<RecordingProgress>

    fun recordPcmAudio(targetFile: File): Observable<RecordingProgress>

    fun preparePcmAudioTrack(targetFile: File): AudioTrack
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

    override fun recordPcmAudio(targetFile: File): Observable<AudioService.Companion.RecordingProgress> =
        Observable.create { emitter ->
            var fos: FileOutputStream? = null
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioService.Companion.Pcm.sampleRate,
                AudioService.Companion.Pcm.inputChannel,
                AudioService.Companion.Pcm.sampleFormat,
                AudioService.Companion.Pcm.recorderBufferSize
            )
            val readBuffer = ShortArray(AudioService.Companion.Pcm.readBufferSize)
            recorder.startRecording()

            Thread(Runnable {
                try {
                    fos = FileOutputStream(targetFile)
                    var shortsRead = recorder.read(readBuffer, 0, AudioService.Companion.Pcm.readBufferSize)
                    while (shortsRead > 0 && !emitter.isDisposed) {
                        val bytes = ByteBuffer.allocateDirect(shortsRead * Short.SIZE_BYTES).order(LITTLE_ENDIAN)
                        val shorts = bytes.asShortBuffer().put(readBuffer, 0, shortsRead)
                        shorts.flip()

                        var maxAmplitude = 0
                        while (shorts.hasRemaining()) {
                            val elem = shorts.get()
                            if (Math.abs(elem.toInt()) > Math.abs(maxAmplitude))
                                maxAmplitude = elem.toInt()
                        }

                        fos?.channel?.write(bytes)
                        emitter.onNext(AudioService.Companion.RecordingProgress(maxAmplitude))
                        shortsRead = recorder.read(readBuffer, 0, AudioService.Companion.Pcm.readBufferSize)
                    }
                } catch (ex: Exception) {
                    try {
                        recorder.release()
                        fos?.flush()
                        fos?.close()
                    } catch (ex: Exception) { }
                    emitter.onError(RuntimeException("Error while pcm audio recording.", ex))
                }
            }).start()

            emitter.setCancellable {
                try {
                    recorder.stop()
                    recorder.release()
                    fos?.flush()
                    fos?.close()
                } catch (ex: Exception) { }
            }
        }

    override fun preparePcmAudioTrack(targetFile: File): AudioTrack {
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            AudioService.Companion.Pcm.sampleRate,
            AudioService.Companion.Pcm.outputChannel,
            AudioService.Companion.Pcm.sampleFormat,
            AudioService.Companion.Pcm.readBufferSize,
            AudioTrack.MODE_STREAM)

        Thread(Runnable {
            val playerBuffer = ByteArray(AudioService.Companion.Pcm.readBufferSize)
            val fis = FileInputStream(targetFile)

            var readCount = fis.read(playerBuffer)
            while (readCount != -1) {
                track.write(playerBuffer, 0, readCount)
                readCount = fis.read(playerBuffer)
            }
        }).start()

        return track
    }

}
