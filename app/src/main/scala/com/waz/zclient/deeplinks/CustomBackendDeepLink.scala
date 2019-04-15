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
package com.waz.zclient.deeplinks

import android.content.Intent
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.Injectable
import com.waz.zclient.log.LogUI._


trait CustomBackendDeepLink {
  def checkForCustomBackend(intent: Intent): Unit
}

class CustomBackendDeepLinkImpl extends CustomBackendDeepLink with Injectable with DerivedLogTag {

  def checkForCustomBackend(intent: Intent): Unit = {
    /**
      * Android doesn't seem to provide validation for URLs beyond simple checks like
      * "startsWith("http://"). Since the scheme is filtered by the Android system already
      * we just forgo any kind of testing like that and just try and contact the config url
      * as a check.
      */
    Option(intent.getDataString).flatMap(DeepLinkParser.parseLink) match {
      case Some((DeepLink.Access, token)) =>
        val res = DeepLinkParser.parseToken(DeepLink.Access, token)
        verbose(l"Got token: $res")
      case e => error(l"Ignoring deep link: $e")
    }
  }


  //def checkBackend(url: URL): ErrorOr[BackendConfigJson] = {
  //  debug(l"trying to fetch backend config at ")
  //  Request
  //    .create(Method.Get, url)
  //    .withResultType[http.Response[BackendConfigJson]]
  //    .withErrorType[ErrorResponse]
  //    .executeSafe
  //    .map { _.right.map(resp => LoginResult(resp.body, resp.headers, Some(label))) }
  //    .future
  //}

}
