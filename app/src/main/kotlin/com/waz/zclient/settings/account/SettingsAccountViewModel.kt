package com.waz.zclient.settings.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.settings.account.model.UserProfileItem
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.ChangePhoneUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import com.waz.zclient.core.extension.error
import com.waz.zclient.core.extension.success
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableCompletableObserver
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers

class SettingsAccountViewModel : ViewModel() {


    private val getUserProfileUseCase = GetUserProfileUseCase(Schedulers.io(), AndroidSchedulers.mainThread())
    private val changeHandleUseCase = ChangeHandleUseCase(Schedulers.io(), AndroidSchedulers.mainThread())
    private val changePhoneUseCase = ChangePhoneUseCase(Schedulers.io(), AndroidSchedulers.mainThread())

    val profileUserData = MutableLiveData<Resource<UserProfileItem>>()


    fun profile() = getUserProfileUseCase.execute(GetUserProfileObserver())
    fun changeHandle(value: String) = changeHandleUseCase.execute(UpdateHandleObserver(), value)
    fun changePhone(value: String) = changePhoneUseCase.execute(UpdatePhoneObserver(), value)

    override fun onCleared() {
        getUserProfileUseCase.dispose()
        changeHandleUseCase.dispose()
        changePhoneUseCase.dispose()
        super.onCleared()
    }

    inner class GetUserProfileObserver : DisposableSingleObserver<User>() {
        override fun onSuccess(user: User) {
            profileUserData.success(UserProfileItem(user))
        }

        override fun onError(error: Throwable) {
            profileUserData.error(error)
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


