package com.waz.zclient.core.network.api.client

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.features.clients.ClientEntity

class ClientsService(private val apiService: ApiService,
                     private val clientsApi: ClientsApi) {

    suspend fun allClients(): Either<Failure, List<ClientEntity>> =
        apiService.request({clientsApi.allClients()}, emptyList())

    suspend fun clientById(clientId: String?): Either<Failure, ClientEntity> =
        apiService.request({clientsApi.clientById(clientId)}, ClientEntity.empty())
}
