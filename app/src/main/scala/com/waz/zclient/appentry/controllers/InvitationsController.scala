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
package com.waz.zclient.appentry.controllers

import android.content.Context
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.EmailAddress
import com.waz.service.AccountsService
import com.waz.sync.client.InvitationClient.ConfirmedTeamInvitation
import com.wire.signals.{CancellableFuture, Signal}
import com.waz.utils._
import com.waz.zclient.appentry.controllers.InvitationsController._
import com.waz.zclient.{Injectable, Injector}

import scala.collection.immutable.ListMap
import scala.concurrent.Future

class InvitationsController(implicit inj: Injector, context: Context)
  extends Injectable with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private lazy val accountsService      = inject[AccountsService]
  private lazy val createTeamController = inject[CreateTeamController]

  var inputEmail = ""

  val invitations: Signal[ListMap[EmailAddress, InvitationStatus]] = accountsService.activeAccountManager.flatMap {
    case None => Signal.const(ListMap.empty[EmailAddress, InvitationStatus])
    case Some(account) => account.invitedToTeam.map(_.map {
      case (inv, response) => inv.emailAddress -> InvitationStatus(response)
    })
  }

  def sendInvite(email: EmailAddress): Future[Either[ErrorResponse, Unit]] = {
    for {
      account     <- accountsService.activeAccountManager.head
      alreadySent <- invitations.head
      response <- if (alreadySent.keySet.contains(email))
          CancellableFuture.successful(Left(ErrorResponse(ErrorResponse.InternalErrorCode, "Already sent", "already-sent")))
        else
          account.fold2(CancellableFuture.successful(Left(ErrorResponse.internalError("No account manager available"))),
            _.inviteToTeam(email, Some(createTeamController.teamUserName)))
    } yield
      response match {
        case Left(e) => Left(e)
        case Right(_) => Right(())
      }
  }

  def inviteStatus(email: EmailAddress): Signal[InvitationStatus] = invitations.map(_.applyOrElse(email, (_: EmailAddress) => Failed))

}

object InvitationsController {
  trait InvitationStatus
  object Sending extends InvitationStatus
  object Sent extends InvitationStatus
  object Failed extends InvitationStatus
  object Accepted extends InvitationStatus

  object InvitationStatus {
    def apply(response: Option[Either[ErrorResponse, ConfirmedTeamInvitation]]): InvitationStatus = {
      response match {
        case Some(Left(_)) => Failed
        case Some(Right(_)) => Sent
        case None => Sending
      }
    }
  }
}
