package com.waz.zclient.settings.account.phonenumber.editphone

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.domain.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.CountryCodeAndPhoneNumberParams
import com.waz.zclient.user.domain.usecase.phonenumber.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.CountryCodeInvalid
import com.waz.zclient.user.domain.usecase.phonenumber.PhoneNumber
import com.waz.zclient.user.domain.usecase.phonenumber.PhoneNumberInvalid
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberParams
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberUseCase

data class CountryCodeErrorMessage(@StringRes val errorMessage: Int)
data class PhoneNumberErrorMessage(@StringRes val errorMessage: Int)

class EditPhoneNumberViewModel(
    private val validatePhoneNumberUseCase: ValidatePhoneNumberUseCase,
    private val changePhoneNumberNumberUseCase: ChangePhoneNumberUseCase,
    private val countryCodeAndPhoneNumberUseCase: CountryCodeAndPhoneNumberUseCase
) : ViewModel() {

    private var _countryCodeErrorLiveData = MutableLiveData<CountryCodeErrorMessage>()
    private var _phoneNumberErrorLiveData = MutableLiveData<PhoneNumberErrorMessage>()
    private var _phoneNumberLiveData = MutableLiveData<String>()
    private var _countryCodeLiveData = MutableLiveData<String>()

    val countryCodeErrorLiveData: LiveData<CountryCodeErrorMessage> = _countryCodeErrorLiveData
    val phoneNumberErrorLiveData: LiveData<PhoneNumberErrorMessage> = _phoneNumberErrorLiveData
    val phoneNumberLiveData: LiveData<String> = _phoneNumberLiveData
    val countryCodeLiveData: LiveData<String> = _countryCodeLiveData

    fun onNumberConfirmed(countryCode: String, phoneNumber: String) {
        validatePhoneNumberUseCase(viewModelScope, ValidatePhoneNumberParams(countryCode, phoneNumber)) {
            it.fold(::handleValidationError, ::handleValidationSuccess)
        }
    }

    fun loadPhoneNumberData(phoneNumber: String) {
        countryCodeAndPhoneNumberUseCase(viewModelScope, CountryCodeAndPhoneNumberParams(phoneNumber)) {
            it.fold(::handleValidationError, ::handleFormattingSuccess)
        }
    }

    private fun handleValidationSuccess(phoneNumber: String) {
        _phoneNumberLiveData.value = phoneNumber
    }

    private fun handleFormattingSuccess(phoneNumber: PhoneNumber) {
        _countryCodeLiveData.postValue(phoneNumber.countryCode)
        _phoneNumberLiveData.postValue(phoneNumber.number)
    }

    private fun handleValidationError(failure: Failure) {
        when (failure) {
            is CountryCodeInvalid -> _countryCodeErrorLiveData.postValue(
                CountryCodeErrorMessage(R.string.edit_phone_dialog_country_code_error)
            )
            is PhoneNumberInvalid -> _phoneNumberErrorLiveData.postValue(
                PhoneNumberErrorMessage(R.string.edit_phone_dialog_phone_number_error)
            )
        }
    }
}
