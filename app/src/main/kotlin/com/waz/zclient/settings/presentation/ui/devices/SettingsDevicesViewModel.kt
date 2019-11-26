package com.waz.zclient.settings.presentation.ui.devices

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.domain.GetCurrentDeviceUseCase
import com.waz.zclient.devices.domain.Params
import com.waz.zclient.devices.model.DeviceEntity
import com.waz.zclient.settings.user.usecase.GetUserProfileUseCase
import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.observers.DisposableSingleObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsDevicesViewModel(private val getUserProfileUseCase: GetUserProfileUseCase,
                               private val getCurrentDeviceUseCase: GetCurrentDeviceUseCase) : ViewModel() {

    private fun getProfile(disposableSingleObserver: DisposableSingleObserver<UserEntity>) = getUserProfileUseCase.execute(disposableSingleObserver)

    private val devicesMutableData = MutableLiveData<RequestResult<DeviceEntity>>()
    val devicesData: LiveData<RequestResult<DeviceEntity>>
        get() = devicesMutableData

    fun setup() {
        viewModelScope.launch {
            retrieveCurrentDevice()
        }
    }

    private suspend fun retrieveCurrentDevice(): Unit = withContext(Dispatchers.Default) {
        getProfile(object : DisposableSingleObserver<UserEntity>() {
            override fun onSuccess(t: UserEntity) {
                val params = Params(t.phone)
                devicesMutableData.postValue(getCurrentDeviceUseCase.execute(params).value)
            }

            override fun onError(e: Throwable) {
                devicesMutableData.postValue(RequestResult.error(e.localizedMessage))
            }
        })
    }
}
