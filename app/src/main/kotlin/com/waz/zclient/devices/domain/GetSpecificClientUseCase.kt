package com.waz.zclient.devices.domain

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.domain.model.Client

class GetSpecificClientUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<Client, GetSpecificClientParams>() {
    override suspend fun run(params: GetSpecificClientParams): Either<Failure, Client> =
        requestData {
            clientsRepository.clientById(params.clientId)
        }
}

data class GetSpecificClientParams(val clientId: String)
