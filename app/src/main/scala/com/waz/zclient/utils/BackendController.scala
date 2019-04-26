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
package com.waz.zclient.utils

import java.net.URL

import android.app.AlertDialog
import android.content.{Context, DialogInterface, SharedPreferences}
import android.preference.PreferenceManager
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{BackendConfig, GlobalModule}
import com.waz.sync.client.CustomBackendClient.BackendConfigResponse
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{Backend, BuildConfig, R}
import com.waz.znet2.http.Request.UrlCreator


class BackendController(implicit context: Context) extends DerivedLogTag {
  import BackendController._

  private def prefs: SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(context)

  /// A custom backend is one that is loaded by a config url via deep link.
  def hasCustomBackend: Boolean = customBackendConfigUrl.isDefined

  /// The url string where the custom backend config was downloaded from.
  def customBackendConfigUrl: Option[String] = getStringPreference(CONFIG_URL_PREF)

  /// Retrieves the backend config stored in shared preferences, if present.
  def getStoredBackendConfig: Option[BackendConfig] = {
    val environment = getStringPreference(ENVIRONMENT_PREF)
    val baseUrl = getStringPreference(BASE_URL_PREF)
    val websocketUrl = getStringPreference(WEBSOCKET_URL_PREF)
    val blackListHost = getStringPreference(BLACKLIST_HOST_PREF)
    val teamsUrl = getStringPreference(TEAMS_URL_PREF)
    val accountsUrl = getStringPreference(ACCOUNTS_URL_PREF)
    val websiteUrl = getStringPreference(WEBSITE_URL_PREF)

    // TODO: Find  a way to clean this up.
    (environment, baseUrl, websocketUrl, blackListHost, teamsUrl, accountsUrl, websiteUrl) match {
      case (Some(env), Some(base), Some(web), Some(black), Some(teams), Some(accounts), Some(website)) =>
        info(l"Retrieved stored backend config for environment: ${redactedString(env)}")

        // Staging requires its own firebase options, but all other BEs (prod or custom)
        // will use the same firebase options.
        val firebaseOptions = if (env.equals(Backend.StagingBackend.environment))
          Backend.StagingFirebaseOptions
        else
          Backend.ProdFirebaseOptions

        val config = BackendConfig(env, base, web, black, teams, accounts, website, firebaseOptions, Backend.certPin)
        Some(config)

      case _ =>
        info(l"Couldn't load backend config due to missing data.")
        None
    }
  }

  /// Saves the given backend config to shared preferences.
  def setStoredBackendConfig(config: BackendConfig): Unit = {
    prefs.edit()
      .putString(ENVIRONMENT_PREF, config.environment)
      .putString(BASE_URL_PREF, config.baseUrl.toString)
      .putString(WEBSOCKET_URL_PREF, config.websocketUrl.toString)
      .putString(BLACKLIST_HOST_PREF, config.blacklistHost.toString)
      .putString(TEAMS_URL_PREF, config.teamsUrl.toString)
      .putString(ACCOUNTS_URL_PREF, config.accountsUrl.toString)
      .putString(WEBSITE_URL_PREF, config.websiteUrl.toString)
      .commit()
  }

  /// Presents a dialog to select the backend if necessary, otherwise loads the stored
  /// backend preference if present, otherwise loads the default production backend.
  def selectBackend(callback: BackendConfig => Unit): Unit = {
    if (shouldShowBackendSelector) showDialog(callback)
    else callback(getStoredBackendConfig.getOrElse(Backend.ProdBackend))
  }

  /// Switches the backend in the global module and saves the config to shared preferences.
  /// Warning: use with caution. It is assumed that there are no logged in accounts and the
  /// the global module is ready.
  def switchBackend(globalModule: GlobalModule, configResponse: BackendConfigResponse, configUrl: URL): Unit = {
    globalModule.backend.update(configResponse)
    setStoredBackendConfig(globalModule.backend)

    prefs.edit().putString(CONFIG_URL_PREF, configUrl.toString).commit()
  }

  /// Presents a dialog to select backend.
  private def showDialog(callback: BackendConfig => Unit): Unit = {
    val environments = Backend.byName
    val items: Array[CharSequence] = environments.keys.toArray

    val builder = new AlertDialog.Builder(context)
    builder.setTitle("Select Backend")

    builder.setItems(items, new DialogInterface.OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        val choice = items.apply(which).toString
        val config = environments.apply(choice)
        setStoredBackendConfig(config)
        callback(config)
      }
    })

    builder.setCancelable(false)
    builder.create().show()

    // QA needs to be able to switch backends via intents. Any changes to the
    // preference while the dialog is open will be treated as a user selection.
    val listener = new SharedPreferences.OnSharedPreferenceChangeListener {
      override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String): Unit = {
        if (key.equals(ENVIRONMENT_PREF)) {
          callback(getStoredBackendConfig.getOrElse(Backend.ProdBackend))
          prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
      }
    }

    prefs.registerOnSharedPreferenceChangeListener(listener)
  }

  private def shouldShowBackendSelector: Boolean =
    BuildConfig.DEVELOPER_FEATURES_ENABLED && !backendPreferenceExists

  private def backendPreferenceExists: Boolean =
    prefs.contains(ENVIRONMENT_PREF)

  private def getStringPreference(key: String): Option[String] =
    Option(prefs.getString(key, null))

  def urls: BackendUrls = {
    val config = getStoredBackendConfig.getOrElse(Backend.ProdBackend)
    new BackendUrls(config, hasCustomBackend)
  }
}

object BackendController {
  // Preference Keys
  val ENVIRONMENT_PREF = "CUSTOM_BACKEND_ENVIRONMENT"
  val BASE_URL_PREF = "CUSTOM_BACKEND_BASE_URL"
  val WEBSOCKET_URL_PREF = "CUSTOM_BACKEND_WEBSOCKET_URL"
  val BLACKLIST_HOST_PREF = "CUSTOM_BACKEND_BLACKLIST_HOST"
  val TEAMS_URL_PREF = "CUSTOM_BACKEND_TEAMS_URL"
  val ACCOUNTS_URL_PREF = "CUSTOM_BACKEND_ACCOUNTS_URL"
  val WEBSITE_URL_PREF = "CUSTOM_BACKEND_WEBSITE_URL"
  val CONFIG_URL_PREF = "CUSTOM_BACKEND_CONFIG_URL"

  def apply()(implicit context: Context): BackendController = new BackendController()
}

class BackendUrls(config: BackendConfig, isCustom: Boolean = false)(implicit context: Context) {

  import ContextUtils.getString
  import BackendUrls._



  // Used to generate various links to external support websites.
  val teamsUrl: UrlCreator = UrlCreator.simpleAppender(() =>  config.teamsUrl.toString)
  val accountsUrl: UrlCreator = UrlCreator.simpleAppender(() => config.accountsUrl.toString)
  val websiteUrl: UrlCreator = UrlCreator.simpleAppender(() => config.websiteUrl.toString)

  // Accounts

  def forgotPassword: String = {
    if (isCustom) accountsUrl.create(ForgotPath, List.empty).toString
    else if (config == Backend.StagingBackend) getString(R.string.url_password_reset_staging)
    else getString(R.string.url_password_reset)
  }

  // Teams

  def prefsManageTeam: String = {
    if (isCustom) teamsUrl.create(PrefsManageTeamPath, List.empty).toString
    else getString(R.string.pref_manage_team_url)
  }

  def startUIManageTeam: String = {
    if (isCustom) teamsUrl.create(StartUIManageTeamPath, List.empty).toString
    else getString(R.string.pick_user_manage_team_url)
  }

  def manageServices: String = {
    if (isCustom) config.teamsUrl.toString
    else if (config == Backend.StagingBackend) getString(R.string.url_manage_services_staging)
    else getString(R.string.url_manage_services)
  }

  // Website

  def homePage: String = {
    if (isCustom) config.websiteUrl.toString
    else getString(R.string.url_home)
  }

  def aboutWebsite: String = {
    if (isCustom) config.websiteUrl.toString
    else getString(R.string.pref_about_website_url)
  }

  def aboutTeams: String = {
    if (isCustom) websiteUrl.create(AboutTeamsPath, List.empty).toString
    else getString(R.string.url_about_teams)
  }

  def usernamesLearnMore: String = {
    if (isCustom) websiteUrl.create(UsernamesLearnMorePath, List.empty).toString
    else getString(R.string.usernames__learn_more__link)
  }

  def privacyPolicy: String = {
    if (isCustom) websiteUrl.create(PrivacyPolicyPath, List.empty).toString
    else getString(R.string.url_privacy_policy)
  }

  def personalTermsOfService: String = {
    if (isCustom) websiteUrl.create(PersonalTermsOfServicePath, List.empty).toString
    else getString(R.string.url_terms_of_service_personal)
  }

  def teamsTermsOfService: String = {
    if (isCustom) websiteUrl.create(TeamsTermsOfServicePath, List.empty).toString
    else getString(R.string.url_terms_of_service_teams)
  }

  def thirdPartyLicenses: String = {
    if (isCustom) websiteUrl.create(ThirdPartyLicensesPath, List.empty).toString
    else getString(R.string.url_third_party_licences)
  }

  def otrLearnWhy: String = {
    if (isCustom) websiteUrl.create(OtrLearnWhyPath, List.empty).toString
    else getString(R.string.url_otr_learn_why)
  }

  def otrLearnHow: String = {
    if (isCustom) websiteUrl.create(OtrLearnHowPath, List.empty).toString
    else getString(R.string.url_otr_learn_how)
  }

  def decryptionError1: String = {
    if (isCustom) websiteUrl.create(DecryptionError1Path, List.empty).toString
    else getString(R.string.url_otr_decryption_error_1)
  }

  def decryptionError2: String = {
    if (isCustom) websiteUrl.create(DecryptionError2Path, List.empty).toString
    else getString(R.string.url_otr_decryption_error_2)
  }

  // Support

  def help: String = getString(R.string.url__help)

  def supportPage: String = getString(R.string.pref_support_website_url)

  def contactSupport: String = getString(R.string.pref_support_contact_url)

  def invalidEmailHelp: String = getString(R.string.invalid_email_help)

  def aboutSetTeamEmail: String = getString(R.string.teams_set_email_about_url)

}

object BackendUrls {
  val ForgotPath = "/forgot/"
  val PrefsManageTeamPath = "/login/?utm_source=client_settings&amp;utm_term=android"
  val StartUIManageTeamPath = "/login/?utm_source=client_landing&amp;utm_term=android"
  val PersonalTermsOfServicePath = "/legal/terms/personal/"
  val TeamsTermsOfServicePath = "/legal/terms/teams/"
  val PrivacyPolicyPath = "/legal/privacy/embed/"
  val ThirdPartyLicensesPath = "/legal/#licenses"
  val OtrLearnWhyPath = "/privacy/why"
  val OtrLearnHowPath = "/privacy/how"
  val DecryptionError1Path = "/privacy/error-1"
  val DecryptionError2Path = "/privacy/error-2"
  val AboutTeamsPath = "/products/pro-secure-team-collaboration/"
  val UsernamesLearnMorePath = "/support/username/"
}
