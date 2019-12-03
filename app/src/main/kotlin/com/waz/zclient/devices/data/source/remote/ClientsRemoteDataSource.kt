package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.devices.data.model.ClientEntity

interface ClientsRemoteDataSource {

    suspend fun clientById(clientId: String?): Either<Failure, ClientEntity>

    suspend fun allClients(): Either<Failure, Array<ClientEntity>>
}
