package com.waz.zclient.devices.data

import com.waz.zclient.core.network.requestNetwork
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.requests.flatMap
import com.waz.zclient.devices.data.source.remote.ClientsNetwork
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.mapper.toClient
import com.waz.zclient.devices.mapper.toListOfClients

class ClientsRepository private constructor(private val remoteDataSource: ClientsRemoteDataSource) : ClientsDataSource {

    override suspend fun clientById(clientId: String?): Either<Failure, Client> =
        requestNetwork {
            remoteDataSource.clientById(clientId).flatMap {
                Either.Right(it.toClient())
            }
        }

    override suspend fun allClients(): Either<Failure, List<Client>> =
        requestNetwork {
            remoteDataSource.allClients().flatMap {
                Either.Right(it.toListOfClients())
            }
        }

    companion object {

        @Volatile
        private var clientsRepository: ClientsRepository? = null

        fun getInstance(remoteDataSource: ClientsRemoteDataSource = ClientsRemoteDataSource(ClientsNetwork().getClientsApi())): ClientsDataSource =
            clientsRepository ?: synchronized(this) {
                clientsRepository ?: ClientsRepository(remoteDataSource).also {
                    clientsRepository = it
                }
            }

        fun destroyInstance() {
            clientsRepository = null
        }
    }
}
