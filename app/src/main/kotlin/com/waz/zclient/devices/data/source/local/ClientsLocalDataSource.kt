package com.waz.zclient.devices.data.source.local

import com.waz.zclient.ContextProvider
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.storage.clients.model.ClientEntity
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.pref.GlobalPreferences

class ClientsLocalDataSource {

    private val globalPreferences = GlobalPreferences(ContextProvider.getApplicationContext())
    private val userId = globalPreferences.activeUserId
    private val userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), userId)
    private val clientsDao = userDatabase.clientsDao()

    suspend fun clientById(clientId: String?): Either<Failure, ClientEntity> =
        clientId?.let {
            Either.Right(clientsDao.clientById(it))
        } ?: Either.Left(Failure("Something, went wrong please try again"))

    suspend fun allClients(): Either<Failure, Array<ClientEntity>> =
        Either.Right(clientsDao.allClients())

    fun updateClients(clients: Array<ClientEntity>) {
        clientsDao.updateClients(clients)
    }

    fun updateClient(client: ClientEntity) {
        clientsDao.updateClient(client)
    }
}
