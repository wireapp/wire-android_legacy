package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.resources.Resource
import com.waz.zclient.devices.data.model.ClientEntity

interface ClientsRemoteDataSource {

    suspend fun getClientById(clientId: String): Resource<ClientEntity>

    suspend fun getAllClients(): Resource<Array<ClientEntity>>
}
