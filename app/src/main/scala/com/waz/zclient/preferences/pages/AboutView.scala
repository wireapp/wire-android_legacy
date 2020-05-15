/**
 * Wire
 * Copyright (C) 2020 Wire Swiss GmbH
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
package com.waz.zclient.preferences.pages

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{MetaDataService, ZMessaging}
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.preferences.pages.AboutView._
import com.waz.zclient.preferences.views.TextButton
import com.waz.zclient.utils.BackStackKey
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class AboutView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {
  
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_about_layout)

  private var versionClickCounter: Int = 0

  private lazy val browser = inject[BrowserController]

  val websiteButton       = findById[TextButton](R.id.preferences_about_website)
  val termsButton         = findById[TextButton](R.id.preferences_about_terms)
  val privacyPolicyButton = findById[TextButton](R.id.preferences_about_privacy)
  val licenseButton       = findById[TextButton](R.id.preferences_about_license)

  val versionTextButton = findById[TextButton](R.id.preferences_about_version)
  val copyrightButton = findById[TextButton](R.id.preferences_about_copyright)

  websiteButton.onClickEvent { _ => browser.openAboutWebsite() }
  termsButton.onClickEvent { _ =>
    if (inject[Signal[Option[ZMessaging]]].map(_.flatMap(_.teamId)).currentValue.flatten.isDefined)
      browser.openTeamsTermsOfService()
    else
      browser.openPersonalTermsOfService()
  }
  privacyPolicyButton.onClickEvent { _ => browser.openPrivacyPolicy() }
  licenseButton.onClickEvent {_ => browser.openThirdPartyLicenses() }

  versionTextButton.onClickEvent{ _ =>
    versionClickCounter += 1
    if (versionClickCounter >= A_BUNCH_OF_CLICKS_TO_PREVENT_ACCIDENTAL_TRIGGERING) {
      versionClickCounter = 0
      showToast(getVersion)
    }
  }

  def setVersion(version: String) = versionTextButton.setTitle(getString(R.string.pref_about_version_title, version))

  def getVersion(implicit context: Context): String = {
    val md = inject[MetaDataService]
    val translationId = getResources.getIdentifier("wiretranslations_version", "string", context.getPackageName)
    val translationLibVersion = if(translationId == 0) "n/a" else getString(translationId)
    s"""
      |Version:             ${md.versionName} (${md.appVersion}
      |AVS:                 ${getString(R.string.avs_version)}
      |Audio-notifications: ${getString(R.string.audio_notifications_version)}
      |Translations:        $translationLibVersion
      |Locale:              $getLocale
    """.stripMargin
  }
}

object AboutView {
  val A_BUNCH_OF_CLICKS_TO_PREVENT_ACCIDENTAL_TRIGGERING = 10
}

case class AboutBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_about_screen_title

  override def layoutId = R.layout.preferences_about

  override def onViewAttached(v: View) = {
    Option(v.asInstanceOf[AboutView]).foreach { view =>
      val version =
        try {
          view.wContext.getPackageManager.getPackageInfo(view.wContext.getPackageName, 0).versionName
        } catch {
          case _: PackageManager.NameNotFoundException => ""
        }
      view.setVersion(version)
    }
  }

  override def onViewDetached() = {}
}
