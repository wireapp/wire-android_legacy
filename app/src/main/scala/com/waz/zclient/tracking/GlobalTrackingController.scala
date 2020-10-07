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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountManager, AccountsService, ZMessaging}
import com.waz.service.tracking._
import com.wire.signals.Signal
import com.waz.zclient._
import com.waz.zclient.log.LogUI._
import com.waz.content.UserPreferences.CountlyTrackingId
import com.waz.log.LogsService
import com.waz.model.TeamId
import com.waz.utils.MathUtils
import com.waz.zclient.common.controllers.UserAccountsController
import ly.count.android.sdk.{Countly, CountlyConfig, DeviceId}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class GlobalTrackingController(implicit inj: Injector, cxt: WireContext)
  extends Injectable with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private val tracking  = inject[TrackingService]
  private lazy val am = inject[Signal[AccountManager]]
  private lazy val accountsService = inject[AccountsService]

  //helps us fire the "app.open" event at the right time.
  private val initialized = Signal(false)

  initialized.onChanged { _ =>
    accountsService.activeAccount.foreach(_.foreach(user => tracking.appOpen(user.id)))
  }

  def init(): Future[Unit] = {
    for {
      ap               <- tracking.isTrackingEnabled.head if (ap)
      inited           <- initialized.head if (!inited)
      Some(trackingId) <- am.head.flatMap(_.storage.userPrefs(CountlyTrackingId).apply())
      logsEnabled      <- inject[LogsService].logsEnabled
    } yield {
      verbose(l"Using countly Id: ${trackingId.str}")

      val config = new CountlyConfig(cxt, GlobalTrackingController.countlyAppKey, BuildConfig.COUNTLY_SERVER_URL)
        .setLoggingEnabled(logsEnabled)
        .setIdMode(DeviceId.Type.DEVELOPER_SUPPLIED)
        .setDeviceId(trackingId.str)
        .setRecordAppStartTime(true)

      Countly.sharedInstance().init(config)
      setUserDataFields()
      initialized ! true
    }
  }

  def start(cxt: Activity): Future[Unit] = for {
    ap <- tracking.isTrackingEnabled.head if(ap)
  } yield Countly.sharedInstance().onStart(cxt)

  def stop(): Future[Unit] = for {
   ap <- tracking.isTrackingEnabled.head if(ap)
  } yield Countly.sharedInstance().onStop()

  def optIn(): Future[Unit] = {
    verbose(l"optIn")
    for {
      _ <- init()
    } yield ()
  }

  def optOut(): Unit = {
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
    for {
      Some(z)         <- accountsService.activeZms.head
      teamId          =  z.teamId.getOrElse(TeamId("n/a"))
      teamSize        <- z.teamId.fold(Future.successful(0))(tId => z.usersStorage.getByTeam(Set(tId)).map(_.size))
      userAccountType <- getSelfAccountType
      contacts        <- z.usersStorage.list().map(_.count(!_.isSelf))
    } yield {
      val predefinedFields = new util.HashMap[String, String]()
      val customFields = new util.HashMap[String, String]()
      customFields.put("user_contacts", MathUtils.logRoundFactor6(contacts).toString)
      customFields.put("team_team_id", teamId.toString)
      customFields.put("team_team_size", MathUtils.logRoundFactor6(teamSize).toString)
      customFields.put("team_user_type", userAccountType)
      Countly.userData.setUserData(predefinedFields, customFields)
      Countly.userData.save()
    }
  }

  /**
    * Access tracking events when they become available and start processing
    * Sets super properties and actually performs the tracking of an event. Super properties are user scoped, so for that
    * reason, we need to ensure they're correctly set based on whatever account (zms) they were fired within.
    */
  tracking.events.foreach { case (z, event) => sendEvent(event, z) }

  private def sendEvent(eventArg: TrackingEvent, zmsArg: Option[ZMessaging] = None) = {
    verbose(l"send countly event: $eventArg")
    Countly.sharedInstance().events().recordEvent(eventArg.name, eventArg.segments.asJava)
  }
}

object GlobalTrackingController {
  val internalCountlyAppKey = "18bfffddd3a2a89b6a70bbe6569cc041b17a52d2"
  val demoCountlyAppKey = "af153753b54e3e8365cd928d30f86b88c164a666"

  val countlyAppKey: String = BuildConfig.FLAVOR match {
    case "internal" => internalCountlyAppKey
    case "dev" => demoCountlyAppKey
    case _ => BuildConfig.COUNTLY_APP_KEY
  }

}
