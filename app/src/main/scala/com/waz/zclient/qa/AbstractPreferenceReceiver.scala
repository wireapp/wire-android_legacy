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

package com.waz.zclient.qa

import android.app.Activity
import android.content.{BroadcastReceiver, Context, Intent}
import com.waz.content.GlobalPreferences._
import com.waz.content.Preferences.PrefKey
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{BackendConfig, ZMessaging}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.BackendController
import com.waz.zclient.{Backend, BuildConfig, WireApplication}

/**
  * to test, fire an intent using:
  * adb shell am broadcast -a com.waz.zclient.dev.intent.action.ENABLE_TRACKING
  */
trait AbstractPreferenceReceiver extends BroadcastReceiver with DerivedLogTag {

  import AbstractPreferenceReceiver._
  import com.waz.threading.Threading.Implicits.Background

  def setGlobalPref[K: PrefCodec](preference: PrefKey[K], value: K): Unit = {
    val globalPrefs = ZMessaging.globalModule.map(_.prefs)
    globalPrefs.map(_.preference(preference) := value)
    setResultCode(Activity.RESULT_OK)
  }

  def setUserPref[K: PrefCodec](userPref: PrefKey[K], value: K): Unit = {
    val accounts = ZMessaging.accountsService.map(_.zmsInstances)
    accounts.map(_.head.map(_.foreach { zms =>
      zms.userPrefs.preference(userPref) := value
    }))
    setResultCode(Activity.RESULT_OK)
  }

  override def onReceive(context: Context, intent: Intent) = {
    verbose(l"onReceive: ${redactedString(intent.getAction)}")
    intent.getAction match {
      case AUTO_ANSWER_CALL_INTENT =>
        setGlobalPref(AutoAnswerCallPrefKey, intent.getBooleanExtra(AUTO_ANSWER_CALL_INTENT_EXTRA_KEY, false))
      case ENABLE_GCM_INTENT =>
        setGlobalPref(PushEnabledKey, true)
      case DISABLE_GCM_INTENT =>
        setGlobalPref(PushEnabledKey, false)
      case DISABLE_TRACKING_INTENT =>
        setGlobalPref(DeveloperAnalyticsEnabled, false)
      case ENABLE_TRACKING_INTENT =>
        setGlobalPref(DeveloperAnalyticsEnabled, true)
      case HIDE_GDPR_POPUPS =>
        setGlobalPref(ShowMarketingConsentDialog, false)
      case FULL_CONVERSATION_INTENT =>
        setGlobalPref(ShouldCreateFullConversation, intent.getBooleanExtra(FULL_CONVERSATION_VALUE, true))
      case SILENT_MODE =>
        Seq(RingTone, PingTone, TextTone).foreach(setUserPref(_, "silent"))
      case TRACKING_ID_INTENT =>
        try {
          val wireApplication = context.getApplicationContext.asInstanceOf[WireApplication]
          implicit val injector = wireApplication.module
          val id = wireApplication.inject[GlobalTrackingController].getId
          setResultData(id.toString)
          setResultCode(Activity.RESULT_OK)
        } catch {
          case _: Throwable =>
            setResultData("")
            setResultCode(Activity.RESULT_CANCELED)
        }
      case SELECT_STAGING_BE => updateStoredBackendConfig(context: Context, Backend.StagingBackend)
      case SELECT_QA_BE => updateStoredBackendConfig(context: Context, Backend.QaBackend)
      case SELECT_PROD_BE => updateStoredBackendConfig(context: Context, Backend.ProdBackend)
      case _ =>
        setResultData("Unknown Intent!")
        setResultCode(Activity.RESULT_CANCELED)
    }
  }

  private def updateStoredBackendConfig(context: Context, backendConfig: BackendConfig) = {
    // Note, the app must be terminated for this to work.
    val wireApplication = context.getApplicationContext.asInstanceOf[WireApplication]
    implicit val injector = wireApplication.module
    wireApplication.inject[BackendController].setStoredBackendConfig(backendConfig)
    setResultCode(Activity.RESULT_OK)
  }

}

object AbstractPreferenceReceiver {
  private val AUTO_ANSWER_CALL_INTENT_EXTRA_KEY = "AUTO_ANSWER_CALL_EXTRA_KEY"
  private val FULL_CONVERSATION_VALUE           = "FULL_CONVERSATION_VALUE"

  private val packageName = BuildConfig.APPLICATION_ID
  private val AUTO_ANSWER_CALL_INTENT  = packageName + ".intent.action.AUTO_ANSWER_CALL"
  private val ENABLE_GCM_INTENT        = packageName + ".intent.action.ENABLE_GCM"
  private val DISABLE_GCM_INTENT       = packageName + ".intent.action.DISABLE_GCM"
  private val ENABLE_TRACKING_INTENT   = packageName + ".intent.action.ENABLE_TRACKING"
  private val DISABLE_TRACKING_INTENT  = packageName + ".intent.action.DISABLE_TRACKING"
  private val SILENT_MODE              = packageName + ".intent.action.SILENT_MODE"
  private val TRACKING_ID_INTENT       = packageName + ".intent.action.TRACKING_ID"
  private val FULL_CONVERSATION_INTENT = packageName + ".intent.action.FULL_CONVERSATION_INTENT"
  private val HIDE_GDPR_POPUPS         = packageName + ".intent.action.HIDE_GDPR_POPUPS"
  private val SELECT_STAGING_BE        = packageName + ".intent.action.SELECT_STAGING_BE"
  private val SELECT_QA_BE             = packageName + ".intent.action.SELECT_QA_BE"
  private val SELECT_PROD_BE           = packageName + ".intent.action.SELECT_PROD_BE"

  private lazy val DeveloperAnalyticsEnabled = PrefKey[Boolean]("DEVELOPER_TRACKING_ENABLED")
}
