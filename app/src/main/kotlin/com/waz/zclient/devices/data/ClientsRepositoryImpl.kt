package com.waz.zclient.devices.data

import com.waz.zclient.core.resources.Resource
import com.waz.zclient.devices.data.source.remote.ClientsNetwork
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSourceImpl
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.devices.mapper.toDomainList
import com.waz.zclient.devices.mapper.toDomainObject

class ClientsRepositoryImpl private constructor(private val remoteDataSource: ClientsRemoteDataSource) : ClientsRepository {

    override suspend fun getClientById(clientId: String): Resource<Client> = remoteDataSource.getClientById(clientId).toDomainObject()

    override suspend fun getAllClients(): Resource<List<Client>> = remoteDataSource.getAllClients().toDomainList()

    companion object {

        @Volatile
        private var clientsRepository: ClientsRepositoryImpl? = null

        fun getInstance(remoteDataSource: ClientsRemoteDataSource = ClientsRemoteDataSourceImpl(ClientsNetwork().getClientsApi())): ClientsRepository =
            clientsRepository ?: synchronized(this) {
                clientsRepository ?: ClientsRepositoryImpl(remoteDataSource).also {
                    clientsRepository = it
                }
            }

        fun destroyInstance() {
            clientsRepository = null
        }
    }
}
