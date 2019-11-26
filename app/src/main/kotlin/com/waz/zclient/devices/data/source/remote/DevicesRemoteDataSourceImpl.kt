package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.BaseRemoteDataSource

class DevicesRemoteDataSourceImpl : DevicesRemoteDataSource, BaseRemoteDataSource() {

    private val deviceNetwork = DevicesNetwork()

    override suspend fun getCurrentDeviceData(clientId: String) = getResult {
        deviceNetwork.getDevicesApi().getCurrentDeviceDetails(clientId)
    }
}
