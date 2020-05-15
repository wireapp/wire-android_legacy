package com.waz.zclient.shared.clients.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.fallback
import com.waz.zclient.core.functional.map
import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.ClientsRepository
import com.waz.zclient.shared.clients.datasources.local.ClientsLocalDataSource
import com.waz.zclient.shared.clients.datasources.remote.ClientsRemoteDataSource
import com.waz.zclient.shared.clients.mapper.ClientMapper

class ClientsDataSource constructor(
    private val remoteDataSource: ClientsRemoteDataSource,
    private val localDataSource: ClientsLocalDataSource,
    private val clientMapper: ClientMapper
) : ClientsRepository {

    override suspend fun clientById(clientId: String): Either<Failure, Client> =
        clientByIdLocal(clientId)
            .fallback { clientByIdRemote(clientId) }
            .finally { saveClient(it) }
            .execute()

    override suspend fun allClients() =
        allClientsLocal()
            .fallback { allClientsRemote() }
            .finally { saveAllClients(it) }
            .execute()

    private suspend fun clientByIdRemote(clientId: String) =
        remoteDataSource.clientById(clientId).map { clientMapper.toClient(it) }

    private suspend fun allClientsRemote() =
        remoteDataSource.allClients().map { clientMapper.toListOfClients(it) }

    private fun clientByIdLocal(clientId: String): suspend () -> Either<Failure, Client> = {
        localDataSource.clientById(clientId).map { clientMapper.toClient(it) }
    }

    private fun allClientsLocal(): suspend () -> Either<Failure, List<Client>> = {
        localDataSource.allClients().map { clientMapper.toListOfClients(it) }
    }

    private suspend fun saveClient(client: Client) {
        localDataSource.updateClient(clientMapper.toClientDao(client))
    }

    private suspend fun saveAllClients(clients: List<Client>) {
        localDataSource.updateClients(clientMapper.toListOfClientDao(clients))
    }
}
