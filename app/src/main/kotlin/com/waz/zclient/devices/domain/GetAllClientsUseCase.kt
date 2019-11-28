package com.waz.zclient.devices.domain

import com.waz.zclient.core.data.source.remote.Either
import com.waz.zclient.core.data.source.remote.Failure
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.domain.model.Client
import timber.log.Timber

class GetAllClientsUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<RequestResult<List<Client>>, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, RequestResult<List<Client>>> {
        return try {
            val clients = clientsRepository.getAllClients()
            Either.Right(clients)
        } catch (e: Exception) {
            Timber.e(e.localizedMessage)
            Either.Left(GetClientsFailure(e))
        }
    }
}

data class GetClientsFailure(val error: Exception) : Failure.FeatureFailure(error)

