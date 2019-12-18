package com.waz.zclient.devices.data.source.local

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.clients.model.ClientDao
import com.waz.zclient.storage.db.clients.service.ClientDbService

class ClientsLocalDataSource(private val clientDbService: ClientDbService) {

    suspend fun clientById(clientId: String): Either<Failure, ClientDao> =
        requestDatabase { clientDbService.clientById(clientId) }

    suspend fun allClients(): Either<Failure, List<ClientDao>> =
        requestDatabase { clientDbService.allClients().toList() }

    suspend fun updateClients(clients: List<ClientDao>) =
        clientDbService.updateClients(clients)

    suspend fun updateClient(client: ClientDao) =
        clientDbService.updateClient(client)
}
