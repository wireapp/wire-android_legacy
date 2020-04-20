package com.waz.zclient.core.images.transformations

import android.content.Context
import com.bumptech.glide.load.resource.bitmap.CenterCrop

object AppBackgroundTransformations {
    private const val SCALE = 1.4F
    private const val DARKEN_ALPHA = 148
    private const val DARKEN_SATURATION = 2F
    private const val BLUR_RADIUS = 25F
    private const val BLUR_PASSES = 12

    @JvmStatic
    fun transformations(context: Context) = arrayOf(
        CenterCrop(),
        ScaleTransformation(SCALE),
        BlurTransformation(context, blurRadius = BLUR_RADIUS, blurPasses = BLUR_PASSES),
        DarkenTransformation(alphaValue = DARKEN_ALPHA, saturation = DARKEN_SATURATION)
    )
}
