package com.waz.zclient.audio

import android.media.MediaRecorder
import java.io.File
import java.util.*
import kotlin.concurrent.fixedRateTimer

class AudioRecorder() {

    private var recorder: MediaRecorder? = null
    private var maxAmplitudeTask: Timer? = null
    private val levels: LinkedList<Int> = LinkedList()

    fun start(targetFile: File,
              callback: AudioRecorderCallback,
              audioSource: Int = MediaRecorder.AudioSource.MIC,
              audioEncoder: Int = MediaRecorder.AudioEncoder.AMR_WB,
              outputFormat: Int = MediaRecorder.OutputFormat.MPEG_4) {

        recorder = MediaRecorder().apply {
            setAudioSource(audioSource)
            setAudioEncoder(audioEncoder)
            setOutputFormat(outputFormat)
            setOutputFile(targetFile.toString())

            prepare()
            start()
            maxAmplitudeTask = fixedRateTimer(
                "extracting_audio_levels_$targetFile",
                false,
                0L,
                250
            ) {
                val amplitude = maxAmplitude
                levels.add(amplitude)
                callback.newMaxAmplitude(amplitude)
            }
        }
    }

    fun stop(): Array<Int> = levels.toTypedArray()
}

interface AudioRecorderCallback {
    fun newMaxAmplitude(amplitude: Int)
}
