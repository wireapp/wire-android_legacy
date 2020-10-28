package com.waz.service

import java.net.{InetSocketAddress, Proxy}

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.HttpProxy._
import com.waz.log.LogSE._

import scala.util.Try

case class ProxyDetails(hostUrl: String, port: Int) {
  val hostAndPort: Option[(String, Int)] =
    if (!hostUrl.equalsIgnoreCase(INVALID_PROXY_HOST)) Some((hostUrl, port)) else None
}

class HttpProxy(metaDataService: MetaDataService, defaultProxyDetails: ProxyDetails) extends DerivedLogTag {
  lazy val proxy: Option[Proxy] = details.hostAndPort.orElse(defaultProxyDetails.hostAndPort).map {
    case (host, port) =>
      verbose(l"HTTP Proxy $host:$port")
      new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))
  }

  private def details = {
    val metadata = metaDataService.metaData
    // empty metadata should be treated the same way as if we didn't find metadata at all
    val url = metadata.getOrElse(HTTP_PROXY_URL_KEY, "")
    if (url.isEmpty) defaultProxyDetails
    else
      metadata.get(HTTP_PROXY_PORT_KEY)
        .flatMap(port => Try(port.toInt).toOption)
        .map(ProxyDetails(url, _))
        .getOrElse(defaultProxyDetails)
  }
}

object HttpProxy {
  val INVALID_PROXY_HOST = "none"
  val HTTP_PROXY_URL_KEY = "http_proxy_url"
  val HTTP_PROXY_PORT_KEY = "http_proxy_port"

  def apply(metaDataService: MetaDataService, defaultProxyDetails: ProxyDetails): HttpProxy =
    new HttpProxy(metaDataService, defaultProxyDetails)
}
