package com.waz.zclient.pages.extendedcursor.voicefilter2

class LinearInterpolation(private val controlPoints: FloatArray, private val size: Int) {
    private val stepSize = size.toFloat() / controlPoints.size
    fun interpolate(x: Int): Float {
        val index = x.toFloat() / stepSize
        val controlPoint0 = controlPoints.getOrNull(Math.floor(index.toDouble()).toInt()) ?: 0.0F
        val controlPoint1 = controlPoints.getOrNull(Math.ceil(index.toDouble()).toInt()) ?: 0.0F

        return controlPoint0 + ((controlPoint1 - controlPoint0) / stepSize) * (x % stepSize)
    }
}
