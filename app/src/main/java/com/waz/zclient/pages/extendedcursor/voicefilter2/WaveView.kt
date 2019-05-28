package com.waz.zclient.pages.extendedcursor.voicefilter2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.waz.api.AudioOverview
import com.waz.zclient.R
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterController

class WaveView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr:Int = 0) :
    View(context, attrs, defStyleAttr), VoiceFilterController.PlaybackObserver {

    companion object {
        private const val MAX_NUM_OF_LEVELS = 56
    }

    private var duration:Long = 0
    private var currentHead:Long = 0

    private val activePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inactivePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var levels: FloatArray? = null
    private val binWidth: Int
    private val binSpaceWidth: Int

    fun setAccentColor(accentColor:Int) {
        activePaint.color = accentColor
    }

    init {
        activePaint.color = Color.BLUE
        inactivePaint.color = Color.WHITE
        binWidth = resources.getDimensionPixelSize(R.dimen.wave_graph_bin_width)
        binSpaceWidth = resources.getDimensionPixelSize(R.dimen.wave_graph_bin_space_width)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (levels == null) return

        val size = levels!!.size
        val totalBinWidth = size * binWidth + (size - 1) * binSpaceWidth

        val height = canvas.height
        val width = canvas.width

        var currentX = (width - totalBinWidth) / 2

        val breakPoint = (MAX_NUM_OF_LEVELS.toFloat() * currentHead.toFloat() * 1.0f / duration).toInt()

        for (i in 0 until breakPoint) {
            if (i > MAX_NUM_OF_LEVELS - 1) return

            var lh = levels!![i] * height
            if (lh < binWidth) {
                lh = binWidth.toFloat()
            }
            val top = (height - lh) / 2

            canvas.drawRect(currentX.toFloat(), top, (currentX + binWidth).toFloat(), top + lh, activePaint)
            currentX += binWidth + binSpaceWidth
        }

        for (i in breakPoint until MAX_NUM_OF_LEVELS) {
            var lh = levels!![i] * height
            if (lh < binWidth) {
                lh = binWidth.toFloat()
            }
            val top = (height - lh) / 2

            canvas.drawRect(currentX.toFloat(), top, (currentX + binWidth).toFloat(), top + lh, inactivePaint)
            currentX += binWidth + binSpaceWidth
        }
    }

    override fun onPlaybackStopped(seconds: Long) {

    }

    override fun onPlaybackStarted(overview: AudioOverview) {
        levels = overview.getLevels(MAX_NUM_OF_LEVELS)
    }

    override fun onPlaybackProceeded(current: Long, total: Long) {
        this.currentHead = current
        this.duration = total
        invalidate()
    }
}
