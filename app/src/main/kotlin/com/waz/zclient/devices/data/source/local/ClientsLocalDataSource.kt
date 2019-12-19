package com.waz.zclient.devices.data.source.local

import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.clients.model.ClientDao
import com.waz.zclient.storage.db.clients.service.ClientDbService

class ClientsLocalDataSource(private val clientDbService: ClientDbService) {

    suspend fun clientById(clientId: String) = requestDatabase { clientDbService.clientById(clientId) }

    suspend fun allClients() = requestDatabase { clientDbService.allClients().toList() }

    suspend fun updateClients(clients: List<ClientDao>) = clientDbService.updateClients(clients)

    suspend fun updateClient(client: ClientDao) = clientDbService.updateClient(client)
}
