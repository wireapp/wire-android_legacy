package com.waz.zclient.feature.auth.registration.personal.email

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.waz.zclient.core.extension.empty

class CreatePersonalAccountWithEmailSharedViewModel : ViewModel() {

    private val _credentialsLiveData = MutableLiveData<Credentials>().apply { setValue(Credentials()) }

    val credentialsLiveData = _credentialsLiveData

    val emailLiveData: LiveData<String> = Transformations.map(_credentialsLiveData) {
        it.email
    }

    fun saveEmail(email: String) {
        _credentialsLiveData.value?.email = email
    }

    fun saveActivationCode(activationCode: String) {
        _credentialsLiveData.value?.activationCode = activationCode
    }

    fun saveName(name: String) {
        _credentialsLiveData.value?.name = name
    }
}

data class Credentials(
    var email: String = String.empty(),
    var activationCode: String = String.empty(),
    var name: String = String.empty()
)
