package com.waz.zclient.devices.domain

import androidx.lifecycle.LiveData
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.DevicesRepository
import com.waz.zclient.devices.model.DeviceEntity

class GetCurrentDeviceUseCase(private val devicesRepository: DevicesRepository)
    : UseCase<Params, DeviceEntity> {

    override fun execute(params: Params): LiveData<RequestResult<DeviceEntity>> =
        devicesRepository.getCurrentDeviceDetails(params.clientId)
}

data class Params(val clientId: String)
