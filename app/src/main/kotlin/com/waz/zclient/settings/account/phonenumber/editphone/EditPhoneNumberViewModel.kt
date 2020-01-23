package com.waz.zclient.settings.account.phonenumber.editphone

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.permissions.result.PermissionSuccess
import com.waz.zclient.user.domain.usecase.phonenumber.ChangePhoneNumberUseCase
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberError
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberParams
import com.waz.zclient.user.domain.usecase.phonenumber.ValidatePhoneNumberUseCase

class EditPhoneNumberViewModel(private val validatePhoneNumberUseCase: ValidatePhoneNumberUseCase,
                               private val changePhoneNumberNumberUseCase: ChangePhoneNumberUseCase) :
    ViewModel() {

    private var _errorLiveData = MutableLiveData<Failure>()
    private var _phoneNumberLiveData = MutableLiveData<String>()

    val errorLiveData: LiveData<Failure> = _errorLiveData
    val phoneNumberLiveData: LiveData<String> = _phoneNumberLiveData

    fun onOkButtonClicked(countryCode: String, phoneNumber: String) {
        validatePhoneNumberUseCase(viewModelScope, ValidatePhoneNumberParams(countryCode, phoneNumber)) {
            it.fold(::handleFailure, ::validationSuccess)
        }
    }

    private fun validationSuccess(phoneNumber: String) {
        _phoneNumberLiveData.value = phoneNumber
    }

    private fun handleFailure(failure: Failure) {
        if (failure is ValidatePhoneNumberError) {
            _errorLiveData.value = failure
        }
    }

    fun onCancelButtonClicked() {

    }

    fun onReadPhonePermissionDenied(failure: Failure) {

    }

    fun onReadPhonePermissionGranted(success: PermissionSuccess) {

    }

}
