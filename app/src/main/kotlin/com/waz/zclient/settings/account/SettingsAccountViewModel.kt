package com.waz.zclient.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.settings.account.model.UserProfileItem
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.ChangePhoneUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase

class SettingsAccountViewModel constructor(private val getUserProfileUseCase: GetUserProfileUseCase,
                                           private val changeHandleUseCase: ChangeHandleUseCase,
                                           private val changePhoneUseCase: ChangePhoneUseCase) : ViewModel() {

    private val mutableLoading = MutableLiveData<Boolean>()
    private val mutableError = MutableLiveData<String>()
    private val mutableProfile = MutableLiveData<UserProfileItem>()

    val loading: LiveData<Boolean>
        get() = mutableLoading

    val error: LiveData<String>
        get() = mutableError

    val profile: LiveData<UserProfileItem>
        get() = mutableProfile

    fun loadData() {
        handleLoading(true)
        getUserProfileUseCase.invoke(viewModelScope, Unit) { response ->
            response.fold(::handleProfileError, ::handleProfileSuccess)
        }
    }

    private fun handleProfileError(failure: Failure) {
        handleLoading(false)
        handleFailure(failure.message)
    }

    private fun handleProfileSuccess(user: User) {
        handleLoading(false)
        mutableProfile.postValue(UserProfileItem(user))
    }

    private fun handleLoading(isLoading: Boolean) {
        mutableLoading.postValue(isLoading)
    }

    private fun handleFailure(message: String) {
        mutableError.value = message
    }
}


