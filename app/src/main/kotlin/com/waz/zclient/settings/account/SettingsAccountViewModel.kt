package com.waz.zclient.settings.account

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.settings.account.model.UserProfileItem
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeNameParams
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase

class SettingsAccountViewModel
constructor(private val getUserProfileUseCase: GetUserProfileUseCase,
            private val changeNameUseCase: ChangeNameUseCase) : ViewModel() {

    private val mutableProfile = MutableLiveData<UserProfileItem>()
    private val mutableError = MutableLiveData<String>()

    val profile: LiveData<UserProfileItem>
        get() = mutableProfile

    val error: LiveData<String>
        get() = mutableError


    fun loadProfile() {
        getUserProfileUseCase(viewModelScope, Unit) { response ->
            response.fold(::handleProfileError, ::handleProfileSuccess)
        }
    }

    fun updateName(value: String) {
        changeNameUseCase(viewModelScope, ChangeNameParams(value)) { response ->
            response.fold(::handleChangeNameError, ::handleChangeNameSuccess)
        }
    }

    private fun handleProfileSuccess(user: User) {
        mutableProfile.postValue(UserProfileItem(user))
    }

    private fun handleProfileError(failure: Failure) {
        when (failure) {
            is HttpError -> {
                mutableError.postValue("${failure.errorCode} : ${failure.errorMessage}")
            }
            else ->
                Log.e(javaClass.simpleName, "Misc error scenario")
        }
    }

    private fun handleChangeNameSuccess(any: Any) {

    }

    private fun handleChangeNameError(failure: Failure) {
        when (failure) {
            is HttpError -> {
                mutableError.postValue("${failure.errorCode} : ${failure.errorMessage}")
            }
            else ->
                Log.e(javaClass.simpleName, "Misc error scenario")
        }
    }


}


