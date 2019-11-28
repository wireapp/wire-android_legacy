package com.waz.zclient.devices.domain

import com.waz.zclient.core.data.source.remote.Either
import com.waz.zclient.core.data.source.remote.Failure
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.data.model.ClientEntity

class GetCurrentDeviceUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<ClientEntity, Params>() {

    override suspend fun run(params: Params): Either<Failure, ClientEntity> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

data class Params(val clientId: String)
