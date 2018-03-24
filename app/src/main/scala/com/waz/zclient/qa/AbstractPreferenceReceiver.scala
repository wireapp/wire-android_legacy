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
package com.waz.zclient.qa

import android.app.Activity
import android.content.{BroadcastReceiver, Context, Intent}
import com.waz.content.GlobalPreferences._
import com.waz.content.Preferences.PrefKey
import com.waz.content.UserPreferences._
import com.waz.service.ZMessaging
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController._
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.controllers.userpreferences.UserPreferencesController._
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.{BuildConfig, WireApplication}
import com.waz.ZLog.ImplicitTag._

trait AbstractPreferenceReceiver extends BroadcastReceiver {

  import AbstractPreferenceReceiver._
  import com.waz.threading.Threading.Implicits.Background

  override def onReceive(context: Context, intent: Intent) = {
    val globalPrefs = ZMessaging.accountsService.map(_.global.prefs)
    intent.getAction match {
      case AUTO_ANSWER_CALL_INTENT =>
        globalPrefs.map(_.preference(AutoAnswerCallPrefKey) := intent.getBooleanExtra(AUTO_ANSWER_CALL_INTENT_EXTRA_KEY, false))
        setResultCode(Activity.RESULT_OK)
      case ENABLE_GCM_INTENT =>
        globalPrefs.map(_.preference(PushEnabledKey) := true)
        setResultCode(Activity.RESULT_OK)
      case DISABLE_GCM_INTENT =>
        globalPrefs.map(_.preference(PushEnabledKey) := false)
        setResultCode(Activity.RESULT_OK)
      case SILENT_MODE =>
        val accounts = ZMessaging.accountsService.map(_.zmsInstances)
        accounts.map(_.head.map(_.foreach { zms =>
          Seq(RingTone, PingTone, TextTone).map(zms.userPrefs.preference(_)).foreach(_ := "silent")
        }))
        setResultCode(Activity.RESULT_OK)
      case NO_CONTACT_SHARING =>
        val preferences = context.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)
        preferences.edit()
          .putBoolean(USER_PREF_ACTION_PREFIX + DO_NOT_SHOW_SHARE_CONTACTS_DIALOG, true)
          .apply()
        setResultCode(Activity.RESULT_OK)
      case TRACKING_ID_INTENT =>
        try {
          val wireApplication = context.getApplicationContext.asInstanceOf[WireApplication]
          implicit val injector = wireApplication.module
          val id = wireApplication.inject[GlobalTrackingController].getId
          setResultData(id)
          setResultCode(Activity.RESULT_OK)
        } catch {
          case _: Throwable =>
            setResultData("")
            setResultCode(Activity.RESULT_CANCELED)
        }
      case ENABLE_TRACKING_INTENT =>
        globalPrefs.map(_.preference(DeveloperAnalyticsEnabled) := true)
        setResultCode(Activity.RESULT_OK)
      case DISABLE_TRACKING_INTENT =>
        globalPrefs.map(_.preference(DeveloperAnalyticsEnabled) := false)
        setResultCode(Activity.RESULT_OK)
      case TEAM_CREATION_TOU_AB_INTENT =>
        val wireApplication = context.getApplicationContext.asInstanceOf[WireApplication]
        implicit val injector = wireApplication.module
        val appEntryController = wireApplication.inject[AppEntryController]
        //TODO coordinate remove of this block with QA
      case _ =>
        setResultData("Unknown Intent!")
        setResultCode(Activity.RESULT_CANCELED)
    }
  }

}

object AbstractPreferenceReceiver {
  val AUTO_ANSWER_CALL_INTENT_EXTRA_KEY = "AUTO_ANSWER_CALL_EXTRA_KEY"
  val packageName = BuildConfig.APPLICATION_ID
  val AUTO_ANSWER_CALL_INTENT = packageName + ".intent.action.AUTO_ANSWER_CALL"
  val ENABLE_GCM_INTENT = packageName + ".intent.action.ENABLE_GCM"
  val DISABLE_GCM_INTENT = packageName + ".intent.action.DISABLE_GCM"
  val ENABLE_TRACKING_INTENT = packageName + ".intent.action.ENABLE_TRACKING"
  val DISABLE_TRACKING_INTENT = packageName + ".intent.action.DISABLE_TRACKING"
  val SILENT_MODE = packageName + ".intent.action.SILENT_MODE"
  val NO_CONTACT_SHARING = packageName + ".intent.action.NO_CONTACT_SHARING"
  val TRACKING_ID_INTENT = packageName + ".intent.action.TRACKING_ID"

  val TEAM_CREATION_TOU_AB_INTENT = packageName + ".intent.action.TEAM_CREATION_TOS_AB"
  val TEAM_CREATION_TOU_AB_EXTRA_KEY = "SET_B_POSITION"

  lazy val DeveloperAnalyticsEnabled = PrefKey[Boolean]("DEVELOPER_TRACKING_ENABLED")
}
