package com.waz.zclient.shared.clients

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface ClientsRepository {

    suspend fun clientById(clientId: String): Either<Failure, Client>

    suspend fun allClients(): Either<Failure, List<Client>>
}
