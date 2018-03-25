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
import com.waz.model.EmailAddress
import com.waz.service.ZMessaging
import com.waz.service.tracking.TrackingService._
import com.waz.sync.client.InvitationClient.ConfirmedTeamInvitation
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.appentry.controllers.InvitationsController._
import com.waz.zclient.tracking.TeamInviteSent
import com.waz.zclient.{Injectable, Injector}
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.Future

class InvitationsController(implicit inj: Injector, eventContext: EventContext, context: Context) extends Injectable {

  private val zms = inject[Signal[ZMessaging]]

  var inputEmail = ""

  val invitations = zms.flatMap(_.invitations.invitedToTeam).map(_.map {
    case (inv, response) => inv.emailAddress -> InvitationStatus(response)
  })

  def sendInvite(email: EmailAddress): Future[Either[ErrorResponse, Unit]] = {
    import com.waz.threading.Threading.Implicits.Background
    for {
      zms         <- zms.head
      account     <- zms.accounts.getActiveAccount
      alreadySent <- invitations.head
      name        <- zms.usersStorage.get(zms.selfUserId).map(_.map(_.name))
      response <- if (alreadySent.keySet.contains(email))
          CancellableFuture.successful(Left(ErrorResponse(ErrorResponse.InternalErrorCode, "Already sent", "already-sent")))
        else
          zms.invitations.inviteToTeam(email, name)
    } yield
      response match {
        case Left(e) => Left(e)
        case Right(_) =>
          track(TeamInviteSent(), account.map(_.id))
          Right(())
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
