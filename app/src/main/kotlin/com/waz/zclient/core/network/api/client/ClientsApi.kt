package com.waz.zclient.core.network.api.client

import com.waz.zclient.features.clients.ClientEntity
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ClientsApi {

    companion object {
        private const val PARAM_CLIENT_ID = "clientId"
        private const val CLIENTS = "/clients"
        private const val CLIENT_DETAILS = "$CLIENTS/{$PARAM_CLIENT_ID}"
    }

    @GET(CLIENTS)
    suspend fun allClients(): Response<List<ClientEntity>>

    @GET(CLIENT_DETAILS)
    suspend fun clientById(@Path(PARAM_CLIENT_ID) clientId: String?): Response<ClientEntity>
}
