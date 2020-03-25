package com.waz.zclient.core.ui.backgroundasset

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.user.profile.GetUserProfilePictureDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
open class BackgroundAssetViewModel(
    private val getUserProfilePictureDelegate: GetUserProfilePictureDelegate
) : ViewModel(),
    BackgroundAssetOwner {
    override val backgroundAsset: LiveData<*> = getUserProfilePictureDelegate.profilePicture

    override fun fetchBackgroundAsset() = getUserProfilePictureDelegate.fetchProfilePicture(viewModelScope)
}
