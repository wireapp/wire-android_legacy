package com.waz.zclient.core.network.api.client

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.NetworkClient
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class ClientsServiceTest : UnitTest() {

    private lateinit var clientsService: ClientsService

    @Mock private lateinit var networkClient: NetworkClient
    @Mock private lateinit var clientsApi: ClientsApi

    @Before
    fun setUp() {
//        `when`(networkClient.create(ClientsApi::class.java)).thenReturn(clientsApi)
//        clientsService = ClientsService(networkClient)
    }

    @Test
    fun `should call clients api to retrieve all clients`() {
//        clientsService.allClients()
//
//        verify(clientsApi).allClients()
//        verifyNoMoreInteractions()
    }
}
