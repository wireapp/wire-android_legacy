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
package com.waz.api

import java.io.File
import java.net.URL

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.znet2.http.MultipartBodyFormData.Part
import com.waz.znet2.http._

import scala.concurrent.Future

object HockeyCrashReporter {
  import Threading.Implicits.Background
  import HttpClient.dsl._
  import HttpClient.AutoDerivation._

  def uploadCrashReport(hockeyId: String, dump: File, log: File): Future[Unit] = {
    implicit val httpClient: HttpClient = ZMessaging.currentGlobal.httpClientForLongRunning
    Request
      .create(
        method = Method.Post,
        url = new URL(s"https://rink.hockeyapp.net/api/2/apps/$hockeyId/crashes/upload"),
        body = MultipartBodyFormData(Part(dump, name = "attachment0"), Part(log, name = "log"))
      )
      .withResultType[Response[Unit]]
      .execute
      .map { _ =>
        verbose("crash report successfully sent")
        dump.delete()
        log.delete()
      }
      .recover {
        case err => error(s"Unexpected response from hockey crash report request: $err")
      }
      .map(_ => ())
      .future
  }
}
