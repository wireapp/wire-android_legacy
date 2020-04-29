package com.waz.zclient.core.images.transformations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import java.util.Objects

data class DarkenTransformation(
    private val alphaValue: Int,
    private val saturation: Float = 1F
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.digest(toString().toByteArray(Charsets.UTF_8))
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val bitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val colorMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }

        val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        val darkenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = alphaValue
        }

        with(Canvas(bitmap)) {
            drawBitmap(toTransform, 0F, 0F, saturationPaint)
            drawPaint(darkenPaint)
            setBitmap(null)
        }

        return bitmap
    }

    override fun hashCode(): Int = Objects.hash(alphaValue, saturation)

    override fun equals(other: Any?): Boolean =
        other === this ||
            (other is DarkenTransformation &&
                other.alphaValue == this.alphaValue &&
                other.saturation == this.saturation)
}
