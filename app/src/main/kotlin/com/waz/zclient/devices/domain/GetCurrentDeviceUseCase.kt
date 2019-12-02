package com.waz.zclient.devices.domain

import com.waz.zclient.core.resources.Resource
import com.waz.zclient.core.usecase.coroutines.UseCase
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.domain.model.Client

class GetCurrentDeviceUseCase(private val clientsRepository: ClientsRepository)
    : UseCase<Client, Params>() {

    override suspend fun run(params: Params): Resource<Client> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

data class Params(val clientId: String)
