/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.sync.client

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.net.URL

import com.waz.api.impl.ErrorResponse
import com.waz.sync.client.OpenGraphClient.{OpenGraphData, OpenGraphImage}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.utils.{IoUtils, JsonDecoder, JsonEncoder}
import com.waz.znet2.http.HttpClient.{ConnectionError, HttpClientError, UnknownServiceError}
import com.waz.znet2.http._
import org.json.JSONObject

import scala.util.matching.Regex

trait OpenGraphClient {
  def loadMetadata(uri: URI): ErrorOrResponse[Option[OpenGraphData]]
  def downloadImage(image: OpenGraphImage): CancellableFuture[File]
}

class OpenGraphClientImpl(implicit httpClient: HttpClient) extends OpenGraphClient {
  import HttpClient.AutoDerivationOld._
  import HttpClient.dsl._
  import OpenGraphClient._
  import Threading.Implicits.Background

  implicit val OpenGraphDataDeserializer: RawBodyDeserializer[OpenGraphDataResponse] =
    RawBodyDeserializer[String].map(bodyStr => OpenGraphDataResponse(StringResponse(bodyStr)))

  override def loadMetadata(uri: URI): ErrorOrResponse[Option[OpenGraphData]] = {
    val url = new URL(uri.toString)
    Request.create(method = Method.Get, url = url, headers = Headers("User-Agent" -> DesktopUserAgent))
      .withResultType[OpenGraphDataResponse]
      .withErrorType[ErrorResponse]
      .execute
      .map(response => Right(response.data))
      .recoverWith {
        case _: UnknownServiceError if url.getProtocol == "http" =>
          loadMetadata(
            AndroidURIUtil.parse(
              uri.toString.toLowerCase.trim.replaceFirst("http", "https")
            )
          )
        case _: ConnectionError   => CancellableFuture.successful(Right(None))
        case err: HttpClientError => CancellableFuture.successful(Left(ErrorResponse.errorResponseConstructor.constructFrom(err)))
      }
  }

  private implicit def fileBodyDeserializer: RawBodyDeserializer[File] =
    RawBodyDeserializer.create { body =>
      val tempFile = File.createTempFile("http_client_download", null)
      val out = new BufferedOutputStream(new FileOutputStream(tempFile))
      IoUtils.copy(body.data(), out)
      tempFile
    }

  override def downloadImage(image: OpenGraphImage): CancellableFuture[File] = {
    Request.create(method = Method.Get, url = image.url)
      .withResultType[File]
      .execute
  }

}

object OpenGraphClient {
  val MaxHeaderLength: Int = 16 * 1024 // maximum amount of data to load from website
  val DesktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36"
  val CookiePattern: Regex = """([^=]+)=([^\;]+)""".r

  case class OpenGraphDataResponse(data: Option[OpenGraphData])

  case class OpenGraphImage(url: URL) extends AnyVal

  case class OpenGraphData(title: String,
                           description: String,
                           image: Option[OpenGraphImage],
                           tpe: String,
                           permanentUrl: Option[URL])

  object OpenGraphData extends ((String, String, Option[OpenGraphImage], String, Option[URL]) => OpenGraphData) {
    val Empty = OpenGraphData("", "", None, "", None)

    implicit object Decoder extends JsonDecoder[OpenGraphData] {
      import JsonDecoder._
      override def apply(implicit js: JSONObject): OpenGraphData =
        OpenGraphData(
          'title,
          'description,
          decodeOptString('image).map(new URL(_)).map(OpenGraphImage.apply),
          'tpe,
          decodeOptString('url).map(new URL(_))
        )
    }

    implicit object Encoder extends JsonEncoder[OpenGraphData] {
      override def apply(v: OpenGraphData): JSONObject = JsonEncoder { o =>
        o.put("title", v.title)
        o.put("description", v.description)
        v.image foreach { image => o.put("image", image.url.toString) }
        v.permanentUrl foreach { uri => o.put("url", uri.toString) }
        o.put("tpe", v.tpe)
      }
    }
  }

  object OpenGraphDataResponse {
    val Title = "title"
    val Image = "image"
    val Type = "type"
    val Url = "url"
    val Description = "description"

    val PropertyPrefix: Regex = """^(og|twitter):(.+)""".r
    val MetaTag: Regex = """<\s*meta\s+[^>]+>""".r
    val Attribute: Regex = """(\w+)\s*=\s*("|')([^"']+)("|')""".r
    val TitlePattern: Regex = """<title[^>]*>(.*)</title>""".r

    def apply(body: StringResponse): OpenGraphDataResponse = {
      def htmlTitle: Option[String] = TitlePattern.findFirstMatchIn(body.value).map(_.group(1))

      val ogMeta = MetaTag.findAllIn(body.value).flatMap { meta =>
        val attrs = Attribute.findAllMatchIn(meta).map { m => m.group(1).toLowerCase -> m.group(3) } .toMap
        val name = attrs.get("property").orElse(attrs.get("name"))
        val iter = PropertyPrefix.findAllMatchIn(name.getOrElse("")).map(a => a.group(2).toLowerCase -> attrs.getOrElse("content",""))
        if (iter.hasNext)
          Some(iter.next())
        else
          None
      } .toMap
      OpenGraphDataResponse(
        if (ogMeta.contains(Title) || ogMeta.contains(Image)) {
          Some(
            OpenGraphData(
              ogMeta.get(Title).orElse(htmlTitle).getOrElse(""),
              ogMeta.getOrElse(Description, ""),
              ogMeta.get(Image).map(new URL(_)).map(OpenGraphImage.apply),
              ogMeta.getOrElse(Type, ""),
              ogMeta.get(Url).map(new URL(_))
            )
          )
        } else None)
    }
  }
}
