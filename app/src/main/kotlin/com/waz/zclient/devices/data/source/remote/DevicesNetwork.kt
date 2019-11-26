package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.Network

class DevicesNetwork : Network() {

    fun getDevicesApi(): DevicesApi {
        return retrofit.create(DevicesApi::class.java)
    }
}
