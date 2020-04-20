@file:Suppress("SpreadOperator")

package com.waz.zclient.core.extension

import android.graphics.drawable.Drawable
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.request.RequestOptions
import com.waz.zclient.core.images.ViewBackgroundTarget

fun AppCompatActivity.imageLoader(): RequestManager = Glide.with(this)

fun RequestBuilder<Drawable>.intoBackground(view: View) = into(ViewBackgroundTarget(view))

fun <T> RequestBuilder<T>.addTransformations(vararg transformations: BitmapTransformation) = this.apply {
    if (transformations.isNotEmpty()) {
        val requestOptions = RequestOptions().also {
            it.transform(*transformations)
        }
        apply(requestOptions)
    }
}
