package com.waz.zclient.devices.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.mapper.toClient
import com.waz.zclient.devices.mapper.toListOfClients

class ClientsDataSource private constructor(
    private val remoteDataSource: ClientsRemoteDataSource,
    private val localDataSource: ClientsLocalDataSource) : ClientsRepository {

    override suspend fun clientById(clientId: String): Either<Failure, Client> =
        resultEither(
            mainRequest = { localDataSource.clientById(clientId) },
            fallbackRequest = { remoteDataSource.clientById(clientId) },
            saveToDatabase = { localDataSource.updateClient(it) }).map {
            it.toClient()
        }

    override suspend fun allClients(): Either<Failure, List<Client>> =
        resultEither(
            mainRequest = { localDataSource.allClients() },
            fallbackRequest = { remoteDataSource.allClients() },
            saveToDatabase = { localDataSource.updateClients(it) }).map {
            it.toListOfClients()
        }

    companion object {

        @Volatile
        private var clientsRepository: ClientsRepository? = null

        fun getInstance(remoteDataSource: ClientsRemoteDataSource,
                        localDataSource: ClientsLocalDataSource): ClientsRepository =
            clientsRepository ?: synchronized(this) {
                clientsRepository ?: ClientsDataSource(remoteDataSource, localDataSource).also {
                    clientsRepository = it
                }
            }

        fun destroyInstance() {
            clientsRepository = null
        }
    }
}
