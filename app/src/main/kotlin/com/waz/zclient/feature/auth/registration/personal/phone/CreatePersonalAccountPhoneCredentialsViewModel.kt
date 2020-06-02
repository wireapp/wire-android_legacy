package com.waz.zclient.feature.auth.registration.personal.phone

import androidx.lifecycle.ViewModel
import com.waz.zclient.core.extension.empty

class CreatePersonalAccountPhoneCredentialsViewModel : ViewModel() {

    private val credentials = Credentials()

    fun phone() = credentials.phone
    fun activationCode() = credentials.activationCode

    fun savePhone(phone: String) {
        credentials.phone = phone
    }

    fun saveActivationCode(activationCode: String) {
        credentials.activationCode = activationCode
    }
}

data class Credentials(
    var phone: String = String.empty(),
    var activationCode: String = String.empty()
)
