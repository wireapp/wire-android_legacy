package com.waz.zclient.devices.domain

import com.fernandocejas.sample.core.functional.Either
import com.fernandocejas.sample.core.functional.Failure
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.model.ClientEntity

class GetAllClientsUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<RequestResult<Array<ClientEntity>>, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, RequestResult<Array<ClientEntity>>> {
        return try {
            val clients = clientsRepository.getAllClients()
            Either.Right(clients)
        } catch (e: Exception) {
            Either.Left(GetClientsFailure(e))
        }
    }
}

data class GetClientsFailure(val error: Exception) : Failure.FeatureFailure(error)

