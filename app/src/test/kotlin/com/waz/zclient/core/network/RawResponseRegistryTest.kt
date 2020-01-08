package com.waz.zclient.core.network

import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import retrofit2.Response

class RawResponseRegistryTest {

    private lateinit var rawResponseRegistry: RawResponseRegistry

    @Before
    fun setUp() {
        rawResponseRegistry = RawResponseRegistry()
    }

    @Test
    fun `given an action is added, performs the action with given response when notifyRawResponse is called`() {
        val action: (Response<*>) -> Unit = mock(Function1::class.java) as (Response<*>) -> Unit
        val response = mock(Response::class.java)

        rawResponseRegistry.addRawResponseAction(action)

        rawResponseRegistry.notifyRawResponseReceived(response)
        verify(action).invoke(response)
    }
}
