package com.waz.zclient.devices.data

import com.waz.zclient.core.network.resultEither
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.requests.flatMap
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsNetwork
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.mapper.toClient
import com.waz.zclient.devices.mapper.toListOfClients

class ClientsRepository private constructor(
    private val remoteDataSource: ClientsRemoteDataSource,
    private val localDataSource: ClientsLocalDataSource) : ClientsDataSource {

    override suspend fun clientById(clientId: String?): Either<Failure, Client> =
        resultEither(
            databaseRequest = { localDataSource.clientById(clientId) },
            networkRequest = { remoteDataSource.clientById(clientId) },
            saveCallRequest = { client -> localDataSource.updateClient(client) }
        ).flatMap {
            Either.Right(it.toClient())
        }

    override suspend fun allClients(): Either<Failure, List<Client>> =
        resultEither(
            databaseRequest = { localDataSource.allClients() },
            networkRequest = { remoteDataSource.allClients() },
            saveCallRequest = { clients -> localDataSource.updateClients(clients) }
        ).flatMap {
            Either.Right(it.toListOfClients())
        }

    companion object {

        @Volatile
        private var clientsRepository: ClientsRepository? = null

        fun getInstance(remoteDataSource: ClientsRemoteDataSource = ClientsRemoteDataSource(ClientsNetwork().getClientsApi()),
                        localDataSource: ClientsLocalDataSource = ClientsLocalDataSource()): ClientsDataSource =
            clientsRepository ?: synchronized(this) {
                clientsRepository ?: ClientsRepository(remoteDataSource, localDataSource).also {
                    clientsRepository = it
                }
            }

        fun destroyInstance() {
            clientsRepository = null
        }
    }
}
