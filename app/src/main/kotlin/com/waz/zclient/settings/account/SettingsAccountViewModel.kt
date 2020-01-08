package com.waz.zclient.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.settings.account.model.UserProfileItem
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeNameParams
import com.waz.zclient.user.domain.usecase.ChangeNameUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import retrofit2.HttpException

class SettingsAccountViewModel
constructor(private val getUserProfileUseCase: GetUserProfileUseCase,
            private val changeNameUseCase: ChangeNameUseCase) : ViewModel() {

    private val mutableProfile = MutableLiveData<UserProfileItem>()
    private val mutableNameUpdated = MutableLiveData<Boolean>().apply { setValue(false) }
    private val mutableError = MutableLiveData<String>()

    val profile: LiveData<UserProfileItem>
        get() = mutableProfile

    val nameUpdated: LiveData<Boolean>
        get() = mutableNameUpdated

    val error: LiveData<String>
        get() = mutableError


    fun loadProfile() {
        getUserProfileUseCase(scope = viewModelScope, params = Unit,
            onSuccess = { user -> handleProfileSuccess(user) },
            onError = { handleError(it) }
        )
    }

    fun updateName(value: String) {
        changeNameUseCase(scope = viewModelScope, params = ChangeNameParams(value),
            onSuccess = { handleChangeNameSuccess() },
            onError = { handleError(it) }
        )
    }

    private fun handleProfileSuccess(user: User) {
        mutableProfile.postValue(UserProfileItem(user))
    }

    private fun handleError(throwable: Throwable) {
        when (throwable) {
            is HttpException -> {
                mutableError.postValue("${throwable.code()} : ${throwable.message()}")
            }
            else -> {
                mutableError.postValue("${throwable.message} : ${throwable.localizedMessage}")
            }
        }
    }

    private fun handleChangeNameSuccess() {
        mutableNameUpdated.postValue(true)
    }


}


