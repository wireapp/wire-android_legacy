package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.network.requestApi

class ClientsRemoteDataSource(private val clientsNetworkService: ClientsNetworkService) {

    suspend fun clientById(clientId: String?) = requestApi { clientsNetworkService.clientById(clientId) }

    suspend fun allClients() = requestApi { clientsNetworkService.allClients() }
}
