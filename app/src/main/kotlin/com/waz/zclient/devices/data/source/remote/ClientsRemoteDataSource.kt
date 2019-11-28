package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.data.model.ClientEntity

interface ClientsRemoteDataSource {

    suspend fun getClientById(clientId: String): RequestResult<ClientEntity>

    suspend fun getAllClients(): RequestResult<Array<ClientEntity>>
}
