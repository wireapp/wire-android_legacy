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
package com.waz.zclient.tracking

import android.content.Context
import android.renderscript.RSRuntimeException
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.content.Preferences.PrefKey
import com.waz.content.{GlobalPreferences, UsersStorage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{UserId, _}
import com.waz.service.tracking.TrackingService.{NoReporting, track}
import com.waz.service.tracking._
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.threading.{SerialDispatchQueue, Threading}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.{RichThreetenBPDuration, _}
import com.waz.zclient._
import com.waz.zclient.appentry.fragments.SignInFragment
import com.waz.zclient.appentry.fragments.SignInFragment.{InputType, SignInMethod}
import com.waz.zclient.utils.DeprecationUtils
import net.hockeyapp.android.CrashManagerListener
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.concurrent.duration._
import scala.util.Try

class GlobalTrackingController(implicit inj: Injector, cxt: WireContext, eventContext: EventContext) extends Injectable {

  import GlobalTrackingController._

  private implicit val dispatcher = new SerialDispatchQueue(name = "Tracking")

  private val superProps = Signal(new JSONObject())

  //Create mixpanel object and set persistant super property values
  private val mixpanelGuard = returning(new MixpanelGuard(cxt)) { g =>
    g.open()
    g.withApi { m =>
      m.registerSuperPropertiesMap(Map(
        "app"     -> "android",
        "$city"   -> null.asInstanceOf[AnyRef],
        "$region" -> null.asInstanceOf[AnyRef]
      ).asJava)
    }
  }

  //For automation tests
  def getId = mixpanelGuard.withApi(_.getDistinctId).getOrElse("")

  val zmsOpt = inject[Signal[Option[ZMessaging]]]
  val zMessaging = inject[Signal[ZMessaging]]
  val currentConv = inject[Signal[ConversationData]]

  private def trackingEnabled = Future.successful(true)//ZMessaging.globalModule.flatMap(_.prefs.preference(analyticsPrefKey).apply())

  inject[UiLifeCycle].uiActive.onChanged {
    case false =>
      mixpanelGuard.withApi { m =>
        verbose("flushing mixpanel events")
        m.flush()
      }
    case _ =>
  }

  /**
    * Access tracking events when they become available and start processing
    * Sets super properties and actually performs the tracking of an event. Super properties are user scoped, so for that
    * reason, we need to ensure they're correctly set based on whatever account (zms) they were fired within.
    */
  ZMessaging.globalModule.map(_.trackingService.events).foreach {
    _ { case (zms, event) =>
      def send(zmsArg: Option[ZMessaging], eventArg: TrackingEvent) = {
        for {
          sProps <- superProps.head
          teamSize <- zmsArg match {
            case Some(z) => z.teamId.fold(Future.successful(0))(_ => z.teams.searchTeamMembers().head.map(_.size))
            case _ => Future.successful(0)
          }
        } yield {
          mixpanelGuard.withApi { m =>
            //clear account-based super properties
            m.unregisterSuperProperty(TeamInTeamSuperProperty)
            m.unregisterSuperProperty(TeamSizeSuperProperty)

            //set account-based super properties based on supplied zms
            sProps.put(TeamInTeamSuperProperty, zmsArg.flatMap(_.teamId).isDefined)
            sProps.put(TeamSizeSuperProperty, teamSize)

            //register the super properties, and track
            m.registerSuperProperties(sProps)
            verbose(s"tracking ${eventArg.name}")
            m.track(eventArg.name, eventArg.props.orNull)
          }
          verbose(
            s"""
               |trackEvent: ${eventArg.name}
               |properties: ${eventArg.props.map(_.toString(2))}
               |superProps: ${mixpanelGuard.withApi(_.getSuperProperties).getOrElse(sProps).toString(2)}
          """.stripMargin)
        }
      }

      event match {
        case _: OpenedTeamRegistration =>
          trackingEnabled.map {
            case true => ZMessaging.currentAccounts.accountManagers.head.map {
              _.size match {
                case 0 =>
                  send(zms, event)
                case _ =>
                  send(zms, OpenedTeamRegistrationFromProfile())
              }
            }
            case _ =>
          }
        case e: LoggedOutEvent if e.reason == LoggedOutEvent.InvalidCredentials =>
          //This event type is trigged a lot, so disable for now
        case _: MissedPushEvent if !BuildConfig.FLAVOR.equals("internal") =>
          //This event is high volume, so we limit it to only internal clients
        case e: ReceivedPushEvent if e.p.toFetch.forall(_.asScala < 10.seconds) =>
        //don't track - there are a lot of these events! We want to keep the event count lower
        case OptInEvent =>
          mixpanelGuard.open()
          mixpanelGuard.withApi { m =>
            verbose("Opted in to analytics, re-registering")
            m.unregisterSuperProperty(MixpanelIgnoreProperty)
          }
          send(zms, event).map { _ => mixpanelGuard.flush() }
        case OptOutEvent =>
          send(zms, event).map { _ =>
            mixpanelGuard.withApi { m =>
              verbose("Opted out of analytics, flushing and de-registering")
              m.registerSuperProperties(returning(new JSONObject()) { _.put(MixpanelIgnoreProperty, true) })
            }
            mixpanelGuard.close()
          }
        case e@ExceptionEvent(_, _, description, Some(throwable)) =>
          error(description, throwable)(e.tag)
          trackingEnabled.map {
            case true =>
              throwable match {
                case _: NoReporting =>
                case _ => saveException(throwable, description)(e.tag)
              }
            case _ => //no action
          }
        case _ =>
          trackingEnabled.map {
            case true => send(zms, event)
            case _ => //no action
          }
      }
    }
  }

  def onEnteredCredentials(response: Either[ErrorResponse, _], method: SignInMethod): Unit =
    track(EnteredCredentialsEvent(method, responseToErrorPair(response)), None)

  def onEnterCode(response: Either[ErrorResponse, Unit], method: SignInMethod): Unit =
    track(EnteredCodeEvent(method, responseToErrorPair(response)))

  def onRequestResendCode(response: Either[ErrorResponse, Unit], method: SignInMethod, isCall: Boolean): Unit =
    track(ResendVerificationEvent(method, isCall, responseToErrorPair(response)))

  def onAddNameOnRegistration(response: Either[ErrorResponse, Unit], inputType: InputType): Unit =
    for {
    //Should wait until a ZMS instance exists before firing the event
      _   <- ZMessaging.currentAccounts.activeZms.collect { case Some(z) => z }.head
      acc <- ZMessaging.currentAccounts.activeAccount.collect { case Some(acc) => acc }.head
    } yield {
      track(EnteredNameOnRegistrationEvent(inputType, responseToErrorPair(response)), Some(acc.id))
      track(RegistrationSuccessfulEvent(SignInFragment.Phone), Some(acc.id))
    }

  def flushEvents(): Unit = mixpanelGuard.flush()
}

object GlobalTrackingController {

  private def saveException(t: Throwable, description: String)(implicit tag: LogTag) = {
    t match {
      case _: RSRuntimeException => //
      case _ =>
        DeprecationUtils.saveException(t, new CrashManagerListener {
          override def shouldAutoUploadCrashes: Boolean = true
          override def getUserID: String = Try(ZMessaging.context.getSharedPreferences("zprefs", Context.MODE_PRIVATE).getString("com.waz.device.id", "???")).getOrElse("????")
          override def getDescription: String = s"zmessaging - $tag - $description"
        })
    }
  }

  private lazy val MixpanelIgnoreProperty = "$ignore"
  private lazy val TeamInTeamSuperProperty = "team.in_team"
  private lazy val TeamSizeSuperProperty = "team.size"

  val analyticsPrefKey = BuildConfig.APPLICATION_ID match {
    case "com.wire" | "com.wire.internal" => GlobalPreferences.AnalyticsEnabled
    case _ => PrefKey[Boolean]("DEVELOPER_TRACKING_ENABLED")
  }

  def isBot(conv: ConversationData, users: UsersStorage): Future[Boolean] =
    if (conv.convType == ConversationType.OneToOne) users.get(UserId(conv.id.str)).map(_.exists(_.isWireBot))(Threading.Background)
    else successful(false)

  def responseToErrorPair(response: Either[ErrorResponse, _]) = response.fold({ e => Option((e.code, e.label))}, _ => Option.empty[(Int, String)])

}
