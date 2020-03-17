package com.waz.zclient.clients.usecase

import com.waz.zclient.clients.Client
import com.waz.zclient.clients.ClientsRepository
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class GetClientUseCase(private val clientsRepository: ClientsRepository) : UseCase<Client, GetSpecificClientParams>() {

    override suspend fun run(params: GetSpecificClientParams): Either<Failure, Client> =
        clientsRepository.clientById(params.clientId)
}

data class GetSpecificClientParams(val clientId: String)