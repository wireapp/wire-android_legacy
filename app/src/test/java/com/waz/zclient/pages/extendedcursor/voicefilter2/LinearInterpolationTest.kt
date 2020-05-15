package com.waz.zclient.pages.extendedcursor.voicefilter2

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert.*

@RunWith(JUnit4::class)
class LinearInterpolationTest {

    @Test
    fun testInterpolationSize() {

        // given
        val basePoints = floatArrayOf(
                -187.0f, -130.0f, 124.0f, 156.0f, 135.0f, -1179.0f,
                -218.0f, -459.0f, -436.0f, -580.0f, -193.0f, -155.0f
        )
        val interpolationSize = 56

        // when
        val interpolation = LinearInterpolation(basePoints, interpolationSize)
        val interpolatedValues = 0.rangeTo(interpolationSize).map { interpolation.interpolate(it) }

        // then
        assertTrue(interpolatedValues.max()!! <= basePoints.max()!!)
        assertTrue(interpolatedValues.min()!! >= basePoints.min()!!)
    }

}
