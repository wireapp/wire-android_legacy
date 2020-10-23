package com.waz.service

import java.net.InetSocketAddress

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.HttpProxy.{HTTP_PROXY_PORT_KEY, HTTP_PROXY_URL_KEY}
import com.waz.specs.AndroidFreeSpec

class HttpProxySpec extends AndroidFreeSpec with DerivedLogTag {

  // default proxy settings with an invalid proxy host result in no proxy being returned if proxy is not specified
  private val noProxyDetails: ProxyDetails = ProxyDetails(HttpProxy.INVALID_PROXY_HOST, 8080)
  private val customProxyDetails: ProxyDetails = ProxyDetails("custom", 8081)
  private val metaDataService = mock[MetaDataService]

  private val validHostUrl = "www.wire.com"
  private val invalidHostUrl = "none"
  private val validPort = 8080
  private val invalidPort = "Wire"


  private def setMetaData(host: String, port: Int): Unit = setMetaData(host, port.toString)
  private def setMetaData(host: String, port: String): Unit =
    (metaDataService.metaData _).expects().atLeastOnce().returning(
      Map(HTTP_PROXY_URL_KEY -> host, HTTP_PROXY_PORT_KEY -> port)
    )

  private def checkProxy(proxy: java.net.Proxy, host: String, port: Int) = {
    val socketAddress = proxy.address().asInstanceOf[InetSocketAddress]
    proxy.`type`() shouldEqual java.net.Proxy.Type.HTTP
    socketAddress.getHostName shouldEqual host
    socketAddress.getPort shouldEqual port
  }

  scenario("Given HttpProxy instance, when proxy host url is valid and port is valid, then return correct proxy instance") {
    setMetaData(validHostUrl, validPort)

    val proxy = HttpProxy(metaDataService, noProxyDetails).proxy
    proxy.nonEmpty shouldEqual true
    proxy.foreach(checkProxy(_, validHostUrl, validPort))
  }

  scenario("Given HttpProxy instance, when proxy host url is 'none' and port is valid, then return none") {
    setMetaData(invalidHostUrl, validPort)
    val proxy = HttpProxy(metaDataService, noProxyDetails).proxy
    proxy.isEmpty shouldEqual true
  }

  scenario("Given HttpProxy instance and custom defaults, when proxy host url is 'none' and port is valid, then return custom proxy") {
    setMetaData(invalidHostUrl, validPort)
    val proxy = HttpProxy(metaDataService, customProxyDetails).proxy
    proxy.nonEmpty shouldEqual true
    proxy.foreach(checkProxy(_, customProxyDetails.hostUrl, customProxyDetails.port))
  }

  scenario("Given HttpProxy instance, when proxy host url is 'none' and port is not valid, then return none") {
    setMetaData(invalidHostUrl, invalidPort)
    val proxy = HttpProxy(metaDataService, noProxyDetails).proxy
    proxy.isEmpty shouldEqual true
  }

  scenario("Given HttpProxy instance and custom defaults, when proxy host url is 'none' and port is not valid, then return custom proxy") {
    setMetaData(invalidHostUrl, invalidPort)
    val proxy = HttpProxy(metaDataService, customProxyDetails).proxy
    proxy.nonEmpty shouldEqual true
    proxy.foreach(checkProxy(_, customProxyDetails.hostUrl, customProxyDetails.port))
  }

  scenario("Given HttpProxy instance, when proxy host url is valid and port is not valid, then return none") {
    setMetaData(validHostUrl, invalidPort)
    val proxy = HttpProxy(metaDataService, noProxyDetails).proxy
    proxy.isEmpty shouldEqual true
  }

  scenario("Given HttpProxy instance and custom defaults, when proxy host url is valid and port is not valid, then return custom proxy") {
    setMetaData(validHostUrl, invalidPort)
    val proxy = HttpProxy(metaDataService, customProxyDetails).proxy
    proxy.nonEmpty shouldEqual true
    proxy.foreach(checkProxy(_, customProxyDetails.hostUrl, customProxyDetails.port))
  }
}
