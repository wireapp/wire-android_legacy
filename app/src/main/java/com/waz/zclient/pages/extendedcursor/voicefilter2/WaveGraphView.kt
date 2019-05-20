package com.waz.zclient.pages.extendedcursor.voicefilter2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.waz.zclient.R

class WaveGraphView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val kDefaultFrequency = 1.5f
        private const val kDefaultAmplitude = 1.0f
        private const val kDefaultIdleAmplitude = 0.01f
        private const val kDefaultNumberOfWaves = 5
        private const val kDefaultPhaseShift = -0.25f
        private const val kDefaultDensity = 5.0f
    }

    private val path: Path
    private var waveColor: Int = 0
    private val frequency: Float
    private var amplitude: Float = 0.toFloat()
    private val idleAmplitude: Float
    private val phaseShift: Float
    private val density: Double
    private var currentMaxAmplitude: Float? = null

    private val paint: Paint
    private val numberOfWaves: Int
    private var phase: Float = 0.toFloat()


    fun setAccentColor(accentColor: Int) {
        waveColor = accentColor
    }

    init {
        this.waveColor = Color.WHITE
        this.frequency = kDefaultFrequency
        this.amplitude = kDefaultAmplitude
        this.idleAmplitude = kDefaultIdleAmplitude
        this.numberOfWaves = kDefaultNumberOfWaves
        this.phaseShift = -kDefaultPhaseShift
        this.density = kDefaultDensity.toDouble()

        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.strokeWidth = resources.getDimensionPixelSize(R.dimen.wire__divider__height).toFloat()
        paint.style = Paint.Style.STROKE
        path = Path()
    }

    fun setMaxAmplitude(normalizedAmplitude: Float) {
        this.currentMaxAmplitude = normalizedAmplitude
        invalidate()

        phase += phaseShift
        val newAmplitude = Math.max(normalizedAmplitude, idleAmplitude)
        amplitude = (amplitude * 2 + newAmplitude) / 3
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (currentMaxAmplitude == null) {
            return
        }

        // We draw multiple sinus waves, with equal phases but altered amplitudes, multiplied by a parable function.
        for (i in 0 until numberOfWaves) {
            path.reset()

            val halfHeight = canvas.height / 2.0f
            val width = canvas.width
            val mid = width / 2.0f

            val maxAmplitude = halfHeight - 4.0f // 4 corresponds to twice the stroke width

            // Progress is a value between 1.0 and -0.5, determined by the current wave idx, which is used to alter the wave's amplitude.
            val progress = 1.0f - i.toFloat() / numberOfWaves
            val normedAmplitude = (1.5f * progress - 0.5f) * amplitude

            path.moveTo(0f, halfHeight)
            var x = density
            while (x < width + this.density) {
                // We use a parable to scale the sinus wave, that has its peak in the middle of the view.
                val scaling = (-Math.pow(1.0f / mid * (x - mid), 2.0) + 1).toFloat()
                val y = (scaling.toDouble() * maxAmplitude.toDouble() * normedAmplitude.toDouble() * Math.sin(2.0 * Math.PI * (x / width) * this.frequency.toDouble() + this.phase) + halfHeight).toFloat()
                path.lineTo(x.toFloat(), y)
                x += this.density
            }

            val multiplier = Math.min(1.0f, progress / 3.0f * 2.0f + 1.0f / 3.0f)
            val alpha = Color.alpha(waveColor)
            val newAlpha = (multiplier * alpha).toInt()
            val color = Color.argb(newAlpha, Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor))
            paint.color = color
            canvas.drawPath(path, paint)
        }
    }
}
