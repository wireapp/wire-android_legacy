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
    private var levels: FloatArray? = null
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
                lh = binWidth.toFloat()
            }
            val top = (height - lh) / 2

            canvas.drawRect(
                currentX.toFloat(),
                top,
                (currentX + binWidth).toFloat(),
                (top + lh),
                activePaint)
            currentX += binWidth + binSpaceWidth
        }

        for (i in breakPoint until MAX_NUM_OF_LEVELS) {
            var lh = levels!![i] * height
            if (lh < binWidth) {
                lh = binWidth.toFloat()
            }
            val top = (height - lh) / 2


            canvas.drawRect(
                currentX.toFloat(),
                top,
                (currentX + binWidth).toFloat(),
                (top + lh),
                inactivePaint)
            currentX += binWidth + binSpaceWidth
        }
    }

    fun setAccentColor(accentColor: Int) {
        activePaint.color = accentColor
    }

    fun setAudioLevels(levels: IntArray) {
        when {
            levels.size <= 1 -> {
                this.levels = FloatArray(MAX_NUM_OF_LEVELS)
            }
            levels.size < MAX_NUM_OF_LEVELS -> {
                val interpolation = LinearInterpolation(levels, MAX_NUM_OF_LEVELS)
                this.levels = FloatArray(MAX_NUM_OF_LEVELS) { i ->
                    normalizeAudioLoudness(interpolation.interpolate(i))
                }
            }
            else -> {
                val dx = levels.size.toFloat() / MAX_NUM_OF_LEVELS
                this.levels = FloatArray(MAX_NUM_OF_LEVELS) { i ->
                    val level = (i * dx).toInt().rangeTo(((i + 1f) * dx).toInt()).map { levels[it] }.max()!!
                    normalizeAudioLoudness(level)
                }
            }
        }
    }

    private fun normalizeAudioLoudness(level: Int): Float {
        val n = Math.min(Math.max(Short.MIN_VALUE.toInt(), level), Short.MAX_VALUE.toInt())
        val doubleValue = if (n < 0) n.toDouble() / -32768 else n.toDouble() / 32767
        val dbfsSquare = 20 * Math.log10(Math.abs(doubleValue))

        return Math.pow(2.0, Math.min(dbfsSquare, 0.0) / 10.0).toFloat()
    }

    fun setAudioPlayingProgress(current: Long, total: Long) {
        this.currentHead = current
        this.duration = total
        invalidate()
    }
}
