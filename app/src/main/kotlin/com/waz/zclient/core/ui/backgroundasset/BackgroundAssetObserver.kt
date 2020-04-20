@file:Suppress("SpreadOperator")

package com.waz.zclient.core.ui.backgroundasset

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.waz.zclient.core.extension.addTransformations
import com.waz.zclient.core.extension.imageLoader
import com.waz.zclient.core.extension.intoBackground
import com.waz.zclient.core.images.transformations.AppBackgroundTransformations

interface BackgroundAssetObserver<T : LifecycleOwner> {
    fun loadBackground(lifecycleOwner: T, backgroundAssetOwner: BackgroundAssetOwner, view: View)
}

class ActivityBackgroundAssetObserver : BackgroundAssetObserver<AppCompatActivity> {

    override fun loadBackground(
        lifecycleOwner: AppCompatActivity,
        backgroundAssetOwner: BackgroundAssetOwner,
        view: View
    ) {
        backgroundAssetOwner.let {
            it.fetchBackgroundAsset()
            it.backgroundAsset.observe(lifecycleOwner, Observer {
                lifecycleOwner
                    .imageLoader()
                    .load(it)
                    .addTransformations(*AppBackgroundTransformations.transformations(lifecycleOwner))
                    .intoBackground(view)
            })
        }
    }
}
