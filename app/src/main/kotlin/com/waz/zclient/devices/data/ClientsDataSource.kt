package com.waz.zclient.devices.data

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.devices.domain.model.Client

interface ClientsDataSource {

    suspend fun clientById(clientId: String): Either<Failure, Client>

    suspend fun allClients(): Either<Failure, List<Client>>
}
