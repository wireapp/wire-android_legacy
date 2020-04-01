package com.waz.zclient.core.ui.backgroundasset

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.shared.user.profile.GetUserProfilePictureUseCase
import com.waz.zclient.shared.user.profile.ProfilePictureAsset
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class BackgroundAssetViewModel(private val getUserProfilePictureUseCase: GetUserProfilePictureUseCase) : ViewModel(),
    BackgroundAssetOwner {

    private val _backgroundAsset = MutableLiveData<ProfilePictureAsset>()
    override val backgroundAsset: LiveData<*> = _backgroundAsset

    override fun fetchBackgroundAsset() {
        getUserProfilePictureUseCase(viewModelScope, Unit) {
            it.onSuccess {
                _backgroundAsset.value = it
            }
        }
    }
}
