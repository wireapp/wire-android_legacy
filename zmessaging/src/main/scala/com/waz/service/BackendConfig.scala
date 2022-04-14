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
package com.waz.service

import com.waz.service.BackendConfig.FederationSupport
import com.waz.sync.client.CustomBackendClient.BackendConfigResponse
import com.waz.sync.client.SupportedApiConfig
import com.waz.utils.wrappers.URI
import com.waz.znet.ServerTrust

final class BackendConfig(private var _environment: String,
                          private var _baseUrl: URI,
                          private var _websocketUrl: URI,
                          private var _blacklistHost: Option[URI],
                          private var _teamsUrl: URI,
                          private var _accountsUrl: URI,
                          private var _websiteUrl: URI,
                          val firebaseOptions: FirebaseOptions,
                          val pin: CertificatePin = ServerTrust.wirePin,
                          private var _apiVersionInformation: Option[SupportedApiConfig] = None
                         ) {

  val pushSenderId: String = firebaseOptions.pushSenderId

  def environment: String = _environment
  def baseUrl: URI = _baseUrl
  def websocketUrl: URI = _websocketUrl
  def blacklistHost: Option[URI] = _blacklistHost
  def teamsUrl: URI = _teamsUrl
  def accountsUrl: URI = _accountsUrl
  def websiteUrl: URI = _websiteUrl
  def agreedApiVersion: Option[Int] = _apiVersionInformation.flatMap { _.highestCommonAPIVersion() }
  def couldNotAgreeOnApiVersion: Boolean = agreedApiVersion.isEmpty && apiVersionInformation.isDefined
  def apiVersionInformation: Option[SupportedApiConfig] = _apiVersionInformation

  def baseUrlWithApi: URI = agreedApiVersion match {
    case Some(x) if x > 0 =>
      _baseUrl.buildUpon.appendPath(s"v$x").build
    case Some(x) if x == 0 =>  _baseUrl
    case None => _baseUrl
  }

  def federationSupport: FederationSupport =
    FederationSupport(apiVersionInformation.getOrElse(SupportedApiConfig.v0OnlyApiConfig).federation)

  def update(configResponse: BackendConfigResponse): Unit = {
    _environment = configResponse.title
    _baseUrl = URI.parse(configResponse.endpoints.backendURL.toString)
    _websocketUrl = URI.parse(configResponse.endpoints.backendWSURL.toString)
    _blacklistHost = configResponse.endpoints.blackListURL.map(p => URI.parse(p.toString))
    _teamsUrl = URI.parse(configResponse.endpoints.teamsURL.toString)
    _accountsUrl = URI.parse(configResponse.endpoints.accountsURL.toString)
    _websiteUrl = URI.parse(configResponse.endpoints.websiteURL.toString)
  }

  def updateSupportedAPIConfig(supportedApiConfig: SupportedApiConfig): Unit = {
    _apiVersionInformation = Some(supportedApiConfig)
  }

  override def toString: String =
    s"""
      |BackendConfig(
      |  environment:   $environment,
      |  baseUrl:       $baseUrl,
      |  websocketUrl:  $websocketUrl,
      |  blacklistHost: $blacklistHost,
      |  teamsUrl:      $teamsUrl,
      |  accountsUrl:   $accountsUrl,
      |  websiteUrl:    $websiteUrl,
      |  agreedApiVersion:    $agreedApiVersion,
      |  apiVersionInformation: $apiVersionInformation
      |)
      |""".stripMargin
}

object BackendConfig {
  def apply(environment: String,
            baseUrl: String,
            websocketUrl: String,
            blacklistHost: Option[String],
            teamsUrl: String,
            accountsUrl: String,
            websiteUrl: String,
            firebaseOptions: FirebaseOptions,
            pin: CertificatePin = ServerTrust.wirePin,
            apiVersionInformation: Option[SupportedApiConfig] = None
           ): BackendConfig =
    new BackendConfig(
      environment,
      URI.parse(baseUrl),
      URI.parse(websocketUrl),
      blacklistHost.map(URI.parse),
      URI.parse(teamsUrl),
      URI.parse(accountsUrl),
      URI.parse(websiteUrl),
      firebaseOptions,
      pin,
      apiVersionInformation
    )

  final case class FederationSupport(isSupported: Boolean)
}

//cert is expected to be base64-encoded
case class CertificatePin(domain: String, cert: Array[Byte])

case class FirebaseOptions(pushSenderId: String, appId: String, apiKey: String)
