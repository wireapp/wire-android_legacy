package com.waz.zclient.core.ui.backgroundasset

import androidx.lifecycle.LiveData

interface BackgroundAssetOwner {
    val backgroundAsset: LiveData<*>
    fun fetchBackgroundAsset()
}
