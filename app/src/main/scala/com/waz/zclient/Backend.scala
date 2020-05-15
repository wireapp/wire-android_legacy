/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient

import com.waz.service.{BackendConfig, CertificatePin, FirebaseOptions}
import com.waz.utils.SafeBase64

object Backend {

  lazy val byName: Map[String, BackendConfig] =
    Seq(StagingBackend,QaBackend,ProdBackend).map(b => b.environment -> b).toMap

  private val certBytes = SafeBase64.decode(BuildConfig.CERTIFICATE_PIN_BYTES).get
  val certPin = CertificatePin(BuildConfig.CERTIFICATE_PIN_DOMAIN, certBytes)

  //This information can be found in downloadable google-services.json file from the BE console.
  val StagingFirebaseOptions = FirebaseOptions(
    "723990470614",
    "1:723990470614:android:9a1527f79aa62284",
    "AIzaSyAGCoJGUtDBLJJiQPLxHQRrdkbyI0wlbo8")

  val ProdFirebaseOptions = FirebaseOptions(
    BuildConfig.FIREBASE_PUSH_SENDER_ID,
    BuildConfig.FIREBASE_APP_ID,
    BuildConfig.FIREBASE_API_KEY)

  //These are only here so that we can compile tests, the UI sets the backendConfig
  val StagingBackend = BackendConfig(
    environment = "staging",
    baseUrl = "https://staging-nginz-https.zinfra.io",
    websocketUrl = "https://staging-nginz-ssl.zinfra.io/await",
    blacklistHost = s"https://clientblacklist.wire.com/staging/android",
    teamsUrl = "https://wire-teams-staging.zinfra.io",
    accountsUrl = "https://wire-account-staging.zinfra.io",
    websiteUrl = "https://wire.com",
    StagingFirebaseOptions,
    certPin)

  //These are only here so that we can compile tests, the UI sets the backendConfig
  val QaBackend = BackendConfig(
    environment = "qa-demo",
    baseUrl = "https://nginz-https.qa-demo.wire.link",
    websocketUrl = "https://nginz-ssl.qa-demo.wire.link",
    blacklistHost = "https://assets.qa-demo.wire.link/public/blacklist/android.json",
    teamsUrl = "https://teams.qa-demo.wire.link",
    accountsUrl = "https://account.qa-demo.wire.link",
    websiteUrl = "https://webapp.qa-demo.wire.link",
    StagingFirebaseOptions,
    certPin)

  val ProdBackend = BackendConfig(
    environment = "prod",
    BuildConfig.BACKEND_URL,
    BuildConfig.WEBSOCKET_URL,
    BuildConfig.BLACKLIST_HOST,
    teamsUrl = BuildConfig.TEAMS_URL,
    accountsUrl = BuildConfig.ACCOUNTS_URL,
    websiteUrl = BuildConfig.WEBSITE_URL,
    ProdFirebaseOptions,
    certPin)
}
