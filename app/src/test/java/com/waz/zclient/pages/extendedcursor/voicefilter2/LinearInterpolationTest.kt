package com.waz.zclient.pages.extendedcursor.voicefilter2

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LinearInterpolationTest {

    @Test
    fun testInterpolationSize() {
        val basePoints = intArrayOf(-187, -130, 124, 156, 135, -1179, -218, -459, -436, -580, -193, -155)
        val interpolationSize = 56
        val interpolation = LinearInterpolation(basePoints, interpolationSize)

        val interpolatedValues = 0.rangeTo(56).map { interpolation.interpolate(it) }
        println(interpolatedValues)
    }

}
