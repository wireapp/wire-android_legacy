package com.waz.zclient.core.network.proxy

import com.waz.zclient.UnitTest
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class HttpProxyFactoryTest : UnitTest() {

    @Test
    fun `given HttpProxy instance, when proxy host url is valid and port is valid, then return correct proxy instance`() {
        val validHostUrl = "www.wire.com"
        val valiePort = "8080"
        val proxyDetails = ProxyDetails(validHostUrl, valiePort)

        val proxy = HttpProxyFactory.generateProxy(proxyDetails)
        val socketAddress = (proxy?.address() as InetSocketAddress)
        assert(proxy.type() == Proxy.Type.HTTP)
        assert(socketAddress.hostName == validHostUrl)
        assert(socketAddress.port == valiePort.toInt())
    }

    @Test
    fun `given HttpProxy instance, when proxy host url is "none" and port is valid, then return default proxy`() {
        val invalidHostUrl = "none"
        val valiePort = "8080"
        val proxyDetails = ProxyDetails(invalidHostUrl, valiePort)
        proxyFailure(proxyDetails)
    }

    @Test
    fun `given HttpProxy instance, when proxy host url is "none" and port is not valid, then return default proxy `() {
        val invalidHostUrl = "none"
        val invalidPort = "Wire"
        val proxyDetails = ProxyDetails(invalidHostUrl, invalidPort)
        proxyFailure(proxyDetails)
    }

    @Test
    fun `given HttpProxy instance, when proxy host url is valid" and port is not valid, then return default proxy`() {
        val validHostUrl = "www.wire.com"
        val invalidPort = "Wire"
        val proxyDetails = ProxyDetails(validHostUrl, invalidPort)
        proxyFailure(proxyDetails)
    }

    private fun proxyFailure(proxyDetails: ProxyDetails) {
        val proxy = HttpProxyFactory.generateProxy(proxyDetails)
        assert(proxy == null)
    }
}
