package com.waz.zclient.shared.clients.datasources.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ClientsApi {

    @GET("/clients/{clientId}")
    suspend fun clientById(@Path("clientId") clientId: String?): Response<ClientResponse>

    @GET("/clients")
    suspend fun allClients(): Response<List<ClientResponse>>
}
