package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.BaseRemoteDataSource
import com.waz.zclient.core.data.source.remote.Network.Companion.API_TOKEN
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.model.ClientEntity

class ClientsRemoteDataSourceImpl : ClientsRemoteDataSource, BaseRemoteDataSource() {

    private val deviceNetwork = ClientsNetwork()

    override suspend fun getAllClients(): RequestResult<Array<ClientEntity>> = getResult {
        deviceNetwork.getClientsApi().getAllClientsAsync(API_TOKEN)
    }
}
