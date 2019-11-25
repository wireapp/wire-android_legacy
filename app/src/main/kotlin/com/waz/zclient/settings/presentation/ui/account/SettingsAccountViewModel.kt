package com.waz.zclient.settings.presentation.ui.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.waz.zclient.settings.data.model.UserEntity
import com.waz.zclient.settings.domain.usecase.GetUserProfileUseCase
import io.reactivex.observers.DisposableSingleObserver

class SettingsAccountViewModel(private val getUserProfileUseCase : GetUserProfileUseCase) : ViewModel() {

    val profileUserData = MutableLiveData<UserEntity>()
    fun getProfile() =  getUserProfileUseCase.execute(SettingsAccountObserver())

    override fun onCleared() {
        getUserProfileUseCase.dispose()
        super.onCleared()
    }

    inner class SettingsAccountObserver: DisposableSingleObserver<UserEntity>() {
        override fun onSuccess(t: UserEntity) {
            profileUserData.postValue(t)
        }
        override fun onError(e: Throwable) {
            e.printStackTrace()
        }
    }
}


