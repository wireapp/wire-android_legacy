package com.waz.zclient.devices.domain

import com.fernandocejas.sample.core.functional.Either
import com.fernandocejas.sample.core.functional.Failure
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.model.ClientEntity

class GetCurrentDeviceUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<Params, ClientEntity>() {
    override suspend fun run(params: ClientEntity): Either<Failure, Params> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

data class Params(val clientId: String)
