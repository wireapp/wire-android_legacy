package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.network.Network

class ClientsNetwork : Network() {

    fun getClientsApi(): ClientsApi {
        return retrofit.create(ClientsApi::class.java)
    }
}
