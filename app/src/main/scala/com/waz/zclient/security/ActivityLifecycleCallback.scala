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
package com.waz.zclient.security

import android.app.{Activity, Application}
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{BuildConfig, Injectable, Injector, LaunchActivity}
import com.wire.signals._

import scala.collection.convert.DecorateAsScala

class ActivityLifecycleCallback(implicit injector: Injector)
  extends Application.ActivityLifecycleCallbacks
    with Injectable
    with DerivedLogTag
    with DecorateAsScala {

  private lazy val shouldHideScreenContent = for {
    prefs <- userPreferences
    hideScreenContent <- prefs.preference(UserPreferences.HideScreenContent).signal
  } yield hideScreenContent

  private val activitiesRunning = Signal[(Int, Option[Activity])]((0, None))

  protected lazy val userPreferences = inject[Signal[UserPreferences]]

  val appInBackground: Signal[(Boolean, Option[Activity])] = activitiesRunning.map { case (running, lastAct) => (running == 0, lastAct) }

  override def onActivityStopped(activity: Activity): Unit = synchronized {
    activity match {
      case _: LaunchActivity =>
      case _ =>
        verbose(l"onActivityStopped, activities still active: ${activitiesRunning.currentValue}, ${activity.getClass.getName}")
        activitiesRunning.mutate { case (running, _) => (running - 1, Option(activity)) }
    }
  }

  override def onActivityStarted(activity: Activity): Unit = synchronized {
    activity match {
      case _: LaunchActivity =>
      case _ =>
        verbose(l"onActivityStarted, activities active now: ${activitiesRunning.currentValue}, ${activity.getClass.getName}")
        activitiesRunning.mutate { case (running, _) => (running + 1, Option(activity)) }

    }
  }

  override def onActivityCreated(activity: Activity, bundle: Bundle): Unit = {}

  override def onActivityResumed(activity: Activity): Unit = {
    if (BuildConfig.FORCE_HIDE_SCREEN_CONTENT) {
      activity.getWindow.addFlags(FLAG_SECURE)
    } else {
      shouldHideScreenContent.onUi {
        case true  => activity.getWindow.addFlags(FLAG_SECURE)
        case false => activity.getWindow.clearFlags(FLAG_SECURE)
      }
    }
  }

  override def onActivityPaused(activity: Activity): Unit = {}

  override def onActivityDestroyed(activity: Activity): Unit = {}

  override def onActivitySaveInstanceState(activity: Activity, bundle: Bundle): Unit = {}
}
