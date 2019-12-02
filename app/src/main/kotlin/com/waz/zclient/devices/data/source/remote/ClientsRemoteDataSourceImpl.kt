package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.SafeApiDataSource
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.devices.data.model.ClientEntity

class ClientsRemoteDataSourceImpl(private val clientsApi: ClientsApi)
    : ClientsRemoteDataSource, SafeApiDataSource() {

    override suspend fun getClientById(clientId: String): Resource<ClientEntity> = getRequestResult {
        clientsApi.getClientByIdAsync(clientId)
    }

    override suspend fun getAllClients(): Resource<Array<ClientEntity>> = getRequestResult {
        clientsApi.getAllClientsAsync()
    }
}
