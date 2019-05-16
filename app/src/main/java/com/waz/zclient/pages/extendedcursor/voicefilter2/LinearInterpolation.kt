package com.waz.zclient.pages.extendedcursor.voicefilter2

class LinearInterpolation(private val controlPoints: IntArray, private val size: Int) {
    private val stepSize = size.toFloat() / controlPoints.size
    fun interpolate(x: Int): Int {
        val index = x.toFloat() / stepSize
        println("Interpolation index $x $index")
        val controlPoint0 = controlPoints.getOrNull(Math.floor(index.toDouble()).toInt()) ?: 0
        val controlPoint1 = controlPoints.getOrNull(Math.ceil(index.toDouble()).toInt()) ?: 0

        return (controlPoint0 + ((controlPoint1 - controlPoint0) / stepSize) * (x % stepSize)).toInt()
    }
}
