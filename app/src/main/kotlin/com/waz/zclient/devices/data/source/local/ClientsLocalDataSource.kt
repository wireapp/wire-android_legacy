package com.waz.zclient.devices.data.source.local

import com.waz.zclient.ContextProvider
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.network.requestLocal
import com.waz.zclient.storage.clients.model.ClientEntity
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.pref.GlobalPreferences

class ClientsLocalDataSource {

    private val globalPreferences = GlobalPreferences(ContextProvider.getApplicationContext())
    private val userId = globalPreferences.activeUserId
    private val userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), userId)
    private val clientsDao = userDatabase.clientsDao()

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
