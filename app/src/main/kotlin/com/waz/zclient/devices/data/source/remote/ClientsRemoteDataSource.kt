package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class ClientsRemoteDataSource(
    override val networkHandler: NetworkHandler,
    private val clientsApi: ClientsApi
) : ApiService() {

    suspend fun clientById(clientId: String?) = request { clientsApi.clientById(clientId) }

    suspend fun allClients() = request { clientsApi.allClients() }
}
