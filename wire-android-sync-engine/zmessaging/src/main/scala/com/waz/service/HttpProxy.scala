package com.waz.service

import java.net.{InetSocketAddress, Proxy}
import android.content.Context
import android.content.pm.PackageManager
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import scala.util.{Failure, Success, Try}
import HttpProxy._

case class ProxyDetails(hostUrl: String, port: String) {
  val hostAndPort: Option[(String, Int)] =
    if (hostUrl.equalsIgnoreCase(INVALID_PROXY_HOST))
      None
    else
      Try(Integer.parseInt(port)).toOption.map((hostUrl, _))
}

class HttpProxy(defaultProxyDetails: ProxyDetails, context: Option[Context] = None, proxyDetails: Option[ProxyDetails] = None) extends DerivedLogTag {
  lazy val proxy: Option[Proxy] = details.hostAndPort.map {
    case (host, port) => new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))
  }

  private def details = (context, proxyDetails) match {
    case (_, Some(customProxyDetails)) => customProxyDetails
    case (Some(ctx), _) =>
      Try(ctx.getPackageManager.getApplicationInfo(ctx.getPackageName, PackageManager.GET_META_DATA).metaData) match {
        case Success(metadata) =>
          // empty metadata should be treated the same way as if we didn't find metadata at all
          val url = metadata.getString(HTTP_PROXY_URL_KEY, "")
          val port = metadata.getString(HTTP_PROXY_PORT_KEY, "")
          verbose(l"HTTP Proxy Details: $url:$port")
          if (url.nonEmpty && port.nonEmpty) ProxyDetails(url, port) else defaultProxyDetails
        case Failure(ex) =>
          error(l"Unable to load metadata: ${ex.getMessage}")
          defaultProxyDetails
      }
    case _ => defaultProxyDetails
  }
}

object HttpProxy {
  val INVALID_PROXY_HOST = "none"
  val HTTP_PROXY_URL_KEY = "http_proxy_url"
  val HTTP_PROXY_PORT_KEY = "http_proxy_port"

  def apply(context: Context, defaultProxyDetails: ProxyDetails): HttpProxy = new HttpProxy(defaultProxyDetails, Some(context))
  def apply(defaultProxyDetails: ProxyDetails, customProxyDetails: ProxyDetails): HttpProxy = new HttpProxy(defaultProxyDetails, proxyDetails = Some(customProxyDetails))
}
