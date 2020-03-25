package com.waz.zclient.user.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class GetUserProfilePictureDelegate(val getUserProfilePictureUseCase: GetUserProfilePictureUseCase) {
    private val _profilePicture = MutableLiveData<ProfilePictureAsset>()
    val profilePicture: LiveData<ProfilePictureAsset> = _profilePicture

    fun fetchProfilePicture(scope: CoroutineScope) {
        getUserProfilePictureUseCase(scope, Unit) {
            it.onSuccess { _profilePicture.value = it }
        }
    }
}
