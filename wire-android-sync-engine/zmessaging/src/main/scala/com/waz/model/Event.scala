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
package com.waz.model

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.log.LogSE._
import com.waz.model.ConversationEvent.ConversationEventDecoder
import com.waz.model.Event.EventDecoder
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.PropertyKey
import com.waz.service.conversation.FoldersService.FoldersProperty
import com.waz.service.conversation.RemoteFolderData
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.OtrClient
import com.waz.utils.JsonDecoder._
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{JsonDecoder, JsonEncoder, _}
import org.json.{JSONException, JSONObject}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

sealed trait Event {

  //FIXME do we still need this separation?
  var localTime: LocalInstant = LocalInstant.Epoch

  def withCurrentLocalTime(): this.type = {
    localTime = LocalInstant.Now
    this
  }

  def withLocalTime(time: LocalInstant): this.type = {
    localTime = time
    this
  }

  def maybeLocalTime: Option[LocalInstant] = if (localTime.isEpoch) None else Some(localTime)
}

sealed trait UserEvent extends Event
sealed trait OtrClientEvent extends UserEvent

sealed trait RConvEvent extends Event {
  val convId: RConvId
}
object RConvEvent extends (Event => RConvId) {
  def apply(ev: Event): RConvId = ev match {
    case ev: RConvEvent => ev.convId
    case _              => RConvId.Empty
  }
}
case class UserUpdateEvent(user: UserInfo, removeIdentity: Boolean = false) extends UserEvent
case class UserConnectionEvent(convId:       RConvId,
                               from:         UserId,
                               to:           UserId,
                               message:      Option[String],
                               status:       ConnectionStatus,
                               lastUpdated:  RemoteInstant,
                               fromUserName: Option[Name] = None
                              ) extends UserEvent with RConvEvent
case class UserDeleteEvent(user: UserId) extends UserEvent
case class OtrClientAddEvent(client: Client) extends OtrClientEvent
case class OtrClientRemoveEvent(client: ClientId) extends OtrClientEvent

case class PushTokenRemoveEvent(token: PushToken, senderId: String, client: Option[String]) extends Event

sealed trait ConversationEvent extends RConvEvent {
  val time: RemoteInstant
  val from: UserId
}

// events that affect conversation state
sealed trait ConversationStateEvent extends ConversationEvent

// events that add or modify some message
sealed trait MessageEvent extends ConversationEvent

case class UnknownEvent(json: JSONObject) extends Event
case class UnknownConvEvent(json: JSONObject) extends ConversationEvent {
  override val convId: RConvId = RConvId()
  override val from: UserId = UserId()
  override val time: RemoteInstant = RemoteInstant.Epoch //TODO: epoch?
}

case class CreateConversationEvent(convId: RConvId, time: RemoteInstant, from: UserId, data: ConversationResponse) extends ConversationStateEvent

case class DeleteConversationEvent(convId: RConvId, time: RemoteInstant, from: UserId) extends ConversationStateEvent

case class MessageTimerEvent(convId: RConvId, time: RemoteInstant, from: UserId, duration: Option[FiniteDuration]) extends MessageEvent with ConversationStateEvent

case class RenameConversationEvent(convId: RConvId, time: RemoteInstant, from: UserId, name: Name) extends MessageEvent with ConversationStateEvent

case class GenericMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, content: GenericMessage) extends MessageEvent

case class CallMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, sender: ClientId, content: String) extends MessageEvent

sealed trait OtrError
case object Duplicate extends OtrError
case class DecryptionError(msg: String, from: UserId, sender: ClientId) extends OtrError
case class IdentityChangedError(from: UserId, sender: ClientId) extends OtrError
case class UnknownOtrErrorEvent(json: JSONObject) extends OtrError

case class OtrErrorEvent(convId: RConvId, time: RemoteInstant, from: UserId, error: OtrError) extends MessageEvent

case class TypingEvent(convId: RConvId, time: RemoteInstant, from: UserId, isTyping: Boolean) extends ConversationEvent

case class MemberJoinEvent(convId: RConvId, time: RemoteInstant, from: UserId, userIds: Seq[UserId], users: Map[UserId, ConversationRole], firstEvent: Boolean = false)
  extends MessageEvent with ConversationStateEvent with ConversationEvent

case class MemberLeaveEvent(convId: RConvId, time: RemoteInstant, from: UserId, userIds: Seq[UserId]) extends MessageEvent with ConversationStateEvent

case class MemberUpdateEvent(convId: RConvId, time: RemoteInstant, from: UserId, state: ConversationState) extends ConversationStateEvent

case class ConversationReceiptModeEvent(convId: RConvId, time: RemoteInstant, from: UserId, receiptMode: Int) extends MessageEvent with ConversationStateEvent

case class ConnectRequestEvent(convId: RConvId, time: RemoteInstant, from: UserId, message: String, recipient: UserId, name: Name, email: Option[String]) extends MessageEvent with ConversationStateEvent

case class ConversationAccessEvent(convId: RConvId, time: RemoteInstant, from: UserId, access: Set[Access], accessRole: AccessRole) extends ConversationStateEvent
case class ConversationCodeUpdateEvent(convId: RConvId, time: RemoteInstant, from: UserId, link: ConversationData.Link) extends ConversationStateEvent
case class ConversationCodeDeleteEvent(convId: RConvId, time: RemoteInstant, from: UserId) extends ConversationStateEvent

sealed trait OtrEvent extends ConversationEvent {
  val sender: ClientId
  val recipient: ClientId
  val ciphertext: Array[Byte]
}
case class OtrMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, sender: ClientId, recipient: ClientId, ciphertext: Array[Byte], externalData: Option[Array[Byte]] = None) extends OtrEvent

sealed trait PropertyEvent extends UserEvent

case class ReadReceiptEnabledPropertyEvent(value: Int) extends PropertyEvent

// An event that contains a new folders/favorites list
case class FoldersEvent(folders: Seq[RemoteFolderData]) extends PropertyEvent

case class UnknownPropertyEvent(key: PropertyKey, value: String) extends PropertyEvent

case class ConversationState(archived:         Option[Boolean] = None,
                             archiveTime:      Option[RemoteInstant] = None,
                             muted:            Option[Boolean] = None,
                             muteTime:         Option[RemoteInstant] = None,
                             mutedStatus:      Option[Int] = None,
                             target:           Option[UserId] = None,
                             conversationRole: Option[ConversationRole] = None
                            ) extends SafeToLog

object ConversationState {
  private def encode(state: ConversationState, o: JSONObject) = {
    state.archived foreach { o.put("otr_archived", _) }
    state.archiveTime foreach { time =>
      o.put("otr_archived_ref", JsonEncoder.encodeISOInstant(time.instant))
    }
    state.muted.foreach(o.put("otr_muted", _))

    state.muteTime foreach { time =>
      o.put("otr_muted_ref", JsonEncoder.encodeISOInstant(time.instant))
    }
    state.mutedStatus.foreach { status => o.put("otr_muted_status", status) }
    state.target.foreach { id => o.put("target", id) }
    state.conversationRole.foreach { role => o.put("conversation_role", role) }
  }

  implicit lazy val Encoder: JsonEncoder[ConversationState] = new JsonEncoder[ConversationState] {
    override def apply(state: ConversationState): JSONObject = JsonEncoder { o => encode(state, o) }
  }

  implicit lazy val Decoder: JsonDecoder[ConversationState] = new JsonDecoder[ConversationState] {
    import com.waz.utils.JsonDecoder._

    override def apply(implicit js: JSONObject): ConversationState = {
      val archiveTime = decodeOptISOInstant('otr_archived_ref).map(RemoteInstant(_))
      val archived = archiveTime.map( _ => decodeBool('otr_archived))

      val (muted, muteTime) = (decodeOptISOInstant('otr_muted_ref).map(RemoteInstant(_)),
        decodeOptISOInstant('muted_time).map(RemoteInstant(_))) match {
        case (Some(t), Some(t1)) if t1.isAfter(t) => (decodeOptBoolean('muted), Some(t1))
        case (t @ Some(_), _)                     => (decodeOptBoolean('otr_muted), t)
        case (_, t @ Some(_))                     => (decodeOptBoolean('muted), t)
        case _                                    => (None, None)
      }

      val mutedStatus = decodeOptInt('otr_muted_status)

      val target = decodeOptId[UserId]('target).orElse(decodeOptId[UserId]('id))
      val conversationRole = decodeOptConversationRole('conversation_role)

      ConversationState(archived, archiveTime, muted, muteTime, mutedStatus, target, conversationRole)
    }
  }

}

object Event {

  implicit object EventDecoder extends JsonDecoder[Event] with DerivedLogTag {

    import com.waz.utils.JsonDecoder._

    def connectionEvent(implicit js: JSONObject, name: Option[Name]) = UserConnectionEvent('conversation, 'from, 'to, 'message, ConnectionStatus('status), JsonDecoder.decodeISORemoteInstant('last_update), fromUserName = name)

    def gcmTokenRemoveEvent(implicit js: JSONObject) = PushTokenRemoveEvent(token = 'token, senderId = 'app, client = 'client)

    override def apply(implicit js: JSONObject): Event = Try {

      decodeString('type) match {
        case tpe if tpe.startsWith("conversation") => ConversationEventDecoder(js)
        case tpe if tpe.startsWith("team")         => TeamEvent.TeamEventDecoder(js)
        case "user.update" => UserUpdateEvent(JsonDecoder[UserInfo]('user))
        case "user.identity-remove" => UserUpdateEvent(JsonDecoder[UserInfo]('user), true)
        case "user.connection" => connectionEvent(js.getJSONObject("connection"), JsonDecoder.opt('user, _.getJSONObject("user")) flatMap (JsonDecoder.decodeOptName('name)(_)))
        case "user.push-remove" => gcmTokenRemoveEvent(js.getJSONObject("token"))
        case "user.delete" => UserDeleteEvent(user = 'id)
        case "user.client-add" => OtrClientAddEvent(OtrClient.ClientsResponse.client(js.getJSONObject("client")))
        case "user.client-remove" => OtrClientRemoveEvent(decodeId[ClientId]('id)(js.getJSONObject("client"), implicitly))
        case "user.properties-set" => PropertyEvent.Decoder(js)
        case "user.properties-delete" => PropertyEvent.Decoder(js)
        case _ =>
          error(l"unhandled event: $js")
          UnknownEvent(js)
      }
    } .getOrElse(UnknownEvent(js))
  }
}

object UserConnectionEvent {
  implicit lazy val Decoder: JsonDecoder[UserConnectionEvent] = new JsonDecoder[UserConnectionEvent] {
    override def apply(implicit js: JSONObject): UserConnectionEvent = EventDecoder.connectionEvent(js, name = None)
  }
}

object ConversationEvent extends DerivedLogTag {

  import OtrErrorEvent._
  import ConversationRole.decodeUserIdsWithRoles

  def unapply(e: ConversationEvent): Option[(RConvId, RemoteInstant, UserId)] =
    Some((e.convId, e.time, e.from))

  implicit lazy val ConversationEventDecoder: JsonDecoder[ConversationEvent] = new JsonDecoder[ConversationEvent] {
    override def apply(implicit js: JSONObject): ConversationEvent = Try {

      lazy val d = if (js.has("data") && !js.isNull("data")) Try(js.getJSONObject("data")).toOption else None

      val time = RemoteInstant(decodeISOInstant('time))

      decodeString('type) match {
        case "conversation.create"               => CreateConversationEvent('conversation, time, 'from, JsonDecoder[ConversationResponse]('data))
        case "conversation.delete"               => DeleteConversationEvent('conversation, time, 'from)
        case "conversation.rename"               => RenameConversationEvent('conversation, time, 'from, decodeName('name)(d.get))
        case "conversation.member-join"          =>
          MemberJoinEvent('conversation, time, 'from, decodeUserIdSeq('user_ids)(d.get), decodeUserIdsWithRoles('users)(d.get), decodeString('id).startsWith("1."))
        case "conversation.member-leave"         => MemberLeaveEvent('conversation, time, 'from, decodeUserIdSeq('user_ids)(d.get))
        case "conversation.member-update"        => MemberUpdateEvent('conversation, time, 'from, ConversationState.Decoder(d.get))
        case "conversation.connect-request"      => ConnectRequestEvent('conversation, time, 'from, decodeString('message)(d.get), decodeUserId('recipient)(d.get), decodeName('name)(d.get), decodeOptString('email)(d.get))
        case "conversation.typing"               => TypingEvent('conversation, time, 'from, isTyping = d.fold(false)(data => decodeString('status)(data) == "started"))
        case "conversation.otr-message-add"      => OtrMessageEvent('conversation, time, 'from, decodeClientId('sender)(d.get), decodeClientId('recipient)(d.get), decodeByteString('text)(d.get), decodeOptByteString('data)(d.get))
        case "conversation.access-update"        => ConversationAccessEvent('conversation, time, 'from, decodeAccess('access)(d.get), decodeAccessRole('access_role)(d.get))
        case "conversation.code-update"          => ConversationCodeUpdateEvent('conversation, time, 'from, ConversationData.Link(d.get.getString("uri")))
        case "conversation.code-delete"          => ConversationCodeDeleteEvent('conversation, time, 'from)
        case "conversation.receipt-mode-update"  => ConversationReceiptModeEvent('conversation, time, 'from, decodeInt('receipt_mode)(d.get))
        case "conversation.message-timer-update" => MessageTimerEvent('conversation, time, 'from, decodeOptLong('message_timer)(d.get).map(EphemeralDuration(_)))

          //Note, the following events are not from the backend, but are the result of decrypting and re-encoding conversation.otr-message-add events - hence the different name for `convId
        case "conversation.generic-message"      => GenericMessageEvent('convId, time, 'from, 'content)
        case "conversation.otr-error"            => OtrErrorEvent('convId, time, 'from, decodeOtrError('error))
        case _ =>
          error(l"unhandled event: $js")
          UnknownConvEvent(js)
      }
    } .getOrElse {
      error(l"unhandled event: $js")
      UnknownConvEvent(js)
    }
  }
}

object OtrErrorEvent extends DerivedLogTag {

  def decodeOtrError(s: Symbol)(implicit js: JSONObject): OtrError =
    OtrErrorDecoder(js.getJSONObject(s.name))

  implicit lazy val OtrErrorDecoder: JsonDecoder[OtrError] = new JsonDecoder[OtrError] {
    override def apply(implicit js: JSONObject): OtrError = Try {
      decodeString('type) match {
        case "otr-error.decryption-error" => DecryptionError('msg, 'from, 'sender)
        case "otr-error.identity-changed-error" => IdentityChangedError('from, 'sender)
        case "otr-error.duplicate" => Duplicate
        case _ =>
          error(l"unhandled event: $js")
          UnknownOtrErrorEvent(js)
      }
    }.getOrElse {
      error(l"unhandled event: $js")
      UnknownOtrErrorEvent(js)
    }
  }
}

object MessageEvent {
  import com.waz.utils._

  implicit lazy val MessageEventEncoder: JsonEncoder[MessageEvent] = new JsonEncoder[MessageEvent] {

    private def setFields(json: JSONObject, convId: RConvId, time: RemoteInstant, from: UserId, eventType: String) =
      json
        .put("convId", convId.str)
        .put("time", JsonEncoder.encodeISOInstant(time.instant))
        .put("from", from.str)
        .put("type", eventType)
        .setType(eventType)

    override def apply(event: MessageEvent): JSONObject = JsonEncoder { json =>
      event match {
        case GenericMessageEvent(convId, time, from, content) =>
          setFields(json, convId, time, from, "conversation.generic-message")
            .put("content", AESUtils.base64(GenericMessage.toByteArray(content)))
        case OtrErrorEvent(convId, time, from, error) =>
          setFields(json, convId, time, from, "conversation.otr-error")
            .put("error", OtrError.OtrErrorEncoder(error))
        case CallMessageEvent(convId, time, from, sender, content) =>
          setFields(json, convId, time, from, "conversation.call-message")
            .put("sender", sender.str)
            .put("content", content)
        case e => throw new JSONException(s"Encoder for event $e not implemented")
      }
    }
  }
}

object OtrError {
  import com.waz.utils._

  implicit lazy val OtrErrorEncoder: JsonEncoder[OtrError] = new JsonEncoder[OtrError] {
    override def apply(error: OtrError): JSONObject = JsonEncoder { json =>
      error match {
        case DecryptionError(msg, from, sender) =>
          json
            .put("msg", msg)
            .put("from", from.str)
            .put("sender", sender.str)
            .setType("otr-error.decryption-error")
        case IdentityChangedError(from, sender) =>
          json
            .put("from", from.str)
            .put("sender", sender.str)
            .setType("otr-error.identity-changed-error")
        case Duplicate => json.setType("otr-error.duplicate")
        case e => throw new JSONException(s"Encoder for event $e not implemented")
      }
    }
  }
}

sealed trait TeamEvent extends Event {
  val teamId: TeamId
}

object TeamEvent extends DerivedLogTag {

  /**
    * See: https://github.com/wireapp/architecture/blob/master/teams/backend.md
    */

  case class Update(teamId: TeamId, name: Option[Name], icon: AssetId) extends TeamEvent

  sealed trait MemberEvent extends TeamEvent {
    val userId: UserId
  }
  case class MemberLeave(teamId: TeamId, userId: UserId) extends MemberEvent
  case class MemberUpdate(teamId: TeamId, userId: UserId) extends MemberEvent

  sealed trait ConversationEvent extends TeamEvent {
    val convId: RConvId
  }

  case class UnknownTeamEvent(js: JSONObject) extends TeamEvent { override val teamId = TeamId.Empty }

  implicit lazy val TeamEventDecoder: JsonDecoder[TeamEvent] = new JsonDecoder[TeamEvent] {

    override def apply(implicit js: JSONObject): TeamEvent =
      decodeString('type) match {
        case "team.update"              => Update('team, decodeOptName('name)('data), AssetId(decodeString('icon)('data)))
        case "team.member-leave"        => MemberLeave('team, UserId(decodeString('user)('data)))
        case "team.member-update"       => MemberUpdate('team, UserId(decodeString('user)('data)))
        case _ =>
          warn(l"Unhandled/ignored event: $js")
          UnknownTeamEvent(js)
    }
  }
}

object OtrClientRemoveEvent {
  import com.waz.utils._
  implicit lazy val Encoder: JsonEncoder[OtrClientRemoveEvent] =
    new JsonEncoder[OtrClientRemoveEvent] {
      override def apply(error: OtrClientRemoveEvent): JSONObject = JsonEncoder { json =>
        json.setType("user.client-remove")
        json.put("client", new JSONObject().put("id", error.client.toString))
      }
    }
}

object PropertyEvent {
  lazy val Decoder: JsonDecoder[PropertyEvent] = new JsonDecoder[PropertyEvent] {
    override def apply(implicit js: JSONObject): PropertyEvent = {
      import PropertyKey._
      decodePropertyKey('key) match {
        case ReadReceiptsEnabled => decodeString('type) match {
          case "user.properties-set"    => ReadReceiptEnabledPropertyEvent('value)
          case "user.properties-delete" => ReadReceiptEnabledPropertyEvent(0)
          case e => UnknownPropertyEvent(ReadReceiptsEnabled, e)
        }
        case Folders => decodeString('type) match {
          case "user.properties-set"    => FoldersEvent(decode[FoldersProperty]('value).toRemote)
          case "user.properties-delete" => FoldersEvent(Seq.empty[RemoteFolderData])
          case e => UnknownPropertyEvent(Folders, e)
        }
        case key => UnknownPropertyEvent(key, 'value)
      }
    }
  }
}
