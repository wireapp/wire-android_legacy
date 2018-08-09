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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationsPath
import com.waz.utils.{Json, JsonDecoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

import scala.util.Try

trait IntegrationsClient {
  def searchIntegrations(startWith: String): ErrorOrResponse[Map[IntegrationData, Option[AssetData]]]
  def getIntegration(providerId: ProviderId, integrationId: IntegrationId): ErrorOrResponse[(IntegrationData, Option[AssetData])]

  def getProvider(id: ProviderId): ErrorOrResponse[ProviderData]
  def addBot(rConvId: RConvId, pId: ProviderId, iId: IntegrationId): ErrorOrResponse[ConversationEvent]
  def removeBot(rConvId: RConvId, botId: UserId): ErrorOrResponse[ConversationEvent]
}

class IntegrationsClientImpl(implicit
                             urlCreator: UrlCreator,
                             httpClient: HttpClient,
                             authRequestInterceptor: AuthRequestInterceptor) extends IntegrationsClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivation._
  import IntegrationsClient._

  private implicit val integrationSearchDeserializer: RawBodyDeserializer[Map[IntegrationData, Option[AssetData]]] =
    RawBodyDeserializer[JSONObject].map(json => IntegrationsSearchResponse.unapply(JsonObjectResponse(json)).get)

  private implicit val addRemoveBotDeserializer: RawBodyDeserializer[ConversationEvent] =
    RawBodyDeserializer[JSONObject].map(json => AddRemoveBotResponse.unapply(JsonObjectResponse(json)).get)

  def searchIntegrations(startWith: String): ErrorOrResponse[Map[IntegrationData, Option[AssetData]]] = {
    Request.Get(relativePath = IntegrationsSearchPath, queryParameters = queryParameters("tags" -> DefaultTag, "start" -> startWith))
      .withResultType[Map[IntegrationData, Option[AssetData]]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def getIntegration(pId: ProviderId, iId: IntegrationId): ErrorOrResponse[(IntegrationData, Option[AssetData])] = {
    Request.Get(relativePath = integrationPath(pId, iId))
      .withResultType[(IntegrationData, Option[AssetData])]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def getProvider(pId: ProviderId): ErrorOrResponse[ProviderData] = {
    Request.Get(relativePath = providerPath(pId))
      .withResultType[ProviderData]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def addBot(rConvId: RConvId, pId: ProviderId, iId: IntegrationId): ErrorOrResponse[ConversationEvent] = {
    debug(s"addBot: rConvId: $rConvId, providerId: $pId, integrationId: $iId")
    Request
      .Post(
        relativePath = s"$ConversationsPath/${rConvId.str}/bots",
        body = Json("provider" -> pId.str, "service" -> iId.str)
      )
      .withResultType[ConversationEvent]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def removeBot(rConvId: RConvId, botId: UserId): ErrorOrResponse[ConversationEvent] = {
    debug(s"removeBot: convId: $rConvId, botId: $botId")
    Request.Delete(relativePath = s"$ConversationsPath/${rConvId.str}/bots/$botId")
      .withResultType[ConversationEvent]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object IntegrationsClient {
  import JsonDecoder._
  import com.waz.model.ConversationEvent.ConversationEventDecoder

  val IntegrationsSearchPath = "/services"
  val DefaultTag = "integration"
  val ProvidersPath = "/providers"
  val IntegrationConvPath = "/conversations"

  def integrationPath(providerId: ProviderId, integrationId: IntegrationId): String =
    s"$ProvidersPath/$providerId/services/$integrationId"

  def providerPath(id: ProviderId): String = s"$ProvidersPath/$id"

  object IntegrationsSearchResponse {
    def unapply(resp: ResponseContent): Option[Map[IntegrationData, Option[AssetData]]] = resp match {
      case JsonObjectResponse(js) if js.has("services") =>
        Try(decodeSeq('services)(js, IntegrationDecoder).toMap).toOption
      case response =>
        warn(s"Unexpected response: $response")
        None
    }
  }

  object AddRemoveBotResponse {
    def unapply(resp: ResponseContent): Option[ConversationEvent] = resp match {
      case JsonObjectResponse(js) if js.has("event") => Try(ConversationEventDecoder(js.getJSONObject("event"))).toOption
      case response =>
        warn(s"Unexpected response: $response")
        None
    }
  }

  implicit lazy val IntegrationDecoder: JsonDecoder[(IntegrationData, Option[AssetData])] = new JsonDecoder[(IntegrationData, Option[AssetData])] {
    override def apply(implicit js: JSONObject): (IntegrationData, Option[AssetData]) = {
      val asset = getCompleteAsset
      (IntegrationData(
        decodeId[IntegrationId]('id),
        decodeId[ProviderId]('provider),
        decodeString('name),
        decodeString('summary),
        decodeString('description),
        asset.map(_.id),
        decodeStringSeq('tags),
        decodeBool('enabled)
      ), asset)
    }
  }

  def getCompleteAsset(implicit js: JSONObject): Option[AssetData] = fromArray(js, "assets") flatMap { assets =>
    Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
      AssetData(
        remoteId = decodeOptRAssetId('key)(js),
        metaData = Some(AssetMetaData.Image(Dim2(0, 0), Image.Tag(decodeString('size)(js))))
      )
    }.collectFirst { case a@AssetData.IsImageWithTag(Tag.Medium) => a } //discard preview
  }

  private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)

}
