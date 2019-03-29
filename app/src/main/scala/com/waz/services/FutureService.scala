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
package com.waz.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.content.WakefulBroadcastReceiver
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.WakeLockImpl
import com.waz.zclient.log.LogUI._
import com.waz.zclient.Intents.RichIntent

import scala.concurrent.Future
import scala.util.control.NoStackTrace

abstract class FutureService extends Service with DerivedLogTag {

  protected lazy val wakeLock = new WakeLockImpl(getApplicationContext)

  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = wakeLock {
    debug(l"onStartCommand: $startId, intent: ${RichIntent(intent)}")
    Option(intent) foreach WakefulBroadcastReceiver.completeWakefulIntent

    val future =
      if (intent == null) Future.successful({})
      else wakeLock async {
        onIntent(intent, startId).recover { case ex => error(l"onIntent failed", ex) } (Threading.Background)
      }
    future.onComplete { _ => onComplete(startId) }(Threading.Ui)

    Service.START_REDELIVER_INTENT
  }

  protected def onIntent(intent: Intent, id: Int): Future[Any]

  protected def onComplete(startId: Int): Unit = {
    debug(l"onCompleted: $startId")
    stopSelf(startId)
  }
}

trait ZMessagingService extends Service with DerivedLogTag {
  import Threading.Implicits.Background
  import ZMessagingService._

  private def accounts = ZMessaging.currentAccounts

  def onZmsIntent[Result](intent: Intent)(execute: ZMessaging => Future[Result]): Future[Result] =
    if (intent != null && intent.hasExtra(ZmsUserIdExtra)) {
      val userId = UserId(intent.getStringExtra(ZmsUserIdExtra))
      accounts.getZms(userId) flatMap {
        case Some(acc) => execute(acc)
        case None =>
          error(l"zmessaging not available")
          Future.failed(NoAccountException(userId))
      }
    } else {
      error(l"intent has no ZUserId extra")
      Future.failed(InvalidIntentException)
    }
}

object ZMessagingService {
  val ZmsUserIdExtra = "zms_user_id"

  case object InvalidIntentException extends Exception(s"Invalid ZMessagingService intent") with NoStackTrace
  case class NoZMessagingException(id: UserId) extends Exception(s"ZMessaging instance not available with id: $id") with NoStackTrace
  case class NoAccountException(id: UserId) extends Exception(s"AccountService instance not available with id: $id") with NoStackTrace
}
