package com.waz.zclient.devices.data

import com.waz.zclient.core.network.requestRemote
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.requests.map
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
        requestRemote {
            remoteDataSource.clientById(clientId).map {
                it.toClient()
            }
        }

    override suspend fun allClients(): Either<Failure, List<Client>> =
        requestRemote {
            remoteDataSource.allClients().map {
                it.toListOfClients()
            }
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
