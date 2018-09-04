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
import android.app.{PendingIntent, Service}
import android.content.{BroadcastReceiver, Context, Intent}
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat._
import android.support.v4.content.WakefulBroadcastReceiver
import com.github.ghik.silencer.silent
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.WsForegroundKey
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.events.{ServiceEventContext, Signal}
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.ChannelId
import com.waz.zms.{FutureService, R}

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
  private lazy val launchIntent = PendingIntent.getActivity(context, 1, getPackageManager.getLaunchIntentForPackage(context.getPackageName), 0)

  val zmessaging = Signal.future(ZMessaging.accountsService).flatMap(_.activeZms)

  val notificationsState = for {
    Some(zms) <- zmessaging
    false <- zms.pushToken.pushActive
    true <- zms.prefs.preference(WsForegroundKey).signal // only when foreground service is enabled
    offline <- zms.network.networkMode.map(_ == NetworkMode.OFFLINE)
    connected <- zms.wsPushService.connected
  } yield Option(
    if (offline) R.string.zms_websocket_connection_offline
    else if (connected) R.string.zms_websocket_connected
    else R.string.zms_websocket_connecting
  )

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

/*  (for {
    Some(zms)    <- zmessaging
    networkMode  <- zms.network.networkMode
    accountState <- if (networkMode == NetworkMode.OFFLINE) Signal.const(LoggedOut)
                    else zms.accounts.accountState(zms.selfUserId)
    pushActive   <- if (accountState == LoggedOut) Signal.const(false) else zms.pushToken.pushActive
    wsActive     <- if (!pushActive) Signal.const(true)
                    else Signal.future(CancellableFuture.delayed(zms.timeouts.webSocket.inactivityTimeout)(false)).orElse(Signal const true)
  } yield (zms.wsPushService, zms.clientId, accountState, wsActive)) {
    case (wsPushService, clientId, accountState, true) =>
      debug(s"Active, client: $clientId")
      wsPushService.activate()
      if (accountState == InBackground) {
        // start android service to keep the app running while we need to be connected.
        com.waz.zms.WebSocketService(context)
      }
    case (wsPushService, _, _, _) =>
      debug(s"onInactive")
      wsPushService.deactivate()
  }*/

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

  @silent def getNotificationCompatBuilder(context: Context): Builder = new NotificationCompat.Builder(context, ChannelId)
}

object WebSocketService {
  val ForegroundId = 41235

  def apply(context: Context) = context.startService(new Intent(context, classOf[WebSocketService]))
}
