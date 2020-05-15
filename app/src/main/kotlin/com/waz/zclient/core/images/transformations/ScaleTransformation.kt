package com.waz.zclient.core.images.transformations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import java.util.Objects

data class ScaleTransformation(
    private val scaleX: Float,
    private val scaleY: Float = scaleX
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.digest(toString().toByteArray(Charsets.UTF_8))
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val bitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val matrix = Matrix().apply {
            val dx = (toTransform.width - toTransform.width * scaleX) * SCALE_HALF
            val dy = (toTransform.height - toTransform.height * scaleY) * SCALE_HALF

            setScale(scaleX, scaleY)
            postTranslate(dx, dy)
        }

        with(Canvas(bitmap)) {
            drawBitmap(toTransform, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
            setBitmap(null)
        }

        return bitmap
    }

    override fun hashCode(): Int = Objects.hash(scaleX, scaleY)

    override fun equals(other: Any?): Boolean =
        other === this ||
            (other is ScaleTransformation &&
                other.scaleX == this.scaleX &&
                other.scaleY == this.scaleY)

    companion object {
        private const val SCALE_HALF = 0.5F
    }
}
