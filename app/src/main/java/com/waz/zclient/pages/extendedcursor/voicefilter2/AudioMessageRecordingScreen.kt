package com.waz.zclient.pages.extendedcursor.voicefilter2

import android.content.Context
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.widget.ViewAnimator
import com.waz.api.AudioEffect
import com.waz.zclient.R
import com.waz.zclient.audio.AudioService
import com.waz.zclient.audio.AudioServiceImpl
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.audio_message_recording_screen.view.*
import java.io.File

class AudioMessageRecordingScreen @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ViewAnimator(context, attrs), View.OnClickListener {

    companion object {
        var GLYPH_PLACEHOLDER = "_GLYPH_"

        enum class CenterButton {
            RECORD_START, RECORD_STOP, CONFIRM
        }

        interface Listener {
            fun onCenterButtonPressed(button: CenterButton)
            fun onLeftButtonPressed()
            fun onRightButtonPressed()
        }
    }


    private val audioService: AudioService = AudioServiceImpl(context)
    private val recordFile: File = File(context.cacheDir, "record_temp.pcm")
    private val recordWithEffectFile: File = File(context.cacheDir, "record_with_effect_temp.pcm")
    private val recordLevels: MutableList<Int> = mutableListOf()

    private lateinit var currentCenterButton: CenterButton
    private var listener: Listener? = null

    private var recordingDisposable: Disposable? = null

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.audio_message_recording_screen, this, true)


        center_button.setOnClickListener(this)
        left_button.setOnClickListener(this)
        right_button.setOnClickListener(this)

        voice_filter_none.setOnClickListener(this)
        voice_filter_balloon.setOnClickListener(this)
        voice_filter_jelly_fish.setOnClickListener(this)
        voice_filter_rabbit.setOnClickListener(this)
        voice_filter_church.setOnClickListener(this)
        voice_filter_alien.setOnClickListener(this)
        voice_filter_robot.setOnClickListener(this)
        voice_filter_rollercoaster.setOnClickListener(this)

        val original = resources.getString(R.string.audio_message__recording__tap_to_record)

        val indexWrap = original.indexOf('\n')
        val indexGlyph = original.indexOf(GLYPH_PLACEHOLDER)
        val indexGlyphEnd = indexGlyph + GLYPH_PLACEHOLDER.length

        ttv__voice_filter__tap_to_record_1st_line.text = original.substring(0, indexWrap)
        ttv__voice_filter__tap_to_record_2nd_line_begin.text = original.substring(indexWrap + 1, indexGlyph)
        ttv__voice_filter__tap_to_record_2nd_line_end.text = original.substring(indexGlyphEnd)

        showAudioRecordingHint()
    }


    private fun showAudioRecordingHint() {
        audio_recording_container.visibility = View.VISIBLE
        audio_recording_hint_container.visibility = View.VISIBLE
        wave_graph_view.visibility = View.GONE
        audio_filters_container.visibility = View.GONE

        setCenterButton(Companion.CenterButton.RECORD_START)
        left_button.visibility = View.GONE
        right_button.visibility = View.GONE
    }

    private fun showAudioRecordingInProgress() {
        wave_graph_view.visibility = View.VISIBLE
        audio_recording_hint_container.visibility = View.GONE
        audio_filters_container.visibility = View.GONE

        setCenterButton(Companion.CenterButton.RECORD_STOP)
        left_button.visibility = View.GONE
        right_button.visibility = View.GONE
    }

    private fun showAudioFilters() {
        audio_recording_container.visibility = View.GONE
        audio_filters_container.visibility = View.VISIBLE

        setCenterButton(Companion.CenterButton.CONFIRM)
        left_button.visibility = View.VISIBLE
        right_button.visibility = View.VISIBLE
    }

    override fun setInAnimation(inAnimation: Animation) {
        inAnimation.startOffset = resources.getInteger(R.integer.camera__control__ainmation__in_delay).toLong()
        inAnimation.interpolator = Expo.EaseOut()
        inAnimation.duration = context.resources.getInteger(R.integer.calling_animation_duration_medium).toLong()
        super.setInAnimation(inAnimation)
    }

    override fun setOutAnimation(outAnimation: Animation) {
        outAnimation.interpolator = Expo.EaseIn()
        outAnimation.duration = context.resources.getInteger(R.integer.calling_animation_duration_medium).toLong()
        super.setOutAnimation(outAnimation)
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun setAccentColor(color: Int) {
        wave_graph_view.setAccentColor(color)
    }

    private fun setCenterButton(button: CenterButton) {
        when (button) {
            Companion.CenterButton.RECORD_START -> center_button.setText(R.string.glyph__record)
            Companion.CenterButton.RECORD_STOP -> center_button.setText(R.string.glyph__stop)
            Companion.CenterButton.CONFIRM -> center_button.setText(R.string.glyph__check)
        }
        currentCenterButton = button
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.left_button ->
                showAudioRecordingHint()
            R.id.right_button ->
                listener?.onRightButtonPressed()
            R.id.center_button -> when (currentCenterButton) {
                Companion.CenterButton.RECORD_START -> startRecording()
                Companion.CenterButton.RECORD_STOP -> stopRecording()
                Companion.CenterButton.CONFIRM -> sendRecording()
            }

            R.id.voice_filter_none ->
                applyAudioEffectAndPlay(AudioEffect.NONE)
            R.id.voice_filter_balloon ->
                applyAudioEffectAndPlay(AudioEffect.PITCH_UP_INSANE)
            R.id.voice_filter_jelly_fish ->
                applyAudioEffectAndPlay(AudioEffect.PITCH_DOWN_INSANE)
            R.id.voice_filter_rabbit ->
                applyAudioEffectAndPlay(AudioEffect.PACE_UP_MED)
            R.id.voice_filter_church ->
                applyAudioEffectAndPlay(AudioEffect.REVERB_MAX)
            R.id.voice_filter_alien ->
                applyAudioEffectAndPlay(AudioEffect.CHORUS_MAX)
            R.id.voice_filter_robot ->
                applyAudioEffectAndPlay(AudioEffect.VOCODER_MED)
            R.id.voice_filter_rollercoaster ->
                applyAudioEffectAndPlay(AudioEffect.PITCH_UP_DOWN_MAX)

            else -> {}
        }
    }

    fun startRecording() {
        showAudioRecordingInProgress()
        recordFile.delete()
        recordLevels.clear()
        wave_graph_view.keepScreenOn = true

        recordingDisposable = audioService.withAudioFocus()
            .flatMap { audioService.recordPcmAudio(recordFile) }
            .subscribe({ progress ->
                recordLevels.add(progress.maxAmplitude)
                wave_graph_view.setMaxAmplitude(progress.maxAmplitude)
                println("Max amplitude ${progress.maxAmplitude}")
            }, { error ->
                println("Error while recording $error")
                wave_graph_view.keepScreenOn = false
            })
    }

    fun stopRecording() {
        wave_graph_view.keepScreenOn = false
        showAudioFilters()
        recordingDisposable?.dispose()
    }

    fun sendRecording() {

    }

    fun applyAudioEffectAndPlay(effect: AudioEffect) {
        val avsEffects = com.waz.audioeffect.AudioEffect()
        try {
            val res = avsEffects.applyEffectPCM(
                recordFile.absolutePath,
                recordWithEffectFile.absolutePath,
                AudioService.Companion.Pcm.sampleRate,
                effect.avsOrdinal,
                true)

            if (res < 0) throw RuntimeException("applyEffectWav returned error code: $res")
            audioService.playPcmAudio(recordWithEffectFile)
        } catch (ex: Exception) {
            println("Exception while applying audio effect. $ex")
        } finally {
            avsEffects.destroy()
        }
    }

}
