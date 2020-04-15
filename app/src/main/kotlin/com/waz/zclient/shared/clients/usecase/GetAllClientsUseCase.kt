package com.waz.zclient.shared.clients.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.ClientsRepository

class GetAllClientsUseCase(private val clientsRepository: ClientsRepository) : UseCase<List<Client>, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, List<Client>> =
        clientsRepository.allClients()
}
