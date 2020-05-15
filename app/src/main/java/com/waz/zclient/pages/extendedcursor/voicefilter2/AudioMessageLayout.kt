package com.waz.zclient.pages.extendedcursor.voicefilter2

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.waz.api.AudioEffect
import com.waz.api.AudioOverview
import com.waz.api.RecordingControls
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.zclient.R
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterContent
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterController
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterToolbar
import com.waz.zclient.utils.ViewUtils
import org.threeten.bp.Instant

class AudioMessageLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0):
    FrameLayout(context, attrs, defStyleAttr),
    VoiceFilterController.RecordingObserver {

    private val voiceFilterController: VoiceFilterController

    private var voiceFilterContent: VoiceFilterContent? = null
    private var voiceFilterToolbar: VoiceFilterToolbar? = null
    private var callback: Callback? = null

    init {
        voiceFilterController = VoiceFilterController()
        voiceFilterController.addObserver(this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        voiceFilterToolbar = ViewUtils.getView(this, R.id.vft)
        voiceFilterContent = ViewUtils.getView(this, R.id.vfc)

        voiceFilterToolbar!!.setVoiceFilterController(voiceFilterController)
        voiceFilterContent!!.voiceFilterRecordingLayout.setController(voiceFilterController)
        voiceFilterContent!!.voiceFilterGridLayout.setController(voiceFilterController)
    }

    override fun onRecordingStarted(recording: RecordingControls, timestamp: Instant) {
        if (callback != null) {
            callback!!.onAudioMessageRecordingStarted()
        }
    }

    override fun onRecordingFinished(recording: GlobalRecordAndPlayService.Audio,
                                     fileSizeLimitReached: Boolean,
                                     overview: AudioOverview) {
        voiceFilterContent!!.showNext()
        voiceFilterToolbar!!.showNext()
    }

    override fun onRecordingCanceled() {
        if (callback != null) {
            callback!!.onCancel()
        }
    }

    override fun onReRecord() {
        voiceFilterContent!!.showNext()
        voiceFilterToolbar!!.showNext()
    }

    override fun sendRecording(audio: GlobalRecordAndPlayService.Audio, appliedAudioEffect: AudioEffect) {
        if (callback != null) {
            callback!!.sendRecording(audio, appliedAudioEffect)
        }
    }

    fun setAccentColor(accentColor: Int) {
        voiceFilterContent!!.setAccentColor(accentColor)
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun onClose() {
        voiceFilterController.quit()
    }

    interface Callback {
        fun onCancel()

        fun onAudioMessageRecordingStarted()

        fun sendRecording(audio: GlobalRecordAndPlayService.Audio, appliedAudioEffect: AudioEffect)
    }
}
