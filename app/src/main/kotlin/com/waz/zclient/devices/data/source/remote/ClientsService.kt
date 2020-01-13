package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.devices.data.source.remote.model.ClientApi

class ClientsService(private val apiService: ApiService,
                     private val clientsNetworkService: ClientsNetworkService) {

    suspend fun clientById(clientId: String?): Either<Failure, ClientApi> = apiService.request(
        { clientsNetworkService.clientById(clientId) }, ClientApi.EMPTY
    )
}
