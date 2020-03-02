package com.waz.zclient.features.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.config.AccountUrlConfig
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.User
import com.waz.zclient.user.usecase.email.ChangeEmailParams
import com.waz.zclient.user.usecase.email.ChangeEmailUseCase
import com.waz.zclient.user.usecase.name.ChangeNameParams
import com.waz.zclient.user.usecase.name.ChangeNameUseCase
import com.waz.zclient.user.usecase.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsAccountViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val changeNameUseCase: ChangeNameUseCase,
    private val changeEmailUseCase: ChangeEmailUseCase,
    private val accountUrlConfig: AccountUrlConfig
) : ViewModel() {

    private val profileLiveData = MutableLiveData<User>()
    private val _errorLiveData = MutableLiveData<String>()
    private val _resetPasswordUrlLiveData = MutableLiveData<String>()
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
    val resetPasswordUrlLiveData: LiveData<String> = _resetPasswordUrlLiveData

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

    fun onResetPasswordClicked() {
        _resetPasswordUrlLiveData.value = "${accountUrlConfig.url}$RESET_PASSWORD_URL_SUFFIX"
    }

    companion object {
        private const val RESET_PASSWORD_URL_SUFFIX = "/forgot/"
    }
}

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
