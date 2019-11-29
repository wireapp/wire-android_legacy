package com.waz.zclient.devices.domain

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.domain.model.Client
import timber.log.Timber

class GetAllClientsUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<List<Client>, Unit>() {

    override suspend fun run(params: Unit): RequestResult<List<Client>> {
        return try {
            clientsRepository.getAllClients()
        } catch (e: Exception) {
            Timber.e(e.localizedMessage)
            RequestResult.error(e.localizedMessage)
        }
    }
}
