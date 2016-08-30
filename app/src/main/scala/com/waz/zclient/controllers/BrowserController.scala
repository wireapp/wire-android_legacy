/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.controllers

import android.content.{Context, Intent}
import android.net.Uri
import com.waz.utils.LoggedTry
import com.waz.ZLog.ImplicitTag._

class BrowserController(context: Context) {

  def normalizeHttp(uri: Uri) =
    if (uri.getScheme == null) uri.buildUpon().scheme("http").build()
    else uri.normalizeScheme()

  def openUrl(uri: Uri) = LoggedTry {
    val intent = new Intent(Intent.ACTION_VIEW, normalizeHttp(uri))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }
}
