package com.waz.zclient.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeEmailParams
import com.waz.zclient.user.domain.usecase.ChangeEmailUseCase
import com.waz.zclient.user.domain.usecase.ChangeNameParams
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class ProfileDetail(val value: String) {
    companion object {
        val EMPTY = ProfileDetail(String.empty())
    }
}

data class DialogDetail(val number: String, val hasEmail: Boolean) {
    companion object {
        val EMPTY = DialogDetail(String.empty(), false)
    }
}

@ExperimentalCoroutinesApi
class SettingsAccountViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val changeNameUseCase: ChangeNameUseCase,
    private val changeEmailUseCase: ChangeEmailUseCase
) : ViewModel() {

    private val profileLiveData = MutableLiveData<User>()
    private val _errorLiveData = MutableLiveData<String>()
    private val _phoneDialogLiveData = MutableLiveData<DialogDetail>()

    val nameLiveData: LiveData<String> = Transformations.map(profileLiveData) {
        it.name
    }

    val handleLiveData: LiveData<String> = Transformations.map(profileLiveData) {
        it.handle
    }

    val emailLiveData: LiveData<ProfileDetail> = Transformations.map(profileLiveData) {
        if (it.email.isNullOrEmpty()) ProfileDetail.EMPTY else ProfileDetail(it.email)
    }

    val phoneNumberLiveData: LiveData<ProfileDetail> = Transformations.map(profileLiveData) {
        if (it.phone.isNullOrEmpty()) ProfileDetail.EMPTY else ProfileDetail(it.phone)
    }

    val errorLiveData: LiveData<String> = _errorLiveData
    val phoneDialogLiveData: LiveData<DialogDetail> = _phoneDialogLiveData

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

    fun updateEmail(email: String) {
        changeEmailUseCase(viewModelScope, ChangeEmailParams(email)) {
            it.fold(::handleError) {}
        }
    }

    private fun handleProfileSuccess(user: User) {
        profileLiveData.postValue(user)
    }

    //TODO valid error scenarios once the networking has been integrated
    private fun handleError(failure: Failure) {
        _errorLiveData.postValue("Failure: $failure")
    }

    fun onPhoneContainerClicked() {
        val hasEmail = emailLiveData.value != ProfileDetail.EMPTY
        val hasPhoneNumber = phoneNumberLiveData.value != ProfileDetail.EMPTY
        if (hasPhoneNumber) {
            _phoneDialogLiveData.value = phoneNumberLiveData.value?.value?.let { DialogDetail(it, hasEmail) }
                ?: DialogDetail.EMPTY
        } else {
            _phoneDialogLiveData.value = DialogDetail.EMPTY
        }
    }
}
