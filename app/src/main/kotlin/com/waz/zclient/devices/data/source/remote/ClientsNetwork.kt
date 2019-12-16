package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.network.Network

class ClientsNetwork : Network() {

    fun getClientsApi(): ClientsNetworkService {
        return retrofit.create(ClientsNetworkService::class.java)
    }
}
