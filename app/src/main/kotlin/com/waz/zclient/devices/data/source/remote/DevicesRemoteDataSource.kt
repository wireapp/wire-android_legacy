package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.model.DeviceEntity

interface DevicesRemoteDataSource {

    suspend fun getCurrentDeviceData(clientId: String): RequestResult<DeviceEntity>
}
