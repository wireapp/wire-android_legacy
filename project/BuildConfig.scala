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
import android.Keys._

// Same values as in log_levels.xml
object LogLevel {
  val Verbose = 2
  val Debug = 3
  val Info = 4
  val Warn = 5
  val Error = 6
  val Supress = 99
}

case class BuildConfig (
                 useHockeyUpdate: Boolean = true,
                 useEdgeBackend: Boolean = false,
                 useStagingBackend: Boolean = false,
                 showGridOverlay: Boolean = true,
                 showMentioning: Boolean = true,
                 showDeveloperOptions: Boolean = true,
                 useAudioLink: Boolean = false,
                 gcmEnabled: Boolean = false,
                 showBackendPicker: Boolean = false,
                 loadTimeLogger: Boolean = true,
                 testGallery: Boolean = true,
                 showTestCountryCode: Boolean = true,
                 showAddressBookInvitations: Boolean = true,
                 logLevelUi: Int = LogLevel.Warn,
                 logLevelSe: Int = LogLevel.Supress,
                 logLevelAvs: Int = LogLevel.Verbose,
                 localyticsSenderId: String = BuildConfig.DevLocalyticsSenderId
                 ) {


  def setting = Seq[(String, String, String)] (
    ("boolean", "USE_HOCKEY_UPDATE", useHockeyUpdate.toString),
    ("boolean", "USE_EDGE_BACKEND", useEdgeBackend.toString),
    ("boolean", "USE_STAGING_BACKEND", useStagingBackend.toString),
    ("boolean", "SHOW_GRIDOVERLAY", showGridOverlay.toString),
    ("boolean", "SHOW_MENTIONING", showMentioning.toString),
    ("boolean", "SHOW_DEVELOPER_OPTIONS", showDeveloperOptions.toString),
    ("boolean", "SHOW_BACKEND_PICKER", showBackendPicker.toString),
    ("boolean", "USE_AUDIO_LINK", useAudioLink.toString),
    ("boolean", "GCM_ENABLED", gcmEnabled.toString),
    ("boolean", "IS_LOADTIME_LOGGER_ENABLED", loadTimeLogger.toString),
    ("boolean", "IS_TEST_GALLERY_ALLOWED", testGallery.toString),
    ("boolean", "SHOW_TEST_COUNTRY_CODE", showTestCountryCode.toString),
    ("boolean", "SHOW_ADDRESS_BOOK_INVITATIONS", showAddressBookInvitations.toString),
    ("int", "LOG_LEVEL_UI", logLevelUi.toString),
    ("int", "LOG_LEVEL_SE", logLevelSe.toString),
    ("int", "LOG_LEVEL_AVS", logLevelAvs.toString),
    ("String", "LOCALYTICS_GCM_SENDER_ID", s""""$localyticsSenderId"""")
  )
}

object BuildConfig {

  val DevLocalyticsSenderId = "826316279849"


  lazy val Dev = BuildConfig(
    useEdgeBackend = true,
    showBackendPicker = true
  )

  lazy val Candidate = BuildConfig(
    useStagingBackend = true,
    showMentioning = false,
    showAddressBookInvitations = false,
    gcmEnabled = true,
    logLevelUi = LogLevel.Verbose,
    logLevelSe = LogLevel.Verbose,
    logLevelAvs = LogLevel.Verbose)

  lazy val Prod = BuildConfig(
    useHockeyUpdate = false,
    showGridOverlay = false,
    showMentioning = false,
    showDeveloperOptions = false,
    loadTimeLogger = false,
    testGallery = false,
    showTestCountryCode = false,
    showAddressBookInvitations = false,
    gcmEnabled = true,
    logLevelUi = LogLevel.Supress,
    logLevelSe = LogLevel.Supress,
    logLevelAvs = LogLevel.Supress)

  lazy val Internal = BuildConfig(
    useHockeyUpdate = false,
    showMentioning = false,
    loadTimeLogger = false,
    testGallery = false,
    showTestCountryCode = false,
    showAddressBookInvitations = false,
    logLevelUi = LogLevel.Verbose,
    logLevelSe = LogLevel.Verbose,
    logLevelAvs = LogLevel.Verbose)


  lazy val Avs = BuildConfig(
    showMentioning = false,
    loadTimeLogger = false,
    testGallery = false,
    showTestCountryCode = false,
    logLevelUi = LogLevel.Supress,
    logLevelSe = LogLevel.Verbose,
    logLevelAvs = LogLevel.Verbose)

  lazy val QAAvs = BuildConfig(
    showMentioning = false,
    loadTimeLogger = false,
    logLevelUi = LogLevel.Supress,
    logLevelSe = LogLevel.Verbose,
    logLevelAvs = LogLevel.Verbose)

  lazy val Experimental = BuildConfig(
    logLevelUi = LogLevel.Verbose,
    logLevelSe = LogLevel.Verbose,
    logLevelAvs = LogLevel.Verbose)

}
