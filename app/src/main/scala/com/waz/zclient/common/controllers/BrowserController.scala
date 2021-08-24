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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageId
import com.waz.service.BackendConfig
import com.wire.signals.{EventStream, SourceStream}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.IntentUtils
import com.waz.zclient.{Injectable, Injector, R}

import scala.util.Try

class BrowserController(implicit context: Context, injector: Injector) extends Injectable with DerivedLogTag {
  import BrowserController._

  private lazy val config = inject[BackendConfig]

  val onYoutubeLinkOpened: SourceStream[MessageId] = EventStream[MessageId]()

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
    Option(IntentUtils.getGoogleMapsIntent(
      context,
      location.getLatitude,
      location.getLongitude,
      location.getZoom,
      location.getName)) foreach { context.startActivity }

  def openPlayStoreListing(): Unit =
    openUrl(getString(R.string.url_play_store_listing))

  def openWireTeamManagement(): Unit =
    openUrl(getString(R.string.url_wire_team_management))

  // Accounts

  def openForgotPassword(): Try[Unit] =
    openUrl(getString(R.string.url_password_forgot).replaceFirst(Accounts, config.accountsUrl.toString))

  // Teams

  def openPrefsManageTeam(): Try[Unit] =
    openUrl(getString(R.string.url_pref_manage_team).replaceFirst(Teams, config.teamsUrl.toString))

  def openStartUIManageTeam(): Try[Unit] =
    openUrl(getString(R.string.url_start_ui_manage_team).replaceFirst(Teams, config.teamsUrl.toString))

  def openManageServices(): Try[Unit] =
    openUrl(getString(R.string.url_manage_services).replaceFirst(Teams, config.teamsUrl.toString))

  // Website

  def openHomePage(): Try[Unit] =
    openUrl(getString(R.string.url_home).replaceFirst(Website, config.websiteUrl.toString))

  def openAboutWebsite(): Try[Unit] =
    openUrl(getString(R.string.url_about_website).replaceFirst(Website, config.websiteUrl.toString))

  def openAboutTeams(): Try[Unit] =
    openUrl(getString(R.string.url_about_teams).replaceFirst(Website, config.websiteUrl.toString))

  def openUserNamesLearnMore(): Try[Unit] =
    openUrl(getString(R.string.url_usernames_learn_more).replaceFirst(Website, config.websiteUrl.toString))

  def openPrivacyPolicy(): Try[Unit] =
    openUrl(getString(R.string.url_privacy_policy).replaceFirst(Website, config.websiteUrl.toString))

  def openPersonalTermsOfService(): Try[Unit] =
    openUrl(getString(R.string.url_terms_of_service_personal).replaceFirst(Website, config.websiteUrl.toString))

  def openTeamsTermsOfService(): Try[Unit] =
    openUrl(getString(R.string.url_terms_of_service_teams).replaceFirst(Website, config.websiteUrl.toString))

  def openThirdPartyLicenses(): Try[Unit] =
    openUrl(getString(R.string.url_third_party_licences).replaceFirst(Website, config.websiteUrl.toString))

  def openOtrLearnWhy(): Try[Unit] =
    openUrl(getString(R.string.url_otr_learn_why).replaceFirst(Website, config.websiteUrl.toString))

  def openOtrLearnHow(): Try[Unit] =
    openUrl(getString(R.string.url_otr_learn_how).replaceFirst(Website, config.websiteUrl.toString))

  def openDecryptionError1(): Try[Unit] =
    openUrl(getString(R.string.url_otr_decryption_error_1).replaceFirst(Website, config.websiteUrl.toString))

  def openDecryptionError2(): Try[Unit] =
    openUrl(getString(R.string.url_otr_decryption_error_2).replaceFirst(Website, config.websiteUrl.toString))

  // Support

  // Custom backend configs don't include endpoints for a support website, so just use the
  // resources strings as they are.

  def openHelp(): Try[Unit] = openUrl(getString(R.string.url_help))

  def openSupportPage(): Try[Unit] = openUrl(getString(R.string.url_support_website))

  def openContactSupport(): Try[Unit] = openUrl(getString(R.string.url_contact_support))

  def openInvalidEmailHelp(): Try[Unit] = openUrl(getString(R.string.url_invalid_email_help))

  def openAboutSetTeamEmail(): Try[Unit] = openUrl(getString(R.string.url_teams_set_email_about))

  def openAboutLegalHold(): Try[Unit] = openUrl(getString(R.string.url_legal_hold_about))
}

object BrowserController {
  val Accounts = "\\|ACCOUNTS\\|"
  val Teams = "\\|TEAMS\\|"
  val Website = "\\|WEBSITE\\|"
}
