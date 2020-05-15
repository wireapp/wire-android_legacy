package com.waz.zclient.feature.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.config.AccountUrlConfig
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.settings.account.logout.AnotherAccountExists
import com.waz.zclient.feature.settings.account.logout.CouldNotReadRemainingAccounts
import com.waz.zclient.feature.settings.account.logout.LogoutStatus
import com.waz.zclient.feature.settings.account.logout.NoAccountsLeft
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.shared.accounts.usecase.GetActiveAccountUseCase
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.email.ChangeEmailParams
import com.waz.zclient.shared.user.email.ChangeEmailUseCase
import com.waz.zclient.shared.user.name.ChangeNameParams
import com.waz.zclient.shared.user.name.ChangeNameUseCase
import com.waz.zclient.shared.user.profile.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class SettingsAccountViewModel(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val changeNameUseCase: ChangeNameUseCase,
    private val changeEmailUseCase: ChangeEmailUseCase,
    private val getActiveAccountUseCase: GetActiveAccountUseCase,
    private val accountUrlConfig: AccountUrlConfig
) : ViewModel() {

    private val profileLiveData = MutableLiveData<User>()
    private val activeAccountLiveData = MutableLiveData<ActiveAccount>()

    private val _errorLiveData = MutableLiveData<String>()
    private val _resetPasswordUrlLiveData = MutableLiveData<String>()
    private val _phoneDialogLiveData = MutableLiveData<PhoneDialogDetail>()
    private val _deleteAccountDialogLiveData = MutableLiveData<DeleteAccountDialogDetail>()
    private val _logoutNavigationAction = MutableLiveData<String>()

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

    val isSsoAccountLiveData: LiveData<Boolean> = Transformations.map(activeAccountLiveData) {
        it.ssoId != null
    }

    val inATeamLiveData: LiveData<Boolean> = Transformations.map(activeAccountLiveData) {
        !it.teamId.isNullOrEmpty()
    }

    val errorLiveData: LiveData<String> = _errorLiveData
    val phoneDialogLiveData: LiveData<PhoneDialogDetail> = _phoneDialogLiveData
    val deleteAccountDialogLiveData: LiveData<DeleteAccountDialogDetail> = _deleteAccountDialogLiveData
    val resetPasswordUrlLiveData: LiveData<String> = _resetPasswordUrlLiveData
    val logoutNavigationAction: MutableLiveData<String> = _logoutNavigationAction

    fun loadProfileDetails() {
        getActiveAccountUseCase(viewModelScope, Unit) {
            it.fold(::handleError, ::handleActiveAccountSuccess)
        }

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

    private fun handleActiveAccountSuccess(activeAccount: ActiveAccount) {
        activeAccountLiveData.postValue(activeAccount)
    }

    //TODO valid error scenarios once the networking has been integrated
    private fun handleError(failure: Failure) {
        _errorLiveData.postValue("Failure: $failure")
    }

    fun onPhoneContainerClicked() {
        val hasEmail = emailLiveData.value != ProfileDetail.EMPTY
        val hasPhoneNumber = phoneNumberLiveData.value != ProfileDetail.EMPTY
        if (hasPhoneNumber) {
            _phoneDialogLiveData.value = phoneNumberLiveData.value?.value?.let { PhoneDialogDetail(it, hasEmail) }
                ?: PhoneDialogDetail.EMPTY
        } else {
            _phoneDialogLiveData.value = PhoneDialogDetail.EMPTY
        }
    }

    fun onResetPasswordClicked() {
        _resetPasswordUrlLiveData.value = "${accountUrlConfig.url}$RESET_PASSWORD_URL_SUFFIX"
    }

    fun onDeleteAccountButtonClicked() {
        val hasEmail = emailLiveData.value != ProfileDetail.EMPTY
        val hasPhoneNumber = phoneNumberLiveData.value != ProfileDetail.EMPTY
        when {
            hasEmail -> {
                _deleteAccountDialogLiveData.value = emailLiveData.value?.value?.let {
                    DeleteAccountDialogDetail(it, String.empty())
                } ?: DeleteAccountDialogDetail.EMPTY
            }
            hasPhoneNumber -> {
                _deleteAccountDialogLiveData.value = phoneNumberLiveData.value?.value?.let {
                    DeleteAccountDialogDetail(String.empty(), it)
                } ?: DeleteAccountDialogDetail.EMPTY
            }
        }
    }

    fun onUserLoggedOut(logoutStatus: LogoutStatus) = when (logoutStatus) {
        CouldNotReadRemainingAccounts, NoAccountsLeft -> handleNoActiveUserLeft()
        AnotherAccountExists -> handleCurrentUserChange()
    }

    private fun handleNoActiveUserLeft() {
        _logoutNavigationAction.value = ACTION_LOGOUT_NO_USER_LEFT
    }

    private fun handleCurrentUserChange() {
        _logoutNavigationAction.value = ACTION_LOGOUT_CURRENT_USER_CHANGED
    }

    fun onUserLogoutError(failure: Failure) {
        _errorLiveData.value = "Error logging out: $failure"
    }

    companion object {
        private const val RESET_PASSWORD_URL_SUFFIX = "/forgot/"

        private const val ACTION_LOGOUT_CURRENT_USER_CHANGED = "com.wire.ACTION_CURRENT_USER_CHANGED"
        private const val ACTION_LOGOUT_NO_USER_LEFT = "com.wire.ACTION_NO_USER_LEFT"
    }
}

data class ProfileDetail(val value: String) {
    companion object {
        val EMPTY = ProfileDetail(String.empty())
    }
}

data class DeleteAccountDialogDetail(val email: String, val number: String) {
    companion object {
        val EMPTY = DeleteAccountDialogDetail(String.empty(), String.empty())
    }
}

data class PhoneDialogDetail(val number: String, val hasEmail: Boolean) {
    companion object {
        val EMPTY = PhoneDialogDetail(String.empty(), false)
    }
}
