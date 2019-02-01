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
import com.waz.utils.wrappers.URI

object Backend {

  private val certBytes = BuildConfig.CERTIFICATE_PIN_BYTES.split(",").map(b => Integer.parseInt(b.trim.drop(2), 16).toByte)
  private val certPin = CertificatePin(BuildConfig.CERTIFICATE_PIN_DOMAIN, certBytes)

  private val blacklistHostStaging = URI.parse(s"https://clientblacklist.wire.com/staging/android")

  //This information can be found in downloadable google-services.json file from the BE console.
  val StagingFirebaseOptions = FirebaseOptions(
    "723990470614",
    "1:723990470614:android:9a1527f79aa62284",
    "AIzaSyAGCoJGUtDBLJJiQPLxHQRrdkbyI0wlbo8")
  val ProdFirebaseOptions    = FirebaseOptions(
    BuildConfig.FIREBASE_PUSH_SENDER_ID,
    BuildConfig.FIREBASE_APP_ID,
    BuildConfig.FIREBASE_API_KEY)

  //These are only here so that we can compile tests, the UI sets the backendConfig
  val StagingBackend = BackendConfig(
    URI.parse("https://staging-nginz-https.zinfra.io"),
    URI.parse("https://staging-nginz-ssl.zinfra.io/await"),
    StagingFirebaseOptions,
    "staging",
    blacklistHost = blacklistHostStaging)

  val ProdBackend: BackendConfig = BackendConfig(
    URI.parse(BuildConfig.BACKEND_URL),
    URI.parse(BuildConfig.WEBSOCKET_URL),
    ProdFirebaseOptions,
    "prod",
    certPin,
    URI.parse(BuildConfig.BLACKLIST_HOST))

  lazy val byName = Seq(StagingBackend, ProdBackend).map(b => b.environment -> b).toMap
}
