package com.waz.zclient.features.settings.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.user.profile.GetUserProfilePictureUseCase
import com.waz.zclient.user.profile.ProfilePictureAsset
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsMainViewModel(private val getUserProfilePictureUseCase: GetUserProfilePictureUseCase) : ViewModel() {

    private val _backgroundAsset = MutableLiveData<ProfilePictureAsset>()
    var backgroundAsset : LiveData<ProfilePictureAsset> = _backgroundAsset

    fun fetchBackgroundImage() {
        getUserProfilePictureUseCase(viewModelScope, Unit) {
            it.onSuccess { _backgroundAsset.value = it }
        }
    }
}
