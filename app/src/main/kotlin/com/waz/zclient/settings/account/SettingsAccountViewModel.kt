package com.waz.zclient.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeEmailParams
import com.waz.zclient.user.domain.usecase.ChangeEmailUseCase
import com.waz.zclient.user.domain.usecase.ChangeNameParams
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.ChangePhoneParams
import com.waz.zclient.user.domain.usecase.ChangePhoneUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleParams
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class ProfileDetail(val value: String) {
    companion object {
        val EMPTY = ProfileDetail(String.empty())
    }
}

@ExperimentalCoroutinesApi
class SettingsAccountViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val changeNameUseCase: ChangeNameUseCase,
    private val changePhoneUseCase: ChangePhoneUseCase,
    private val changeEmailUseCase: ChangeEmailUseCase,
    private val changeHandleUseCase: ChangeHandleUseCase
) : ViewModel() {

    private val mutableProfileData = MutableLiveData<User>()
    private val mutableError = MutableLiveData<String>()

    val name: LiveData<String> = Transformations.map(mutableProfileData) {
        it.name
    }

    val handle: LiveData<String> = Transformations.map(mutableProfileData) {
        it.handle
    }

    val email: LiveData<ProfileDetail> = Transformations.map(mutableProfileData) {
        if (it.email.isNullOrEmpty()) ProfileDetail.EMPTY else ProfileDetail(it.email)
    }

    val phone: LiveData<ProfileDetail> = Transformations.map(mutableProfileData) {
        if (it.phone.isNullOrEmpty()) ProfileDetail.EMPTY else ProfileDetail(it.phone)
    }

    val error: LiveData<String> = mutableError

    fun loadProfileDetails() {
        getUserProfileUseCase(viewModelScope, Unit) {
            it.fold(::handleError, ::handleProfileSuccess)
        }
    }

    fun updateName(name: String) {
        changeNameUseCase(viewModelScope, ChangeNameParams(name)) {
            it.fold(::handleError) {}
        }
    }

    fun updatePhone(phoneNumber: String) {
        changePhoneUseCase(viewModelScope, ChangePhoneParams(phoneNumber)) {
            it.fold(::handleError) {}
        }
    }

    fun updateHandle(handle: String) {
        changeHandleUseCase(viewModelScope, ChangeHandleParams(handle)) {
            it.fold(::handleError) {}
        }
    }

    fun updateEmail(email: String) {
        changeEmailUseCase(viewModelScope, ChangeEmailParams(email)) {
            it.fold(::handleError) {}
        }
    }

    private fun handleProfileSuccess(user: User) {
        mutableProfileData.postValue(user)
    }

    //TODO valid error scenarios once the networking has been integrated
    private fun handleError(failure: Failure) {
        if (failure is HttpError) {
            mutableError.postValue("${failure.errorCode} + ${failure.errorMessage}")
        } else {
            mutableError.postValue("Misc error scenario")
        }
    }
}
