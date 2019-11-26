package com.waz.zclient.devices.data

import androidx.lifecycle.LiveData
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.model.DeviceEntity

interface DevicesRepository {

    fun getCurrentDeviceDetails(clientId: String): LiveData<RequestResult<DeviceEntity>>
}
