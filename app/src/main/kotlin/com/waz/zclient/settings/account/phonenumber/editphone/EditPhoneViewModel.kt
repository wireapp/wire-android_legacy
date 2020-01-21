package com.waz.zclient.settings.account.phonenumber.editphone

import androidx.lifecycle.ViewModel
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.permissions.result.PermissionSuccess

class EditPhoneViewModel : ViewModel() {

    fun onOkButtonClicked(countryCode: String, phoneNumber: String) {

    }

    fun onCancelButtonClicked() {
    }

    fun onReadPhonePermissionDenied(failure: Failure) {

    }

    fun onReadPhonePermissionGranted(success: PermissionSuccess) {

    }

}
