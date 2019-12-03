package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.requestResult
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.devices.data.model.ClientEntity

class ClientsRemoteDataSourceImpl(private val clientsApi: ClientsApi)
    : ClientsRemoteDataSource {

    override suspend fun clientById(clientId: String): Either<Failure, ClientEntity> = requestResult {
        clientsApi.clientByIdAsync(clientId)
    }

    override suspend fun allClients(): Either<Failure, Array<ClientEntity>> = requestResult {
        clientsApi.allClientsAsync()
    }
}
