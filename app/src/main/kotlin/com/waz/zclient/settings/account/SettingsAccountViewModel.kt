package com.waz.zclient.settings.account

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeNameParams
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase

data class ProfileDetail(val value: String) : ProfileDetailsState()
object ProfileDetailNull : ProfileDetailsState()

sealed class ProfileDetailsState

class SettingsAccountViewModel constructor(private val getUserProfileUseCase: GetUserProfileUseCase,
                                           private val changeNameUseCase: ChangeNameUseCase)
    : ViewModel() {

    private val mutableName = MutableLiveData<String>()
    private val mutableHandle = MutableLiveData<String>()
    private val mutableEmail = MutableLiveData<ProfileDetailsState>()
    private val mutablePhone = MutableLiveData<ProfileDetailsState>()
    private val mutableError = MutableLiveData<String>()

    val name: LiveData<String>
        get() = mutableName

    val handle: LiveData<String>
        get() = mutableHandle

    val email: LiveData<ProfileDetailsState>
        get() = mutableEmail

    val phone: LiveData<ProfileDetailsState>
        get() = mutablePhone

    val error: LiveData<String>
        get() = mutableError

    fun loadProfile() {
        getUserProfileUseCase(viewModelScope, Unit) {
            it.fold(::handleError, ::handleProfileSuccess)
        }
    }

    fun updateName(name: String) {
        changeNameUseCase(viewModelScope, ChangeNameParams(name)) {
            it.fold(::handleError) {}
        }
    }

    private fun handleProfileSuccess(user: User) {
        mutableName.postValue(user.name)
        mutableHandle.postValue(user.handle)
        mutableEmail.postValue(if (user.email.isNullOrEmpty()) ProfileDetailNull else ProfileDetail(user.email))
        mutablePhone.postValue(if (user.phone.isNullOrEmpty()) ProfileDetailNull else ProfileDetail(user.phone))
    }

    private fun handleError(failure: Failure) {
        when (failure) {
            is HttpError ->
                Log.e(javaClass.simpleName, "failed with errorCode: ${failure.errorCode} and errorMessage {${failure.errorMessage}")
            else ->
                Log.e(javaClass.simpleName, "Misc error scenario")
        }
    }
}


