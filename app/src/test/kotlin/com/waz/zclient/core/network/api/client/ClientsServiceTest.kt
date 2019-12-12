package com.waz.zclient.core.network.api.client

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.NetworkClient
import org.junit.Before
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import retrofit2.Retrofit

class ClientsServiceTest : UnitTest() {

    private lateinit var clientsService: ClientsService

    @Mock private lateinit var networkClient: NetworkClient
    @Mock private lateinit var clientsApi: ClientsApi

    @Before
    fun setUp() {
        given { networkClient.create(ClientsApi::class.java) }.willReturn { clientsApi }
        clientsService = ClientsService(networkClient)
    }

    @Test
    fun `should call clients api to retrieve all clients`() {
        clientsService.allClients()

        verify(clientsApi).allClients()
        verifyNoMoreInteractions()
    }
}
