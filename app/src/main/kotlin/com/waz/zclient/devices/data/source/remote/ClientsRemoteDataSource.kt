package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.devices.data.source.remote.model.ClientApi

class ClientsRemoteDataSource(private val clientsNetworkService: ClientsNetworkService) {

    suspend fun clientById(clientId: String?): Either<Failure, ClientApi> =
        requestApi { clientsNetworkService.clientById(clientId) }

    suspend fun allClients(): Either<Failure, List<ClientApi>> =
        requestApi { clientsNetworkService.allClients() }
}
