package com.waz.zclient.settings.presentation.ui.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.waz.zclient.settings.presentation.mapper.UserItemMapper
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import com.waz.zclient.user.domain.usecase.UpdateHandleUseCase
import com.waz.zclient.user.domain.usecase.UpdateNameUseCase
import com.waz.zclient.user.domain.usecase.UpdatePhoneUseCase
import com.waz.zclient.utilities.extension.setError
import com.waz.zclient.utilities.extension.setSuccess
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableCompletableObserver
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers

class SettingsAccountViewModel : ViewModel() {


    private val getUserProfileUseCase = GetUserProfileUseCase(Schedulers.io(), AndroidSchedulers.mainThread())
    private val updateNameUseCase = UpdateNameUseCase(Schedulers.io(), AndroidSchedulers.mainThread())
    private val updateHandleUseCase = UpdateHandleUseCase(Schedulers.io(), AndroidSchedulers.mainThread())
    private val updatePhoneUseCase = UpdatePhoneUseCase(Schedulers.io(), AndroidSchedulers.mainThread())

    private val userItemMapper = UserItemMapper()
    val profileUserData = MutableLiveData<Resource<UserItem>>()


    fun getProfile() = getUserProfileUseCase.execute(GetUserProfileObserver())
    fun updateName(name: String) = updateNameUseCase.execute(UpdateNameObserver(), name)
    fun updateHandle(handle: String) = updateHandleUseCase.execute(UpdateHandleObserver(), handle)
    fun updatePhone(phone: String) = updatePhoneUseCase.execute(UpdatePhoneObserver(), phone)

    override fun onCleared() {
        getUserProfileUseCase.dispose()
        updateNameUseCase.dispose()
        updateHandleUseCase.dispose()
        updatePhoneUseCase.dispose()
        super.onCleared()
    }

    inner class GetUserProfileObserver : DisposableSingleObserver<User>() {
        override fun onSuccess(user: User) {
            profileUserData.setSuccess(userItemMapper.mapFromDomain(user))
        }

        override fun onError(error: Throwable) {
            profileUserData.setError(error)
        }
    }

    inner class UpdateNameObserver : DisposableCompletableObserver() {
        override fun onComplete() {

        }

        override fun onError(e: Throwable) {
            e.printStackTrace()
        }
    }

    inner class UpdateHandleObserver : DisposableCompletableObserver() {
        override fun onComplete() {

        }

        override fun onError(e: Throwable) {
            e.printStackTrace()
        }
    }

    inner class UpdatePhoneObserver : DisposableCompletableObserver() {
        override fun onComplete() {

        }

        override fun onError(e: Throwable) {
            e.printStackTrace()
        }
    }
}


