package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.devices.model.ClientEntity
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path

interface ClientsApi {

    @GET("/clients/{clientId}")
    suspend fun getClientByIdAsync(@Path("clientId") clientId: String, @Header("Authorization") token: String): Response<ClientEntity>

    @GET("/clients")
    suspend fun getAllClientsAsync(@Header("Authorization") token: String): Response<Array<ClientEntity>>
}
