/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zms

import android.app.{AlarmManager, PendingIntent, Service}
import android.content.{BroadcastReceiver, Context, Intent}
import android.support.v4.app.NotificationCompat
import android.support.v4.content.WakefulBroadcastReceiver
import android.support.v4.app.NotificationCompat._
import com.github.ghik.silencer.silent
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.WsForegroundKey
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.events.{ServiceEventContext, Signal}

import scala.concurrent.Future

/**
  * Receiver called on boot or when app is updated.
  */
class WebSocketBroadcastReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent): Unit = {
    debug(s"onReceive $intent")
    WakefulBroadcastReceiver.startWakefulService(context, new Intent(context, classOf[WebSocketService]))
  }
}


/**
  * Service keeping the process running as long as web socket should be connected.
  */
class WebSocketService extends FutureService with ServiceEventContext {
  import WebSocketService._
  private implicit def context = getApplicationContext
  private lazy val alarmService = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
  private lazy val restartIntent = PendingIntent.getService(context, 89426, new Intent(context, classOf[WebSocketService]), PendingIntent.FLAG_CANCEL_CURRENT)
  private lazy val launchIntent = PendingIntent.getActivity(context, 1, getPackageManager.getLaunchIntentForPackage(context.getPackageName), 0)

  val zmessaging = Signal.future(ZMessaging.accountsService).flatMap(_.activeZms)

//  val restartIntervals = for {
//    Some(zms) <- zmessaging
//    false <- zms.pushToken.pushActive
//    interval  <- zms.pingInterval.interval
//  } yield Option(interval)

  val notificationsState = for {
    Some(zms) <- zmessaging
    false <- zms.pushToken.pushActive
    true <- zms.prefs.preference(WsForegroundKey).signal // only when foreground service is enabled
    offline <- zms.network.networkMode.map(_ == NetworkMode.OFFLINE)
    connected <- zms.wsPushService.connected
//    error <- zms.websocket.connectionError
  } yield Option(
    if (offline) R.string.zms_websocket_connection_offline
    else if (connected) R.string.zms_websocket_connected
//    else if (error) R.string.zms_websocket_connection_failed
    else R.string.zms_websocket_connecting
  )

//  restartIntervals.orElse(Signal const None).onUi {
//    case Some(interval) =>
//      // schedule service restart every couple minutes to send ping on web socket (needed to keep connection alive)
//      verbose(s"scheduling restarts with interval: $interval")
//      alarmService.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval.toMillis, interval.toMillis, restartIntent)
//    case None =>
//      verbose("cancel restarts")
//      alarmService.cancel(restartIntent)
//  }

  notificationsState.orElse(Signal const None).onUi {
    case None =>
      verbose("stopForeground")
      stopForeground(true)
    case Some(state) =>
      verbose(s"startForeground $state")
      startForeground(ForegroundId, getNotificationCompatBuilder(context)
        .setSmallIcon(R.drawable.ic_menu_logo)
        .setContentTitle(context.getResources.getString(state))
        .setContentText(context.getResources.getString(R.string.zms_websocket_connection_info))
        .setContentIntent(launchIntent)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
      )
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = wakeLock {
    verbose(s"onStartCommand($intent, $startId)")

    onIntent(intent, startId).onComplete(_ => onComplete(startId))

    Option(intent) foreach WakefulBroadcastReceiver.completeWakefulIntent

    Service.START_STICKY
  }

  override protected def onIntent(intent: Intent, id: Int): Future[Any] = wakeLock async {
    zmessaging.head flatMap {
      case None =>
        warn("Current ZMessaging not available, stopping")
        Future successful None

      case Some(zms) =>
        // wait as long as web socket fallback is used, this keeps the wakeLock and service running
        zms.pushToken.pushActive.filter(identity).head
    }
  }

  @silent def getNotificationCompatBuilder(context: Context): Builder = new NotificationCompat.Builder(context)
}

object WebSocketService {
  val ForegroundId = 41235

  def apply(context: Context) = context.startService(new Intent(context, classOf[WebSocketService]))
}
