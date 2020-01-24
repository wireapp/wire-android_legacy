package com.waz.zclient.settings.account.phonenumber.editphone

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.permissions.result.PermissionSuccess
import com.waz.zclient.user.domain.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.CountryCodeInvalid
import com.waz.zclient.user.domain.usecase.phonenumber.PhoneNumberInvalid
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberParams
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberUseCase

data class CountryCodeErrorMessage(@StringRes val errorMessage: Int)
data class PhoneNumberErrorMessage(@StringRes val errorMessage: Int)

class EditPhoneNumberViewModel(private val validatePhoneNumberUseCase: ValidatePhoneNumberUseCase,
                               private val changePhoneNumberNumberUseCase: ChangePhoneNumberUseCase) :
    ViewModel() {

    private var _countryCodeErrorLiveData = MutableLiveData<CountryCodeErrorMessage>()
    private var _phoneNumberErrorLiveData = MutableLiveData<PhoneNumberErrorMessage>()
    private var _phoneNumberLiveData = MutableLiveData<String>()

    val countryCodeErrorLiveData: LiveData<CountryCodeErrorMessage> = _countryCodeErrorLiveData
    val phoneNumberErrorLiveData: LiveData<PhoneNumberErrorMessage> = _phoneNumberErrorLiveData
    val phoneNumberLiveData: LiveData<String> = _phoneNumberLiveData

    fun onOkButtonClicked(countryCode: String, phoneNumber: String) {
        validatePhoneNumberUseCase(viewModelScope, ValidatePhoneNumberParams(countryCode, phoneNumber)) {
            it.fold(::handleValidationError, ::handleValidationSuccess)
        }
    }

    private fun handleValidationSuccess(phoneNumber: String) {
        _phoneNumberLiveData.value = phoneNumber
    }

    private fun handleValidationError(failure: Failure) {
        when (failure) {
            is CountryCodeInvalid -> _countryCodeErrorLiveData.value =
                CountryCodeErrorMessage(R.string.edit_phone_dialog_country_code_error)
            is PhoneNumberInvalid -> _phoneNumberErrorLiveData.value =
                PhoneNumberErrorMessage(R.string.edit_phone_dialog_phone_number_error)
        }
    }

    fun onCancelButtonClicked() {

    }

    fun onReadPhonePermissionDenied(failure: Failure) {

    }

    fun onReadPhonePermissionGranted(success: PermissionSuccess) {

    }

}
