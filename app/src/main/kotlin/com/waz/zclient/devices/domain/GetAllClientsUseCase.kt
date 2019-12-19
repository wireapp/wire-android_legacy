package com.waz.zclient.devices.domain

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.domain.model.Client

class GetAllClientsUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<List<Client>, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, List<Client>> =
        clientsRepository.allClients()
}
