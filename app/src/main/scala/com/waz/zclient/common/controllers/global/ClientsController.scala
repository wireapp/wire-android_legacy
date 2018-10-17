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
package com.waz.zclient.common.controllers.global

import java.util.Locale

import android.content.Context
import com.waz.api.OtrClientType
import com.waz.model.{ConvId, UserId}
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.sync.SyncResult
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future

class ClientsController(implicit inj: Injector) extends Injectable {

  import com.waz.threading.Threading.Implicits.Background

  val accountManager = inject[Signal[AccountManager]]

  val zms            = inject[Signal[ZMessaging]]
  val convController = inject[ConversationController]
  val userAccounts   = inject[UserAccountsController]

  val self = accountManager.map(_.userId)

  val selfClientId = accountManager.flatMap(_.clientId)

  def client(userId: UserId, clientId: ClientId): Signal[Option[Client]] = for {
    manager <- accountManager
    clients <- manager.storage.otrClientsStorage.signal(userId)
  } yield clients.clients.get(clientId)

  def selfClient(clientId: ClientId): Signal[Option[Client]] = for {
    userId <- self
    client <- client(userId, clientId)
  } yield client

  def selfClient: Signal[Option[Client]] = selfClientId.flatMap {
    case Some(clientId) => selfClient(clientId)
    case None           => Signal.const(None)
  }

  def fingerprint(userId: UserId, clientId: ClientId): Signal[Option[String]] = for {
    manager <- accountManager
    fp      <- manager.fingerprintSignal(userId, clientId).map(_.map(new String(_))).orElse(Signal.const(None))
  } yield fp

  def selfFingerprint(clientId: ClientId): Signal[Option[String]] = for {
    userId <- self
    fp     <- fingerprint(userId, clientId)
  } yield fp

  def selfFingerprint: Signal[Option[String]] = selfClientId.flatMap {
    case Some(clientId) => selfFingerprint(clientId)
    case None           => Signal.const(None)
  }

  def isCurrentClient(clientId: ClientId): Signal[Boolean] = selfClientId.map(_.contains(clientId))

  def updateVerified(userId: UserId, clientId: ClientId, trusted: Boolean): Future[Option[(UserClients, UserClients)]] =
    accountManager.head.flatMap(_.storage.otrClientsStorage.updateVerified(userId, clientId, trusted))

  def resetSession(userId: UserId, clientId: ClientId): Future[SyncResult] = {
    (for {
      z    <- zms.head
      convId <- inject[Signal[Option[ConvId]]].head.flatMap {
        case Some(id) => Future.successful(id)
        case _ => userAccounts.getConversationId(userId)
      }
      syncId <- z.otrService.resetSession(convId, userId, clientId)
      resp   <- z.syncRequests.await(syncId)
    } yield resp)
      .recover {
        case e: Throwable => SyncResult.failed()
      }
  }

}

object ClientsController {

  private val BoldPrefix = "[["
  private val BoldSuffix = "]]"
  private val Separator = " "

  def getDeviceClassName(otrType: OtrClientType)(implicit context: Context): String = {
    import OtrClientType._
    getString(otrType match {
      case DESKTOP  => R.string.otr__participant__device_class__desktop
      case PHONE    => R.string.otr__participant__device_class__phone
      case TABLET   => R.string.otr__participant__device_class__tablet
      case _        => R.string.otr__participant__device_class__unknown
    })
  }

  def getFormattedFingerprint(fingerprint: String): String = {
    var currentChunkSize = 0
    var bold = true
    val sb = new StringBuilder

    (0 until fingerprint.length).foreach { i =>
      if (currentChunkSize == 0 && bold) sb.append(BoldPrefix)
      sb.append(fingerprint.charAt(i))
      currentChunkSize += 1
      if (currentChunkSize == 2 || i == fingerprint.length - 1) {
        if (bold) sb.append(BoldSuffix)
        bold = !bold
        if (i < fingerprint.length - 1) sb.append(Separator)
        currentChunkSize = 0
      }
    }
    sb.toString
  }

  def getDisplayId(id: ClientId) = f"${id.str.toUpperCase(Locale.ENGLISH)}%16s" replace (' ', '0') grouped 4 map { group =>
    val (bold, normal) = group.splitAt(2)
    s"[[$bold]] $normal"
  } mkString " "

  def getFormattedDisplayId(clientId: ClientId, color: Int)(implicit cxt: Context): CharSequence = {
    val text = getString(R.string.otr__device_id, getDisplayId(clientId))
    TextViewUtils.getBoldHighlightText(cxt, text, color, Math.max(0, text.indexOf(':') + 1), text.length)
  }
}
