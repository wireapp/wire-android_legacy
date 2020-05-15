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

package com.waz.zclient.tracking

import android.content.Context
import android.renderscript.RSRuntimeException
import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{UserId, _}
import com.waz.service.ZMessaging
import com.waz.service.tracking.TrackingService.NoReporting
import com.waz.service.tracking._
import com.waz.threading.{SerialDispatchQueue, Threading}
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.appentry.fragments.SignInFragment
import com.waz.zclient.appentry.fragments.SignInFragment.{InputType, SignInMethod}
import com.waz.zclient.log.LogUI._

import scala.concurrent.Future
import scala.concurrent.Future._
import scala.util.Try

class GlobalTrackingController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext)
  extends Injectable with DerivedLogTag {
  import GlobalTrackingController._

  private implicit val dispatcher = new SerialDispatchQueue(name = "Tracking")

  //For automation tests
  def getId: String = ""

  val tracking  = inject[TrackingService]

  def optIn(): Future[Unit] = {
    verbose(l"optIn")
    sendEvent(OptInEvent)
  }

  def optOut(): Unit = dispatcher {
    verbose(l"optOut")
  }

  /**
    * Access tracking events when they become available and start processing
    * Sets super properties and actually performs the tracking of an event. Super properties are user scoped, so for that
    * reason, we need to ensure they're correctly set based on whatever account (zms) they were fired within.
    */
  tracking.events.foreach {
    case (zms, event: OpenedTeamRegistration) =>
      ZMessaging.currentAccounts.accountManagers.head.map {
        case managers if managers.isEmpty => sendEvent(event, zms)
        case _                            => sendEvent(OpenedTeamRegistrationFromProfile(), zms)
      }
    case (_, event: LoggedOutEvent) if event.reason == LoggedOutEvent.InvalidCredentials =>
      //This event type is trigged a lot, so disable for now
    case (_, event@ExceptionEvent(_, _, description, Some(throwable))) =>
      error(l"description: ${redactedString(description)}", throwable)(event.tag)
      tracking.isTrackingEnabled.head.map {
        case true =>
          throwable match {
            case _: NoReporting =>
            case _              => saveException(throwable, description)(event.tag)
          }
        case _ => //no action
      }
    case (zms, event) => sendEvent(event, zms)
  }

  /**
    * @param eventArg the event to track
    * @param zmsArg a specific zms (account) to associate the event to. If none, we will try and use the current active one
    *
    * Right now it does nothing. The functionality stays here to be used when we implement a new tracking system
    */
  private def sendEvent(eventArg: TrackingEvent, zmsArg: Option[ZMessaging] = None) =
    Future.successful(verbose(l"send event: $eventArg"))

  def onEnteredCredentials(response: Either[ErrorResponse, _], method: SignInMethod): Unit =
    tracking.track(EnteredCredentialsEvent(method, responseToErrorPair(response)), None)

  def onEnterCode(response: Either[ErrorResponse, Unit], method: SignInMethod): Unit =
    tracking.track(EnteredCodeEvent(method, responseToErrorPair(response)))

  def onRequestResendCode(response: Either[ErrorResponse, Unit], method: SignInMethod, isCall: Boolean): Unit =
    tracking.track(ResendVerificationEvent(method, isCall, responseToErrorPair(response)))

  def onAddNameOnRegistration(response: Either[ErrorResponse, Unit], inputType: InputType): Unit =
    for {
    //Should wait until a ZMS instance exists before firing the event
      _   <- ZMessaging.currentAccounts.activeZms.collect { case Some(z) => z }.head
      acc <- ZMessaging.currentAccounts.activeAccount.collect { case Some(acc) => acc }.head
    } yield {
      tracking.track(EnteredNameOnRegistrationEvent(inputType, responseToErrorPair(response)), Some(acc.id))
      tracking.track(RegistrationSuccessfulEvent(SignInFragment.Phone), Some(acc.id))
    }

  def flushEvents(): Unit = {}
}

object GlobalTrackingController {

  private def saveException(t: Throwable, description: String)(implicit tag: LogTag) = {
    t match {
      case _: RSRuntimeException => //
      case _ =>
        val userId = Try(ZMessaging.context.getSharedPreferences("zprefs", Context.MODE_PRIVATE).getString("com.waz.device.id", "???")).getOrElse("????")
        error(l"userId: ${redactedString(userId)}", t)(tag)
    }
  }

  def isBot(conv: ConversationData, users: UsersStorage): Future[Boolean] =
    if (conv.convType == ConversationType.OneToOne) users.get(UserId(conv.id.str)).map(_.exists(_.isWireBot))(Threading.Background)
    else successful(false)

  def responseToErrorPair(response: Either[ErrorResponse, _]) = response.fold({ e => Option((e.code, e.label))}, _ => Option.empty[(Int, String)])

}
