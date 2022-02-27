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
import android.content.{Context, SharedPreferences}
import androidx.preference.PreferenceManager
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{BackendConfig, GlobalModule}
import com.waz.sync.client.CustomBackendClient.BackendConfigResponse
import com.waz.sync.client.SupportedApiConfig
import com.waz.zclient.core.backend.di.BackendModule
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{Backend, BuildConfig}

final class BackendController(implicit context: Context) extends DerivedLogTag {
  import BackendController._

  private lazy val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

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
    val apiInformation = getStringPreference(API_VERSION_INFORMATION)

    (environment, baseUrl, websocketUrl, blackListHost, teamsUrl, accountsUrl, websiteUrl) match {
      case (Some(env), Some(base), Some(web), black, Some(teams), Some(accounts), Some(website)) =>
        info(l"Retrieved stored backend config for environment: ${redactedString(env)}")
        info(l"API version: ${apiInformation}}")


        // Staging requires its own firebase options, but all other BEs (prod or custom)
        // will use the same firebase options.
        val firebaseOptions = if (env.equals(Backend.StagingBackend.environment))
          Backend.StagingFirebaseOptions
        else
          Backend.ProdFirebaseOptions

        val config = BackendConfig(
          env,
          base,
          web,
          black,
          teams,
          accounts,
          website,
          firebaseOptions,
          Backend.certPin,
          apiInformation.flatMap { SupportedApiConfig.fromString }
        )
        Some(config)

      case _ =>
        info(l"Couldn't load backend config due to missing data.")
        None
    }
  }

  /// Saves the given backend config to shared preferences.
  def storeBackendConfig(config: BackendConfig): Unit = {
    prefs.edit()
      .putString(ENVIRONMENT_PREF, config.environment)
      .putString(BASE_URL_PREF, config.baseUrl.toString)
      .putString(WEBSOCKET_URL_PREF, config.websocketUrl.toString)
      .putString(BLACKLIST_HOST_PREF, config.blacklistHost.toString)
      .putString(TEAMS_URL_PREF, config.teamsUrl.toString)
      .putString(ACCOUNTS_URL_PREF, config.accountsUrl.toString)
      .putString(WEBSITE_URL_PREF, config.websiteUrl.toString)
      .putString(API_VERSION_INFORMATION, config.apiVersionInformation.map { _.toString}.getOrElse(""))
      .commit()
  }

  def storeSupportedApiConfig(supportedApiConfig: SupportedApiConfig): Unit = {
    prefs.edit()
      .putString(API_VERSION_INFORMATION, supportedApiConfig.toString)
      .commit()
    verbose(l"Saved backend config with API version information ${supportedApiConfig}")
  }


  /// Switches the backend in the global module and saves the config to shared preferences.
  /// Warning: use with caution. It is assumed that there are no logged in accounts and the
  /// the global module is ready.
  def switchBackend(globalModule: GlobalModule, configResponse: BackendConfigResponse, configUrl: URL): Unit = {
    globalModule.backend.update(configResponse)
    globalModule.blacklistClient.loadVersionBlacklist()
    storeBackendConfig(globalModule.backend)

    prefs.edit().putString(CONFIG_URL_PREF, configUrl.toString).commit()

    BackendModule.getBackendConfigScopeManager.onConfigChanged(configResponse.title)
  }

  def shouldShowBackendSelector: Boolean =
    BuildConfig.DEVELOPER_FEATURES_ENABLED && !backendPreferenceExists

  // This is a helper method to dismiss the backend selector dialog when QA automation
  // selects the backend via an intent.
  def onPreferenceSet(callback: BackendConfig => Unit): Unit = {
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

  private def backendPreferenceExists: Boolean =
    prefs.contains(ENVIRONMENT_PREF)

  private def getStringPreference(key: String): Option[String] =
    Option(prefs.getString(key, null))

  private def getOptIntPreference(key: String): Option[Int] = if(prefs.contains(key))
    Option(prefs.getInt(key, 0))
  else
    None

  private def getIntPreference(key: String): Int = prefs.getInt(key, 0)
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
  val API_VERSION_INFORMATION = "API_VERSION_INFORMATION"
}
