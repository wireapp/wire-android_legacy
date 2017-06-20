/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.profile.preferences.pages

import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.zclient.pages.main.profile.preferences.views.TextButton
import com.waz.zclient.utils.ViewState
import com.waz.zclient.{R, ViewHelper}

class AboutView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_about_layout)

  val versionTextButton = findById[TextButton](R.id.preferences_about_version)

  def setVersion(version: String) = versionTextButton.setTitle(context.getString(R.string.pref_about_version_title, version))
}

case class AboutViewState() extends ViewState {
  override def name = "About" //TODO resource

  override def layoutId = R.layout.preferences_about

  override def onViewAttached(v: View) = {
    Option(v.asInstanceOf[AboutView]).foreach{ view =>
      val version =
        try {
          view.wContext.getPackageManager.getPackageInfo(view.wContext.getPackageName, 0).versionName
        } catch {
          case e: PackageManager.NameNotFoundException => ""
        }
      view.setVersion(version)
    }
  }

  override def onViewDetached() = {}
}
