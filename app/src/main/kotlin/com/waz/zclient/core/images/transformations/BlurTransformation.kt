package com.waz.zclient.core.images.transformations

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import java.util.Objects

class BlurTransformation(
    private val appContext: Context,
    private val blurRadius: Float,
    private val blurPasses: Int
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.digest(tag(blurRadius, blurPasses).toByteArray(Charsets.UTF_8))
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val renderScript = RenderScript.create(appContext.applicationContext) //just to be safe
        val blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript)).also {
            it.setRadius(blurRadius)
        }

        val copiedBmp = toTransform.copy(toTransform.config, true)

        val blurAlloc = Allocation.createFromBitmap(renderScript, copiedBmp)
        blur.setInput(blurAlloc)
        repeat(blurPasses) {
            blur.forEach(blurAlloc)
        }
        blurAlloc.copyTo(copiedBmp)
        blurAlloc.destroy()
        blur.destroy()

        return copiedBmp
    }

    override fun equals(other: Any?): Boolean =
        other === this ||
            (other is BlurTransformation &&
                other.blurPasses == this.blurPasses &&
                other.blurRadius == this.blurRadius)

    override fun hashCode(): Int = Objects.hash(blurPasses, blurRadius)

    companion object {
        private fun tag(blurRadius: Float, blurPasses: Int): String = "BlurTransformation($blurRadius,$blurPasses)"
    }
}
