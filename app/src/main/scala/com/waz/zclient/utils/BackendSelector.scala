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
import com.waz.zclient.{Backend, BuildConfig}

class BackendSelector(implicit context: Context) extends DerivedLogTag {
  import BackendSelector._

  private def prefs: SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(context)

  /// A custom backend is one that is neither the Wire production nor staging backend.
  def hasCustomBackend: Boolean = getStringPreference(CONFIG_URL_PREF).isDefined

  /// The url string where the custom backend config was downloaded from.
  def customBackendConfigUrl: Option[String] = getStringPreference(CONFIG_URL_PREF)

  /// Retrieves the backend config stored in shared preferences, if present.
  def getStoredBackendConfig: Option[BackendConfig] = {
    val environment = getStringPreference(ENVIRONMENT_PREF)
    val baseUrl = getStringPreference(BASE_URL_PREF)
    val websocketUrl = getStringPreference(WEBSOCKET_URL_PREF)
    val blackListHost = getStringPreference(BLACKLIST_HOST_PREF)

    (environment, baseUrl, websocketUrl, blackListHost) match {
      case (Some(env), Some(base), Some(web), Some(black)) =>
        info(l"Retrieved stored backend config for environment: ${redactedString(env)}")

        val firebaseOptions = env match {
          case Backend.ProdEnvironment => Backend.ProdFirebaseOptions
          case Backend.StagingEnvironment => Backend.StagingFirebaseOptions
          case _ => Backend.ProdFirebaseOptions // Custom BEs use production firebase options
        }

        val config = BackendConfig(env, base, web, black, firebaseOptions, Backend.certPin)
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
}

object BackendSelector {
  // Preference Keys
  val ENVIRONMENT_PREF = "CUSTOM_BACKEND_ENVIRONMENT"
  val BASE_URL_PREF = "CUSTOM_BACKEND_BASE_URL"
  val WEBSOCKET_URL_PREF = "CUSTOM_BACKEND_WEBSOCKET_URL"
  val BLACKLIST_HOST_PREF = "CUSTOM_BACKEND_BLACKLIST_HOST"
  val CONFIG_URL_PREF = "CUSTOM_BACKEND_CONFIG_URL"
}
