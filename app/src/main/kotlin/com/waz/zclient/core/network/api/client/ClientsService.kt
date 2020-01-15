package com.waz.zclient.core.network.api.client

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.features.clients.ClientEntity

class ClientsService(override val networkHandler: NetworkHandler,
                     private val clientsApi: ClientsApi) : ApiService() {

    suspend fun allClients(): Either<Failure, List<ClientEntity>> =
        request({clientsApi.allClients()}, emptyList())

    suspend fun clientById(clientId: String?): Either<Failure, ClientEntity> =
        request({clientsApi.clientById(clientId)}, ClientEntity.empty())
}
