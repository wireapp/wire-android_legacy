package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.devices.model.DeviceEntity
import retrofit2.Response
import retrofit2.http.GET

interface DevicesApi {

    @GET("/clients/{clientId}")
    fun getCurrentDeviceDetails(clientId: String): Response<DeviceEntity>
}
