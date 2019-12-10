package com.waz.zclient.devices.domain

import com.waz.zclient.core.network.requestRemote
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsDataSource
import com.waz.zclient.devices.domain.model.Client

class GetSpecificClientUseCase(private val clientsRepository: ClientsDataSource)
    : UseCase<Client, Params>() {
    override suspend fun run(params: Params): Either<Failure, Client> =
        requestRemote {
            clientsRepository.clientById(params.clientId)
        }
}

data class Params(val clientId: String?)
