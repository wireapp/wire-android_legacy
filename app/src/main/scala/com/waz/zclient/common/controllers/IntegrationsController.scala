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
package com.waz.zclient.common.controllers

import android.content.Context
import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.sync.SyncResult
import com.waz.zclient.utils.{ConversationSignal, UiStorage}

import scala.concurrent.Future

class IntegrationsController(implicit injector: Injector, context: Context) extends Injectable {
  import Threading.Implicits.Background

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val integrations = inject[Signal[ZMessaging]].map(_.integrations)
  private implicit lazy val uiStorage = inject[UiStorage]

  lazy val userAccs = inject[UserAccountsController]

  val searchQuery = Signal[String]("")

  def searchIntegrations = for {
    in        <- integrations
    startWith <- searchQuery
    data      <- in.searchIntegrations(startWith).map(Option(_)).orElse(Signal.const(Option.empty[Seq[IntegrationData]]))
  } yield data.map(_.toIndexedSeq)

  def getIntegration(pId: ProviderId, iId: IntegrationId): Future[IntegrationData] =
    integrations.head.flatMap(_.getIntegration(pId, iId))

  def addBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[Either[ErrorResponse, Unit]] =
    integrations.head.flatMap(_.addBotToConversation(cId, pId, iId))

  def createConvWithBot(pId: ProviderId, iId: IntegrationId): Future[ConvId] = {
    for {
      zms <- zms.head
      (conv, syncId) <- zms.convsUi.createGroupConversation()
      _ = zms.syncRequests.scheduler.await(syncId).map {
        case SyncResult.Success =>
          addBot(conv.id, pId, iId).collect {
            case Left(error) => showToastError(error)
          } (Threading.Ui)
        case result =>
          showToastError(result.error.getOrElse(ErrorResponse.InternalError))
      } (Threading.Ui)
    } yield conv.id
  }

  def removeBot(cId: ConvId, userId: UserId): Future[Either[ErrorResponse, Unit]] =
    integrations.head.flatMap(_.removeBotFromConversation(cId, userId))

  def showToastError(error: ErrorResponse): Unit = showToast(errorMessage(error))(context)

  def hasPermissionToRemoveBot(cId: ConvId): Future[Boolean] = {
    for {
      tId <- userAccs.teamId.head
      ps  <- userAccs.selfPermissions.head
      conv <- ConversationSignal(cId).head
    } yield tId == conv.team && ps.contains(AccountDataOld.Permission.RemoveConversationMember)
  }

  def errorMessage(e: ErrorResponse): String =
    getString((e.code, e.label) match {
      case (403, "too-many-members") => R.string.conversation_errors_full
//      case (419, "too-many-bots")    => R.string.integrations_errors_add_service //TODO ???
      case (_, _)                    => R.string.integrations_errors_service_unavailable
    })
}

