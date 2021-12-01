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

package com.waz.services.websocket

import android.app._
import android.content
import android.content.{BroadcastReceiver, ComponentName, Context, Intent}
import android.os.{Build, IBinder}
import android.util.Log
import androidx.core.app.NotificationCompat
import com.waz.content.GlobalPreferences.{PushEnabledKey, WsForegroundKey}
import com.waz.jobs.PushTokenCheckJob
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountsService.InForeground
import com.waz.service.push.WSPushService
import com.waz.service.{AccountsService, GlobalModule, NetworkModeService}
import com.waz.services.websocket.WebSocketService.ForegroundNotificationChannelId
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient._
import com.waz.zclient.log.LogUI._
import com.wire.signals.Signal

final class WebSocketController(implicit inj: Injector) extends Injectable with DerivedLogTag {
  private lazy val global = inject[GlobalModule]
  private lazy val accounts = inject[AccountsService]

  private lazy val cloudPushAvailable =
    for {
      gpsAvailable   <- global.googleApi.isGooglePlayServicesAvailable
      devPrefEnabled <- global.prefs(PushEnabledKey).signal
    } yield gpsAvailable && devPrefEnabled

  lazy val accountWebsocketStates: Signal[(Set[WSPushService], Set[WSPushService])] =
    for {
      cloudPushAvailable                   <- cloudPushAvailable
      wsForegroundEnabled                  <- global.prefs(WsForegroundKey).signal
      accs                                 <- accounts.zmsInstances
      accsInFG                             <- Signal.sequence(accs.map(_.selfUserId).map(id => accounts.accountState(id).map(st => id -> st)).toSeq: _*).map(_.toMap)
      (zmsWithWSActive, zmsWithWSInactive) =  accs.partition(zms => wsForegroundEnabled || !cloudPushAvailable || accsInFG(zms.selfUserId) == InForeground)
    } yield (zmsWithWSActive.map(_.wsPushService), zmsWithWSInactive.map(_.wsPushService))

  lazy val serviceInForeground: Signal[Boolean] =
    for {
      uiActive       <- global.lifecycle.uiActive
      _ = verbose(l"uiActive: $uiActive")
      (zmsWithWS, _) <- accountWebsocketStates
    } yield !uiActive && zmsWithWS.nonEmpty

  private lazy val anyPushServiceConnected =
    accounts.zmsInstances.flatMap(zs =>
      Signal.sequence(zs.map(_.wsPushService.connected).toSeq: _ *).map(_.exists(!identity(_)))
    )

  // It's not important to get this id exactly right every time, but it's important to do it fast,
  // hence `.currentValue` instead of `.head`
  def notificationTitleId: Int = {
    val online = global.network.isOnline.currentValue.getOrElse(true)
    val pushServiceConnected = anyPushServiceConnected.currentValue.getOrElse(false)
    (online, pushServiceConnected) match {
      case (true, true) => R.string.ws_foreground_notification_connecting_title
      case (true, false) => R.string.ws_foreground_notification_connected_title
      case (false, _) => R.string.ws_foreground_notification_no_internet_title
    }
  }
}

/**
  * Receiver called on boot or when app is updated.
  */
class OnBootAndUpdateBroadcastReceiver extends BroadcastReceiver with DerivedLogTag {

  private val TAG = this.getClass.getName

  private var context: Context = _

  override def onReceive(context: Context, intent: Intent): Unit = {
    this.context = context
    Log.i(TAG, s"onReceive ${intent.getDataString}")

    if (WireApplication.ensureInitialized())
      Option(context.getApplicationContext.asInstanceOf[WireApplication].module).foreach { injector =>
        injector.binding[AccountsService] match {
          case Some(accounts) =>
            Log.i(TAG, "AccountsService loaded")
            accounts().zmsInstances.head.foreach { zs =>
              zs.map(_.selfUserId).foreach(PushTokenCheckJob(_))
            }(Threading.Background)

          case _ =>
            Log.e(TAG, "Failed to load AccountsService")
        }

        injector.binding[WebSocketController] match {
          case Some(controller) =>
            Log.i(TAG, s"WebSocketController loaded")
            controller().serviceInForeground.head.foreach {
              case true =>
                Log.i(TAG, s"startForegroundService")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                  context.startForegroundService(new Intent(context, classOf[WebSocketService]))
                else
                  WebSocketService(context)
              case false =>
                Log.i(TAG, s"foreground service not needed, will wait for application to start service if necessary")
            }(Threading.Ui)

          case None =>
            Log.e(TAG, s"Failed to load WebSocketController")
        }
      }
  }

}


/**
  * Service keeping the process running as long as web socket should be connected.
  */
final class WebSocketService extends ServiceHelper with DerivedLogTag {
  private implicit def context: Context = getApplicationContext

  private lazy val launchIntent        = PendingIntent.getActivity(context, 1, Intents.ShowAdvancedSettingsIntent, 0)

  private lazy val controller          = inject[WebSocketController]
  private lazy val global              = inject[GlobalModule]

  private lazy val webSocketActiveSubscription =
    Signal.zip(controller.accountWebsocketStates, global.network.networkMode).foreach {
      case ((zmsWithWSActive, zmsWithWSInactive), networkMode) if NetworkModeService.isOnlineMode(networkMode) =>
        toggleWSPushServices(zmsWithWSActive, zmsWithWSInactive, stopIfNeeded = true)
      case ((zmsWithWSActive, zmsWithWSInactive), _) =>
        toggleWSPushServices(Set.empty, zmsWithWSActive ++ zmsWithWSInactive)
    }

  private def toggleWSPushServices(activate:     Set[WSPushService],
                                   deactivate:   Set[WSPushService],
                                   stopIfNeeded: Boolean = false): Unit = {
    activate.foreach(_.activate())
    deactivate.foreach(_.deactivate())
    if (stopIfNeeded && activate.isEmpty) stopSelf()
  }

  private lazy val notificationBuilder =
    new NotificationCompat.Builder(this, ForegroundNotificationChannelId)
      .setSmallIcon(R.drawable.websocket)
      .setContentIntent(launchIntent)
      .setStyle(new NotificationCompat.BigTextStyle()
      .bigText(getString(R.string.ws_foreground_notification_summary)))
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)

  private def startForeground(title: String): Unit = {
    verbose(l"startForeground($title)")
    startForeground(WebSocketService.ForegroundId, notificationBuilder.setContentTitle(title).build())
  }

  private lazy val appInForegroundSubscription =
    controller.serviceInForeground.foreach {
      case false =>
        notificationChannel
        stopForeground(true)
      case true =>
        notificationChannel
        startForeground(getString(controller.notificationTitleId))
    }(Threading.Ui)

  override def onBind(intent: content.Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    verbose(l"onStartCommand(${RichIntent(intent)}, $startId)")

    Service.START_STICKY
  }

  override def onCreate(): Unit = {
    super.onCreate()

    verbose(l"onCreate")

    webSocketActiveSubscription
    appInForegroundSubscription
    notificationBuilder
    notificationChannel
  }

  // this is the same code as in WireApplication.notificationChannel
  // but we don't want to access it from here and the other way around as well
  lazy val notificationChannel: Option[NotificationChannel] =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      Some(
        returning(
          new NotificationChannel(
            ForegroundNotificationChannelId,
            getString(R.string.foreground_service_notification_name),
            NotificationManager.IMPORTANCE_LOW)
        ) { ch =>
          ch.setDescription(getString(R.string.foreground_service_notification_description))
          ch.enableVibration(false)
          ch.setShowBadge(false)
          ch.setSound(null, null)
          inject[NotificationManager].createNotificationChannel(ch)
        }
      )
    else None
}

object WebSocketService {
  val ForegroundId = 41235
  val ForegroundNotificationChannelId = "FOREGROUND_NOTIFICATION_CHANNEL_ID"

  def apply(context: Context): ComponentName = context.startService(new Intent(context, classOf[WebSocketService]))
}
