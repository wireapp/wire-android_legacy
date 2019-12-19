package com.waz.zclient.devices.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.accessData
import com.waz.zclient.devices.data.source.ClientMapper
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client

class ClientsDataSource constructor(
    private val remoteDataSource: ClientsRemoteDataSource,
    private val localDataSource: ClientsLocalDataSource,
    private val clientMapper: ClientMapper) : ClientsRepository {

    override suspend fun clientById(clientId: String) = accessData(clientByIdLocal(clientId), clientByIdRemote(clientId), saveClient())

    override suspend fun allClients() = accessData(allClientsLocal(), allClientsRemote(), saveAllClients())

    private fun clientByIdRemote(clientId: String): suspend () -> Either<Failure, Client> =
        { remoteDataSource.clientById(clientId).map { clientMapper.toClient(it) } }

    private fun allClientsRemote(): suspend () -> Either<Failure, List<Client>> =
        { remoteDataSource.allClients().map { clientMapper.toListOfClients(it) } }

    private fun clientByIdLocal(clientId: String): suspend () -> Either<Failure, Client> =
        { localDataSource.clientById(clientId).map { clientMapper.toClient(it) } }

    private fun allClientsLocal(): suspend () -> Either<Failure, List<Client>> =
        { localDataSource.allClients().map { clientMapper.toListOfClients(it) } }

    private fun saveClient(): suspend (Client) -> Unit =
        { localDataSource.updateClient(clientMapper.toClientDao(it)) }

    private fun saveAllClients(): suspend (List<Client>) -> Unit =
        { localDataSource.updateClients(clientMapper.toListOfClientDao(it)) }

}

