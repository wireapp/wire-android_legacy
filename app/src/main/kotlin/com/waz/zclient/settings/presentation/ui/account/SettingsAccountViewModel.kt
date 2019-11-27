package com.waz.zclient.settings.presentation.ui.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.waz.zclient.settings.presentation.mapper.UserItemMapper
import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import io.reactivex.observers.DisposableSingleObserver

class SettingsAccountViewModel(private val getUserProfileUseCase: GetUserProfileUseCase) : ViewModel() {

    private val userItemMapper = UserItemMapper()
    val profileUserData = MutableLiveData<UserItem>()

    fun getProfile() = getUserProfileUseCase.execute(SettingsAccountObserver())

    override fun onCleared() {
        getUserProfileUseCase.dispose()
        super.onCleared()
    }

    inner class SettingsAccountObserver : DisposableSingleObserver<User>() {
        override fun onSuccess(user: User) {
            profileUserData.postValue(userItemMapper.mapFromDomain(user))
        }

        override fun onError(e: Throwable) {
            e.printStackTrace()
        }
    }
}


