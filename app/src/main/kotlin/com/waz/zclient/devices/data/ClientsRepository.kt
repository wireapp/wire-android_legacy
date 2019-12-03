package com.waz.zclient.devices.data

import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.devices.domain.model.Client

interface ClientsRepository {

    suspend fun clientById(clientId: String): Either<Failure, Client>

    suspend fun allClients(): Either<Failure, List<Client>>
}
