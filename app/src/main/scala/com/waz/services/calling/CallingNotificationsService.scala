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
package com.waz.services.calling

import android.app.Service
import android.content.{Context, Intent}
import android.os.{Build, IBinder}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.waz.zclient.ServiceHelper
import com.waz.zclient.notifications.controllers.CallingNotificationsController
import com.waz.zclient.notifications.controllers.CallingNotificationsController.{CallNotification, NotificationAction, androidNotificationBuilder}

class CallingNotificationsService extends ServiceHelper with DerivedLogTag {
  private lazy val callNCtrl = inject[CallingNotificationsController]

  implicit lazy val cxt: Context = getApplicationContext

  private lazy val sub = callNCtrl.notifications.map(_.find(_.isMainCall)).onUi {
    case Some(not) if shouldShowNotification(not) =>
      val builder = androidNotificationBuilder(not, treatAsIncomingCall = isAndroid10OrAbove)
      startForeground(not.convId.str.hashCode, builder.build())
    case _ =>
      stopForeground(true)
      stopSelf()
  }

  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    super.onStartCommand(intent, flags, startId)
    sub
    Service.START_STICKY
  }

  // Since Android 10 we can't start the calling activity from the background, so instead we
  // show a calling notification.
  private val isAndroid10OrAbove: Boolean = Build.VERSION.SDK_INT >= 29
  private def isUiActive: Boolean = ZMessaging.currentGlobal.lifecycle.uiActive.currentValue.getOrElse(false)

  private def shouldShowNotification(notification: CallNotification): Boolean = {
    val notificationHasAction = notification.action != NotificationAction.Nothing
    notificationHasAction && !(isAndroid10OrAbove && isUiActive)
  }
}
