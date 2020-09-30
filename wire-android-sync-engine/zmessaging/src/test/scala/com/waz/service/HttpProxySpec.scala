package com.waz.service

import java.net.InetSocketAddress

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.specs.AndroidFreeSpec

class HttpProxySpec extends AndroidFreeSpec with DerivedLogTag {

  private val DefaultProxyDetails: ProxyDetails = ProxyDetails(HttpProxy.INVALID_PROXY_HOST, "8080")

  scenario("Given HttpProxy instance, when proxy host url is valid and port is valid, then return correct proxy instance") {
    val validHostUrl = "www.wire.com"
    val validPort = "8080"

    val customProxyDetails = ProxyDetails(validHostUrl, validPort)

    val proxy = HttpProxy(DefaultProxyDetails, customProxyDetails).proxy
    proxy.nonEmpty shouldEqual true
    proxy.foreach { p =>
      val socketAddress = p.address().asInstanceOf[InetSocketAddress]
      assert(p.`type`() == java.net.Proxy.Type.HTTP)
      assert(socketAddress.getHostName == validHostUrl)
      assert(socketAddress.getPort == validPort.toInt)
    }
  }

  scenario("Given HttpProxy instance, when proxy host url is 'none' and port is valid, then return default proxy") {
    val invalidHostUrl = "none"
    val validPort = "8080"
    val proxyDetails = ProxyDetails(invalidHostUrl, validPort)
    proxyFailure(proxyDetails)
  }

  scenario("Given HttpProxy instance, when proxy host url is 'none' and port is not valid, then return default proxy") {
    val invalidHostUrl = "none"
    val invalidPort = "Wire"
    val proxyDetails = ProxyDetails(invalidHostUrl, invalidPort)
    proxyFailure(proxyDetails)
  }

  scenario("Given HttpProxy instance, when proxy host url is valid and port is not valid, then return default proxy") {
    val validHostUrl = "www.wire.com"
    val invalidPort = "Wire"
    val proxyDetails = ProxyDetails(validHostUrl, invalidPort)
    proxyFailure(proxyDetails)
  }

  private def proxyFailure(proxyDetails: ProxyDetails) {
    val proxy = HttpProxy(DefaultProxyDetails, proxyDetails).proxy
    proxy shouldEqual None
  }
}
