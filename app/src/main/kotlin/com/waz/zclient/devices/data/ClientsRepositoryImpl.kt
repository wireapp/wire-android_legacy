package com.waz.zclient.devices.data

import androidx.annotation.VisibleForTesting
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSourceImpl

class ClientsRepositoryImpl(private val remoteDataSource: ClientsRemoteDataSource) : ClientsRepository {

    override suspend fun getAllClients() = remoteDataSource.getAllClients()

    companion object {

        @Volatile
        @VisibleForTesting
        internal var instance: ClientsRepositoryImpl? = null

        fun getInstance(remoteDataSource: ClientsRemoteDataSource = ClientsRemoteDataSourceImpl()): ClientsRepository =
            instance ?: synchronized(this) {
                instance ?: ClientsRepositoryImpl(remoteDataSource).also {
                    instance = it
                }
            }
    }
}
