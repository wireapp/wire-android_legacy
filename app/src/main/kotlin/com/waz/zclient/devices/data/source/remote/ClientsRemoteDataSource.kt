package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.network.requestApi

class ClientsRemoteDataSource(private val clientsApi: ClientsNetworkService) {

    suspend fun clientById(clientId: String?) = requestApi { clientsApi.clientById(clientId) }

    suspend fun allClients() = requestApi { clientsApi.allClients() }
}
