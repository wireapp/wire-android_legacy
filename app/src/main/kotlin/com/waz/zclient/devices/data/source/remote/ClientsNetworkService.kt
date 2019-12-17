package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.devices.data.source.remote.model.ClientApi
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ClientsNetworkService {

    @GET("/clients/{clientId}")
    suspend fun clientById(@Path("clientId") clientId: String?): Response<ClientApi>

    @GET("/clients")
    suspend fun allClients(): Response<List<ClientApi>>
}
