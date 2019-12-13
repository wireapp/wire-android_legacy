package com.waz.zclient.devices.data

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.mapper.toClient
import com.waz.zclient.devices.mapper.toListOfClients

class ClientsRepository private constructor(
    private val remoteDataSource: ClientsRemoteDataSource,
    private val localDataSource: ClientsLocalDataSource) : ClientsDataSource {

    override suspend fun clientById(clientId: String): Either<Failure, Client> =
        resultEither(
            databaseRequest = { localDataSource.clientById(clientId) },
            networkRequest = { remoteDataSource.clientById(clientId) },
            saveCallRequest = { localDataSource.updateClient(it) }).map {
            it.toClient()
        }

    override suspend fun allClients(): Either<Failure, List<Client>> =
        resultEither(
            databaseRequest = { localDataSource.allClients() },
            networkRequest = { remoteDataSource.allClients() },
            saveCallRequest = { localDataSource.updateClients(it) }).map {
            it.toListOfClients()
        }

    companion object {

        @Volatile
        private var clientsRepository: ClientsRepository? = null

        fun getInstance(remoteDataSource: ClientsRemoteDataSource,
                        localDataSource: ClientsLocalDataSource): ClientsDataSource =
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
