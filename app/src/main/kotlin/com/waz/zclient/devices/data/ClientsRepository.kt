package com.waz.zclient.devices.data

import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.model.ClientEntity

interface ClientsRepository {

    suspend fun getAllClients(): RequestResult<Array<ClientEntity>>
}
