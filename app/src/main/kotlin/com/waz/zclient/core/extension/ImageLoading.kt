@file:Suppress("SpreadOperator")
package com.waz.zclient.core.extension

import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.request.RequestOptions
import com.waz.zclient.core.images.ViewBackgroundTarget

fun View.load(item: Any, transformations: Array<BitmapTransformation>) {
    Glide.with(context).load(item).addTransformations(*transformations).into(ViewBackgroundTarget(this))
}

fun ImageView.load(item: Any, transformations: Array<BitmapTransformation>) {
    Glide.with(context).load(item).addTransformations(*transformations).into(this)
}

fun <T> RequestBuilder<T>.addTransformations(
    vararg transformations: BitmapTransformation
) = this.apply {
    if (transformations.isNotEmpty()) {
        val requestOptions = RequestOptions().also {
            it.transform(*transformations)
        }
        apply(requestOptions)
    }
}
