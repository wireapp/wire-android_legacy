package com.waz.zclient.features.settings.account.editphonenumber

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.usecase.phonenumber.ChangePhoneNumberParams
import com.waz.zclient.user.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.usecase.phonenumber.Country
import com.waz.zclient.user.usecase.phonenumber.CountryCodeAndPhoneNumberParams
import com.waz.zclient.user.usecase.phonenumber.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.user.usecase.phonenumber.CountryCodeInvalid
import com.waz.zclient.user.usecase.phonenumber.DeletePhoneNumberUseCase
import com.waz.zclient.user.usecase.phonenumber.PhoneNumber
import com.waz.zclient.user.usecase.phonenumber.PhoneNumberInvalid
import com.waz.zclient.user.usecase.phonenumber.ValidatePhoneNumberParams
import com.waz.zclient.user.usecase.phonenumber.ValidatePhoneNumberUseCase
import kotlinx.coroutines.Dispatchers

class SettingsAccountPhoneNumberViewModel(
    private val validatePhoneNumberUseCase: ValidatePhoneNumberUseCase,
    private val changePhoneNumberNumberUseCase: ChangePhoneNumberUseCase,
    private val countryCodeAndPhoneNumberUseCase: CountryCodeAndPhoneNumberUseCase,
    private val deletePhoneNumberUseCase: DeletePhoneNumberUseCase
) : ViewModel() {

    private val _countryCodeErrorLiveData = MutableLiveData<PhoneNumberErrorMessage>()
    private val _phoneNumberErrorLiveData = MutableLiveData<PhoneNumberErrorMessage>()
    private val _deleteNumberLiveData = MutableLiveData<String>()
    private val _confirmationLiveData = MutableLiveData<String>()
    private val _confirmedLiveData = MutableLiveData<String>()
    private val _phoneNumberDetailsLiveData = MutableLiveData<PhoneNumber>()

    val countryCodeErrorLiveData: LiveData<PhoneNumberErrorMessage> = _countryCodeErrorLiveData
    val phoneNumberErrorLiveData: LiveData<PhoneNumberErrorMessage> = _phoneNumberErrorLiveData
    val phoneNumberDetailsLiveData: LiveData<PhoneNumber> = _phoneNumberDetailsLiveData
    val deleteNumberLiveData: LiveData<String> = _deleteNumberLiveData
    val confirmationLiveData: LiveData<String> = _confirmationLiveData
    val confirmedLiveData: LiveData<String> = _confirmedLiveData

    fun afterNumberEntered(countryCode: String, phoneNumber: String) {
        validatePhoneNumberUseCase(
            viewModelScope,
            ValidatePhoneNumberParams(countryCode, phoneNumber),
            Dispatchers.Default
        ) {
            it.fold(::handleValidationError, ::handleConfirmationSuccess)
        }
    }

    fun loadPhoneNumberData(phoneNumber: String, deviceLanguage: String) {
        countryCodeAndPhoneNumberUseCase(
            viewModelScope,
            CountryCodeAndPhoneNumberParams(phoneNumber, deviceLanguage),
            Dispatchers.Default
        ) {
            it.fold(::handleValidationError, ::handleFormattingSuccess)
        }
    }

    fun onDeleteNumberButtonClicked(countryCode: String, phoneNumber: String) {
        validatePhoneNumberUseCase(
            viewModelScope,
            ValidatePhoneNumberParams(countryCode, phoneNumber),
            Dispatchers.Default
        ) {
            it.fold(::handleValidationError, ::handleDeletionSuccess)
        }
    }

    private fun handleDeletionSuccess(phoneNumber: String) {
        _deleteNumberLiveData.value = phoneNumber
    }

    private fun handleConfirmationSuccess(phoneNumber: String) {
        _confirmationLiveData.value = phoneNumber
    }

    private fun handleFormattingSuccess(phoneNumber: PhoneNumber) {
        _phoneNumberDetailsLiveData.value = phoneNumber
    }

    private fun handleValidationError(failure: Failure) {
        when (failure) {
            is CountryCodeInvalid -> _countryCodeErrorLiveData.value =
                PhoneNumberErrorMessage(R.string.edit_phone_dialog_country_code_error)
            is PhoneNumberInvalid -> _phoneNumberErrorLiveData.value =
                PhoneNumberErrorMessage(R.string.edit_phone_dialog_phone_number_error)
        }
    }

    fun onDeleteNumberButtonConfirmed() {
        deletePhoneNumberUseCase(viewModelScope, Unit) {
            it.fold({ handleDeletionFailure() }) {}
        }
    }

    private fun handleDeletionFailure() {
        _phoneNumberErrorLiveData.value = PhoneNumberErrorMessage(
            R.string.pref__account_action__dialog__delete_phone__error
        )
    }

    fun onPhoneNumberConfirmed(phoneNumber: String) {
        changePhoneNumberNumberUseCase(viewModelScope, ChangePhoneNumberParams(phoneNumber)) {
            it.fold({}, { onUpdateSuccess(phoneNumber) })
        }
    }

    private fun onUpdateSuccess(phoneNumber: String) {
        _confirmedLiveData.postValue(phoneNumber)
    }

    fun onCountryCodeUpdated(countryCode: Country) {
        _phoneNumberDetailsLiveData.value = PhoneNumber(
            countryCode.countryCode,
            String.empty(),
            countryCode.countryDisplayName
        )
    }
}

data class PhoneNumberErrorMessage(@StringRes val errorMessage: Int)
