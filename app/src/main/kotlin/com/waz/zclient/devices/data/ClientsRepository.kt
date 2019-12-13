package com.waz.zclient.devices.data

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client

interface ClientsRepository {

    suspend fun clientById(clientId: String): Either<Failure, Client>

    suspend fun allClients(): Either<Failure, List<Client>>
}
