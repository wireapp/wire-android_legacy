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
package com.waz.service.tracking

import java.lang.Math.max

import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag
import com.waz.model.{IntegrationId, Mime}
import com.waz.service.call.Avs.AvsClosedReason
import com.waz.service.call.Avs.AvsClosedReason.reasonString
import com.waz.utils.returning
import org.json
import org.json.JSONObject
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait TrackingEvent {
  val name: String
  val props: Option[JSONObject] = None
}

object TrackingEvent {

  //Utility method for simple events
  def apply(eventName: String): TrackingEvent =
    new TrackingEvent { override val name = eventName }
}

trait OptEvent extends TrackingEvent {
  override val props = None
}

case object OptInEvent  extends OptEvent { override val name = s"settings.opted_in_tracking" }
case object OptOutEvent extends OptEvent { override val name = s"settings.opted_out_tracking" }

case class ContributionEvent(action:        ContributionEvent.Action,
                             isGroup:       Boolean,
                             ephExp:        Option[FiniteDuration],
                             withService:   Boolean,
                             guestsAllowed: Boolean,
                             fromGuest:     Boolean) extends TrackingEvent {
  override val name = "contributed"

  override val props = Some(returning(new JSONObject()) { o =>
    o.put("action", action.name)
    o.put("conversation_type", if (isGroup) "group" else "one_to_one")
    o.put("with_service", withService)
    o.put("is_ephemeral", ephExp.isDefined)
    o.put("ephemeral_expiration", ephExp.map(_.toSeconds.toString))
    o.put("is_allow_guests", guestsAllowed)
    o.put("user_type", if (fromGuest) "guest" else "user")
  })
}

object ContributionEvent {

  case class Action(name: String)

  object Action {
    lazy val Text = Action("text")
    lazy val Ping = Action("ping")
    lazy val AudioCall = Action("audio_call")
    lazy val VideoCall = Action("video_call")
    lazy val Photo = Action("photo")
    lazy val Audio = Action("audio")
    lazy val Video = Action("video")
    lazy val File = Action("file")
    lazy val Location = Action("location")
  }

  def fromMime(mime: Mime) = {
    import Action._
    mime match {
      case Mime.Image() => Photo
      case Mime.Audio() => Audio
      case Mime.Video() => Video
      case _ => File
    }
  }
}

// the throwable will not be serialized, but might be used to report an exception
trait ThrowableEvent extends TrackingEvent {
  def throwable: Option[Throwable]
}

//only for exceptions that actually crash the app
case class CrashEvent(crashType: String, crashDetails: String, override val throwable: Option[Throwable] = None) extends ThrowableEvent {
  override val name = "crash"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("crashType", crashType)
    o.put("crashDetails", crashDetails)
  })
}

// for all other exceptions
case class ExceptionEvent(exceptionType: String, exceptionDetails: String, description: String, override val throwable: Option[Throwable] = None)(implicit val tag: LogTag) extends ThrowableEvent {
  override val name = "debug.exception"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("exceptionType", exceptionType)
    o.put("exceptionDetails", exceptionDetails)
    o.put("description", description)
  })
}

case class MissedPushEvent(time:            Instant,
                           countMissed:     Int,
                           inBackground:    Boolean, //will help rull out false-positivie - missed pushes in foreground may be legitimate misses!
                           networkMode:     NetworkMode,
                           networkOperator: String,
                           eventTypes:      Map[String, Int],
                           lastEventId:     String) extends TrackingEvent {
  override val name = "debug.push_missed"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("time", time.toString)
    o.put("missed_count", countMissed)
    o.put("in_background", inBackground)
    o.put("network_mode", networkMode)
    eventTypes.foreach { case (e, f) => o.put(s"event.$e", f) }

    //TODO: remove before going into Prod
    o.put("last_not_id", lastEventId)
  })
}

case class LoggedOutEvent(reason: String) extends TrackingEvent {
  override val name = "account.logged_out"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("reason", reason)
  })
}

object LoggedOutEvent {
  val RemovedClient = "removed_client"
  val InvalidCredentials = "invalid_credentials"
  val SelfDeleted = "self_deleted"
  val ResetPassword = "reset_password"
  val Manual = "manual"
}

case class AVSMetricsEvent(jsonStr: String) extends TrackingEvent {
  override val name = "calling.avs_metrics_ended_call"
  override val props = Try(new json.JSONObject(jsonStr)).toOption
}

case class IntegrationAdded(integrationId: IntegrationId, convSize: Int, botsNumber: Int, method: IntegrationAdded.Method) extends TrackingEvent {
  override val name = "integration.added_service"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("service_id", integrationId.str)
    o.put("conversation_size", convSize)
    o.put("services_size", botsNumber)
    o.put("method", method.str)
  })
}

object IntegrationAdded {
  case class Method(str: String)
  object ConversationDetails extends Method("conversation_details")
  object StartUi extends Method("start_ui")
}

case class IntegrationRemoved(integrationId: IntegrationId) extends TrackingEvent {
  override val name = "integration.removed_service"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("service_id", integrationId.str)
  })
}

case class CreateGroupConversation(method: GroupConversationEvent.Method) extends TrackingEvent {
  override val name = "conversation.opened_group_creation"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("method", method.str)
  })
}

object GroupConversationEvent {
  case class Method(str: String)
  object ConversationDetails extends Method("conversation_details")
  object StartUi extends Method("start_ui")
}

case class OpenSelectParticipants(method: GroupConversationEvent.Method) extends TrackingEvent {
  override val name = "conversation.opened_select_participants"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("method", method.str)
  })
}

case class GroupConversationSuccessful(withParticipants: Boolean, guestsAllowed: Boolean, method: GroupConversationEvent.Method) extends TrackingEvent {
  override val name = "conversation.group_creation_succeeded"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("method", method.str)
    o.put("with_participants", withParticipants)
    o.put("is_allow_guests", guestsAllowed)
  })
}

case class GuestsAllowedToggled(guestsAllowed: Boolean) extends TrackingEvent {
  override val name = "guest_rooms.allow_guests"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("is_allow_guests", guestsAllowed)
  })
}

case class AddParticipantsEvent(guestsAllowed: Boolean, userCount: Int, guestCount: Int, method: GroupConversationEvent.Method) extends TrackingEvent {
  override val name = "conversation.add_participants"

  override val props = Some(returning(new JSONObject()) { o =>
    o.put("user_num", userCount)
    o.put("guest_num", guestCount)
    o.put("temporary_guest_num", 0) //TODO add when we have "wireless guests"
    o.put("is_allow_guests", guestsAllowed)
    o.put("method", method.str)
  })
}

class CallingEvent(partName:              String,
                   video:                 Boolean,
                   isGroup:               Boolean,
                   groupMemberCount:      Int,
                   withService:           Boolean,
                   incoming:              Boolean,
                   guestsAllowed:         Option[Boolean]         = None,
                   callParticipantsCount: Option[Int]             = None,
                   setupTime:             Option[Duration]        = None,
                   callDuration:          Option[Duration]        = None,
                   endReason:             Option[AvsClosedReason] = None,
                   videoAudioToggled:     Option[Boolean]         = None) extends TrackingEvent {

  override lazy val name = s"calling.${partName}_call"
  override val props = Some(returning(new JSONObject()) { o =>
    o.put("conversation_type", if (isGroup) "group" else "one_to_one")
    o.put("conversation_participants", groupMemberCount)
    o.put("with_service", withService)
    o.put("started_as_video", video)

    o.put("direction", if (incoming) "incoming" else "outgoing")

    guestsAllowed.foreach(v => o.put("is_allow_guests", v))
    callParticipantsCount.foreach(v => o.put("conversation_participants_in_call_max", v))
    callDuration.foreach(v => o.put("duration", v.getSeconds))
    setupTime.foreach(v => o.put("setup_time", v.getSeconds))
    endReason.foreach(v => o.put("reason", reasonString(v)))
    videoAudioToggled.foreach(v => o.put("AV_switch_toggled", v))
  })
}

case object HistoryBackupSucceeded extends TrackingEvent {
  override val name = "history.backup_succeeded"
}

case object HistoryBackupFailed extends TrackingEvent {
  override val name = "history.backup_failed"
}

case object HistoryRestoreSucceeded extends TrackingEvent {
  override val name = "history.restore_succeeded"
}

case object HistoryRestoreFailed extends TrackingEvent {
  override val name = "history.restore_failed"
}



