package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.core.data.source.remote.SafeApiDataSource
import com.waz.zclient.devices.data.model.ClientEntity

class ClientsRemoteDataSourceImpl(private var deviceNetwork: ClientsNetwork = ClientsNetwork())
    : ClientsRemoteDataSource, SafeApiDataSource() {

    override suspend fun getClientById(clientId: String): RequestResult<ClientEntity> = getRequestResult {
        deviceNetwork.getClientsApi().getClientByIdAsync(clientId)
    }

    override suspend fun getAllClients(): RequestResult<Array<ClientEntity>> = getRequestResult {
        deviceNetwork.getClientsApi().getAllClientsAsync()
    }
}
