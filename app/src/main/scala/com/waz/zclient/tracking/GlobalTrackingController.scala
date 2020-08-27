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

import java.util

import android.app.Activity
import android.content.Context
import android.renderscript.RSRuntimeException
import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{UserId, _}
import com.waz.service.{AccountManager, AccountsService, ZMessaging}
import com.waz.service.tracking.TrackingService.NoReporting
import com.waz.service.tracking._
import com.wire.signals.{EventContext, SerialDispatchQueue, Signal}
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.appentry.fragments.SignInFragment
import com.waz.zclient.appentry.fragments.SignInFragment.{InputType, SignInMethod}
import com.waz.zclient.log.LogUI._
import com.waz.content.UserPreferences.CountlyTrackingId
import com.waz.log.LogsService
import com.waz.utils.MathUtils
import com.waz.zclient.common.controllers.UserAccountsController
import ly.count.android.sdk.{Countly, CountlyConfig, DeviceId}

import scala.concurrent.Future
import scala.concurrent.Future._
import scala.util.Try

class GlobalTrackingController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext)
  extends Injectable with DerivedLogTag {
  import GlobalTrackingController._

  private implicit val dispatcher = new SerialDispatchQueue(name = "Tracking")

  //For automation tests
  def getId: String = ""

  private val tracking  = inject[TrackingService]
  private lazy val am = inject[Signal[AccountManager]]

  def initCountly(): Future[Unit] = {
    for {
      ap <- tracking.isTrackingEnabled.head if(ap)
      trackingId <- am.head.flatMap(_.storage.userPrefs(CountlyTrackingId).apply())
      logsEnabled <- inject[LogsService].logsEnabled
    } yield {
      verbose(l"Using countly Id: ${trackingId.str}")

      val config = new CountlyConfig(cxt, BuildConfig.COUNTLY_APP_KEY, BuildConfig.COUNTLY_SERVER_URL)
        .setLoggingEnabled(logsEnabled)
        .setIdMode(DeviceId.Type.DEVELOPER_SUPPLIED)
        .setDeviceId(trackingId.str)
        .setEventQueueSizeToSend(1)
        .setRecordAppStartTime(true)

      Countly.sharedInstance().init(config)
      setUserDataFields()
    }
  }

  def countlyOnStart(cxt: Activity): Future[Unit] = for {
    ap <- tracking.isTrackingEnabled.head if(ap)
  } yield Countly.sharedInstance().onStart(cxt)

  def countlyOnStop(): Future[Unit] = for {
   ap <- tracking.isTrackingEnabled.head if(ap)
  } yield Countly.sharedInstance().onStop()

  def optIn(): Future[Unit] = {
    verbose(l"optIn")
    for {
      _ <- initCountly()
      _ <- sendEvent(OptInEvent)
    } yield ()
  }

  def optOut(): Unit = dispatcher {
    verbose(l"optOut")
  }

  private def getSelfAccountType: Future[String] = {
    val accountsController = inject[UserAccountsController]
    for {
      isExternal <- accountsController.isExternal.head
      Some(isWireless) <- accountsController.currentUser.head.map(_.map(_.expiresAt.isDefined))
    } yield {
      if(isExternal) "external"
      else if(isWireless) "wireless"
      else "member"
    }
  }

  private def setUserDataFields(): Future[Unit] = {
    val accountsService = inject[AccountsService]
    for {
      Some(z) <- accountsService.activeZms.head
      teamMember = z.teamId.isDefined
      teamSize <- z.teamId.fold(Future.successful(0))(tId => z.usersStorage.getByTeam(Set(tId)).map(_.size))
      userAccountType <- getSelfAccountType
      contacts <- z.usersStorage.list().map(_.count(!_.isSelf))
    } yield {
      val predefinedFields = new util.HashMap[String, String]()
      val customFields = new util.HashMap[String, String]()
      customFields.put("user_contacts", MathUtils.logRound(contacts, 6).toString)
      customFields.put("team_team_id", teamMember.toString)
      customFields.put("team_team_size", teamSize.toString)
      customFields.put("team_user_type", userAccountType)
      Countly.userData.setUserData(predefinedFields, customFields)
      Countly.userData.save()
    }
  }

  import scala.collection.JavaConverters._
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
    case (_, event:ContributionEvent) => Countly.sharedInstance().events().recordEvent(event.name, event.segments.asJava)
    case (_, event:CallingEvent) => Countly.sharedInstance().events().recordEvent(event.name, event.segments.asJava)
    case (_, event:MessageDecryptionFailed) => Countly.sharedInstance().events().recordEvent(event.name, event.segments.asJava)
    case (_, event:ScreenShareEvent) => Countly.sharedInstance().events().recordEvent(event.name, event.segments.asJava)
    case (zms, event) => sendEvent(event, zms)
  }

  private def sendCountlyEvent(eventArg: ContributionEvent, zmsArg: Option[ZMessaging] = None) = {
    verbose(l"send countly event: $eventArg")
    Countly.sharedInstance().events().recordEvent(eventArg.name, eventArg.segments.asJava)
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
