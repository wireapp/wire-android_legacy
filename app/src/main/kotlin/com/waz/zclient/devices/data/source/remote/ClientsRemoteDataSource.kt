package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.storage.db.clients.model.ClientEntity

class ClientsRemoteDataSource(private val clientsApi: ClientsApi) {

    suspend fun clientById(clientId: String?): Either<Failure, ClientEntity> = requestApi {
        clientsApi.clientById(clientId)
    }

    suspend fun allClients(): Either<Failure, Array<ClientEntity>> = requestApi {
        clientsApi.allClients()
    }
}
