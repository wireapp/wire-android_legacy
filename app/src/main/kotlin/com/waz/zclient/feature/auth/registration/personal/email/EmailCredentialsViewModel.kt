package com.waz.zclient.feature.auth.registration.personal.email

import androidx.lifecycle.ViewModel
import com.waz.zclient.core.extension.empty

class EmailCredentialsViewModel : ViewModel() {

    //TODO Using SavedStateHandle with Koin to save the data
    private val credentials = Credentials()

    fun email() = credentials.email
    fun activationCode() = credentials.activationCode
    fun name() = credentials.name

    fun saveEmail(email: String) {
        credentials.email = email
    }

    fun saveActivationCode(activationCode: String) {
        credentials.activationCode = activationCode
    }

    fun saveName(name: String) {
        credentials.name = name
    }
}

data class Credentials(
    var email: String = String.empty(),
    var activationCode: String = String.empty(),
    var name: String = String.empty()
)
