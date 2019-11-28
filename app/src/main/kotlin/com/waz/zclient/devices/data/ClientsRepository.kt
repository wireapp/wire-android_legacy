package com.waz.zclient.devices.data

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.domain.model.Client

interface ClientsRepository {

    suspend fun getClientById(clientId: String): RequestResult<Client>

    suspend fun getAllClients(): RequestResult<List<Client>>
}
