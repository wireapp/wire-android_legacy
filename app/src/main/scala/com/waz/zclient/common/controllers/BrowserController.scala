/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.common.controllers

import android.content.{Context, Intent}
import android.net.Uri
import com.waz.api.MessageContent.Location
import com.waz.model.MessageId
import com.waz.service.BackendConfig
import com.waz.zclient.Backend.StagingBackend
import com.waz.utils.events.EventStream
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.IntentUtils
import com.waz.zclient.{Injectable, Injector, R}

import scala.util.Try

class BrowserController(implicit context: Context, injector: Injector) extends Injectable {

  private val beConfig = inject[BackendConfig]

  val onYoutubeLinkOpened = EventStream[MessageId]()

  private def normalizeHttp(uri: Uri) =
    if (uri.getScheme == null) uri.buildUpon().scheme("http").build()
    else uri.normalizeScheme()

  def openUrl(uri: String): Try[Unit] = openUrl(AndroidURIUtil.parse(uri))

  def openUrl(uri: URI): Try[Unit] = Try {
    val intent = new Intent(Intent.ACTION_VIEW, normalizeHttp(AndroidURIUtil.unwrap(uri)))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  def openLocation(location: Location): Unit =
    Option(IntentUtils.getGoogleMapsIntent(context, location.getLatitude, location.getLongitude, location.getZoom, location.getName)) foreach { context.startActivity }

  def openForgotPasswordPage(): Try[Unit] =
    openUrl(getString(if (beConfig == StagingBackend) R.string.url_password_reset_staging else R.string.url_password_reset))

  def openManageTeamsPage(): Try[Unit] =
    openUrl(getString(if (beConfig == StagingBackend) R.string.url_manage_services_staging else R.string.url_manage_services))

}
