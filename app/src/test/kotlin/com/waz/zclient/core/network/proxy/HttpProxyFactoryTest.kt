package com.waz.zclient.core.network.proxy

import com.waz.zclient.UnitTest
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class HttpProxyFactoryTest : UnitTest() {

    @Test
    fun `Given HttpProxy instance, when proxy host url is valid and port is valid, then return correct proxy instance`() {
        val validHostUrl = "www.wire.com"
        val validPort = "8080"
        val proxyDetails = ProxyDetails(validHostUrl, validPort)

        val proxy = HttpProxyFactory.create(proxyDetails)
        val socketAddress = (proxy?.address() as InetSocketAddress)
        assert(proxy.type() == Proxy.Type.HTTP)
        assert(socketAddress.hostName == validHostUrl)
        assert(socketAddress.port == validPort.toInt())
    }

    @Test
    fun `Given HttpProxy instance, when proxy host url is "none" and port is valid, then return default proxy`() {
        val invalidHostUrl = "none"
        val validPort = "8080"
        val proxyDetails = ProxyDetails(invalidHostUrl, validPort)
        proxyFailure(proxyDetails)
    }

    @Test
    fun `Given HttpProxy instance, when proxy host url is "none" and port is not valid, then return default proxy `() {
        val invalidHostUrl = "none"
        val invalidPort = "Wire"
        val proxyDetails = ProxyDetails(invalidHostUrl, invalidPort)
        proxyFailure(proxyDetails)
    }

    @Test
    fun `Given HttpProxy instance, when proxy host url is valid" and port is not valid, then return default proxy`() {
        val validHostUrl = "www.wire.com"
        val invalidPort = "Wire"
        val proxyDetails = ProxyDetails(validHostUrl, invalidPort)
        proxyFailure(proxyDetails)
    }

    private fun proxyFailure(proxyDetails: ProxyDetails) {
        val proxy = HttpProxyFactory.create(proxyDetails)
        assert(proxy == null)
    }
}
