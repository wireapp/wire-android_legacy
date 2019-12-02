package com.waz.zclient.devices.data

import com.waz.zclient.core.resources.Resource
import com.waz.zclient.devices.domain.model.Client

interface ClientsRepository {

    suspend fun getClientById(clientId: String): Resource<Client>

    suspend fun getAllClients(): Resource<List<Client>>
}
