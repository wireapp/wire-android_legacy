package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import org.junit.Before

class NetworkTest : UnitTest() {

    private lateinit var network: Network

    @Before
    fun setUp() {
        network = Network()
    }
}
