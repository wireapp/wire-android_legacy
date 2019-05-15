package com.waz.zclient.pages.extendedcursor.voicefilter2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.waz.zclient.R

class WaveBinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0):
    View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_NUM_OF_LEVELS = 56
    }

    private var duration: Long = 0
    private var currentHead: Long = 0

    private val activePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inactivePaint: Paint
    private var levels: IntArray? = null
    private val binWidth: Int
    private val binSpaceWidth: Int

    init {
        activePaint.color = Color.BLUE
        inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        inactivePaint.color = Color.WHITE
        binWidth = resources.getDimensionPixelSize(R.dimen.wave_graph_bin_width)
        binSpaceWidth = resources.getDimensionPixelSize(R.dimen.wave_graph_bin_space_width)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (levels == null) {
            return
        }

        val size = levels!!.size
        val totalBinWidth = size * binWidth + (size - 1) * binSpaceWidth

        val height = canvas.height
        val width = canvas.width

        var currentX = (width - totalBinWidth) / 2

        val breakPoint = (MAX_NUM_OF_LEVELS.toFloat() * currentHead.toFloat() * 1.0f / duration).toInt()

        for (i in 0 until breakPoint) {
            if (i > MAX_NUM_OF_LEVELS - 1) {
                return
            }
            var lh = levels!![i] * height
            if (lh < binWidth) {
                lh = binWidth
            }
            val top = (height - lh) / 2

            canvas.drawRect(
                currentX.toFloat(),
                top.toFloat(),
                (currentX + binWidth).toFloat(),
                (top + lh).toFloat(),
                activePaint)
            currentX += binWidth + binSpaceWidth
        }

        for (i in breakPoint until MAX_NUM_OF_LEVELS) {
            var lh = levels!![i] * height
            if (lh < binWidth) {
                lh = binWidth
            }
            val top = (height - lh) / 2


            canvas.drawRect(
                currentX.toFloat(),
                top.toFloat(),
                (currentX + binWidth).toFloat(),
                (top + lh).toFloat(),
                inactivePaint)
            currentX += binWidth + binSpaceWidth
        }
    }

    fun setAccentColor(accentColor: Int) {
        activePaint.color = accentColor
    }

    fun setAudioLevels(levels: IntArray) {
        this.levels = levels
    }

    fun setAudioPlayingProgress(current: Long, total: Long) {
        this.currentHead = current
        this.duration = total
        invalidate()
    }
}
