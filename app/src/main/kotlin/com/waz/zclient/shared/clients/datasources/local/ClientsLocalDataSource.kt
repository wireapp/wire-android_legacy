package com.waz.zclient.shared.clients.datasources.local

import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.clients.model.ClientEntity
import com.waz.zclient.storage.db.clients.service.ClientsDao

class ClientsLocalDataSource(private val clientsDao: ClientsDao) {

    suspend fun clientById(clientId: String) = requestDatabase { clientsDao.clientById(clientId) }

    suspend fun allClients() = requestDatabase { clientsDao.allClients().toList() }

    suspend fun updateClients(clients: List<ClientEntity>) = clientsDao.updateClients(clients)

    suspend fun updateClient(client: ClientEntity) = clientsDao.updateClient(client)
}
