package com.waz.zclient.devices.domain

import com.waz.zclient.core.network.requestRemote
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsDataSource
import com.waz.zclient.devices.domain.model.Client

class GetAllClientsUseCase(private val clientsRepository: ClientsDataSource)
    : UseCase<List<Client>, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, List<Client>> = requestRemote {
        clientsRepository.allClients()
    }
}
