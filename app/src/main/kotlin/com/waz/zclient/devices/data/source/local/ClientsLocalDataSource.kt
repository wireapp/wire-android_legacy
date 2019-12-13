package com.waz.zclient.devices.data.source.local

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.network.requestLocal
import com.waz.zclient.storage.clients.dao.ClientDao
import com.waz.zclient.storage.clients.model.ClientEntity

class ClientsLocalDataSource(private val clientsDao: ClientDao) {

    suspend fun clientById(clientId: String): Either<Failure, ClientEntity> = requestLocal {
        clientsDao.clientById(clientId)
    }

    suspend fun allClients(): Either<Failure, Array<ClientEntity>> = requestLocal {
        clientsDao.allClients()
    }

    fun updateClients(clients: Array<ClientEntity>) {
        clientsDao.updateClients(clients)
    }

    fun updateClient(client: ClientEntity) {
        clientsDao.updateClient(client)
    }
}
