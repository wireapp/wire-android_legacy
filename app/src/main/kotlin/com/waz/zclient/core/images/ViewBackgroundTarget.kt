package com.waz.zclient.core.images

import android.graphics.drawable.Drawable
import android.view.View
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition

class ViewBackgroundTarget(view: View) : CustomViewTarget<View, Drawable>(view) {
    override fun onLoadFailed(errorDrawable: Drawable?) {}

    override fun onResourceCleared(placeholder: Drawable?) {}

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
        view.background = resource
    }
}
