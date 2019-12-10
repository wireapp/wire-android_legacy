package com.waz.zclient.core.network.api.client

import com.waz.zclient.features.clients.ClientEntity
import retrofit2.Call
import retrofit2.Retrofit

class ClientsService(private val retrofit: Retrofit): ClientsApi {

    private val clientsApi by lazy { retrofit.create(ClientsApi::class.java) }

    override fun allClients(): Call<List<ClientEntity>> = clientsApi.allClients()
    override fun clientById(clientId: String?): Call<ClientEntity> = clientsApi.clientById(clientId)
}
