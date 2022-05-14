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

import com.waz.BuildConfig
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
import com.waz.sync.client.{EncodedEvent, OtrClient}
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
  val convDomain: Domain
}

object RConvEvent extends (Event => RConvId) {
  def apply(ev: Event): RConvId = ev match {
    case ev: RConvEvent => ev.convId
    case _              => RConvId.Empty
  }
}

final case class UserUpdateEvent(user: UserInfo, removeIdentity: Boolean = false) extends UserEvent
final case class UserConnectionEvent(convId:       RConvId,
                                     convDomain:   Domain,
                                     from:         UserId,
                                     to:           UserId,
                                     toDomain:     Domain,
                                     message:      Option[String],
                                     status:       ConnectionStatus,
                                     lastUpdated:  RemoteInstant,
                                     fromUserName: Option[Name] = None
                                    ) extends UserEvent with RConvEvent {
  def qualifiedConvId: Option[RConvQualifiedId] = convDomain.mapOpt(RConvQualifiedId(convId, _))
  def qualifiedTo: Option[QualifiedId] = toDomain.mapOpt(QualifiedId(to, _))
}
final case class UserDeleteEvent(user: UserId) extends UserEvent
final case class OtrClientAddEvent(client: Client) extends OtrClientEvent
final case class OtrClientRemoveEvent(client: ClientId) extends OtrClientEvent

final case class PushTokenRemoveEvent(token: PushToken, senderId: String, client: Option[String]) extends Event

sealed trait ConversationEvent extends RConvEvent {
  val time: RemoteInstant
  val from: UserId
  val fromDomain: Domain
}

// events that affect conversation state
sealed trait ConversationStateEvent extends ConversationEvent {
  val convId: RConvId
  val convDomain: Domain
}

// events that add or modify some message
sealed trait MessageEvent extends ConversationEvent

final case class UnknownEvent(json: JSONObject) extends Event
final case class UnknownConvEvent(json: JSONObject) extends ConversationEvent {
  override val convId: RConvId = RConvId()
  override val convDomain: Domain = Domain.Empty
  override val from: UserId = UserId()
  override val fromDomain: Domain = Domain.Empty
  override val time: RemoteInstant = RemoteInstant.Epoch //TODO: epoch?
}

final case class CreateConversationEvent(convId:     RConvId,
                                         convDomain: Domain,
                                         time:       RemoteInstant,
                                         from:       UserId,
                                         fromDomain: Domain,
                                         data:       ConversationResponse)
  extends ConversationStateEvent

final case class DeleteConversationEvent(convId:     RConvId,
                                         convDomain: Domain,
                                         time:       RemoteInstant,
                                         from:       UserId,
                                         fromDomain: Domain)
  extends ConversationStateEvent

final case class MessageTimerEvent(convId:     RConvId,
                                   convDomain: Domain,
                                   time:       RemoteInstant,
                                   from:       UserId,
                                   fromDomain: Domain,
                                   duration:   Option[FiniteDuration])
  extends MessageEvent with ConversationStateEvent

final case class RenameConversationEvent(convId:     RConvId,
                                         convDomain: Domain,
                                         time:       RemoteInstant,
                                         from:       UserId,
                                         fromDomain: Domain,
                                         name:       Name)
  extends MessageEvent with ConversationStateEvent

final case class GenericMessageEvent(convId:     RConvId,
                                     convDomain: Domain,
                                     time:       RemoteInstant,
                                     from:       UserId,
                                     fromDomain: Domain,
                                     content:    GenericMessage)
  extends MessageEvent

final case class CallMessageEvent(convId:     RConvId,
                                  convDomain: Domain,
                                  time:       RemoteInstant,
                                  from:       UserId,
                                  fromDomain: Domain,
                                  sender: ClientId,
                                  content: String)
  extends MessageEvent

sealed trait OtrError

case object Duplicate extends OtrError

final case class DecryptionError(msg:    String,
                                 code:   Option[Int],
                                 from:   UserId,
                                 sender: ClientId)
  extends OtrError

final case class IdentityChangedError(from: UserId, sender: ClientId) extends OtrError

final case class UnknownOtrErrorEvent(json: JSONObject) extends OtrError

final case class OtrErrorEvent(convId:     RConvId,
                               convDomain: Domain,
                               time:       RemoteInstant,
                               from:       UserId,
                               fromDomain: Domain,
                               error:      OtrError)
  extends MessageEvent

final case class SessionReset(convId:     RConvId,
                              convDomain: Domain,
                              time:       RemoteInstant,
                              from:       UserId,
                              fromDomain: Domain,
                              sender:     ClientId)
  extends MessageEvent

final case class TypingEvent(convId:     RConvId,
                             convDomain: Domain,
                             time:       RemoteInstant,
                             from:       UserId,
                             fromDomain: Domain,
                             isTyping:   Boolean)
  extends ConversationEvent

final case class MemberJoinEvent(convId:     RConvId,
                                 convDomain: Domain,
                                 time:       RemoteInstant,
                                 from:       UserId,
                                 fromDomain: Domain,
                                 userIds:    Seq[UserId],
                                 users:      Map[QualifiedId, ConversationRole],
                                 firstEvent: Boolean = false)
  extends MessageEvent with ConversationStateEvent

final case class MemberLeaveEvent(convId:     RConvId,
                                  convDomain: Domain,
                                  time:       RemoteInstant,
                                  from:       UserId,
                                  fromDomain: Domain,
                                  userIds:    Seq[UserId],
                                  reason:     Option[MemberLeaveReason])
  extends MessageEvent with ConversationStateEvent

final case class MemberLeaveReason(value: String) extends AnyVal
object MemberLeaveReason {
  val LegalHoldPolicyConflict = MemberLeaveReason("legalhold-policy-conflict")
}

final case class MemberUpdateEvent(convId:     RConvId,
                                   convDomain: Domain,
                                   time:       RemoteInstant,
                                   from:       UserId,
                                   fromDomain: Domain,
                                   state:      ConversationState)
  extends ConversationStateEvent

final case class ConversationReceiptModeEvent(convId:      RConvId,
                                              convDomain:  Domain,
                                              time:        RemoteInstant,
                                              from:        UserId,
                                              fromDomain:  Domain,
                                              receiptMode: Int)
  extends MessageEvent with ConversationStateEvent

final case class ConnectRequestEvent(convId:     RConvId,
                                     convDomain: Domain,
                                     time:       RemoteInstant,
                                     from:       UserId,
                                     fromDomain: Domain,
                                     message:    String,
                                     recipient:  UserId,
                                     name:       Name,
                                     email:      Option[String])
  extends MessageEvent with ConversationStateEvent

final case class ConversationAccessEvent(convId:     RConvId,
                                         convDomain: Domain,
                                         time:       RemoteInstant,
                                         from:       UserId,
                                         fromDomain: Domain,
                                         access:     Set[Access],
                                         accessRole: AccessRole)
  extends ConversationStateEvent

final case class ConversationCodeUpdateEvent(convId:     RConvId,
                                             convDomain: Domain,
                                             time:       RemoteInstant,
                                             from:       UserId,
                                             fromDomain: Domain,
                                             link:       ConversationData.Link)
  extends ConversationStateEvent

final case class ConversationCodeDeleteEvent(convId:     RConvId,
                                             convDomain: Domain,
                                             time:       RemoteInstant,
                                             from:       UserId,
                                             fromDomain: Domain)
  extends ConversationStateEvent

sealed trait OtrEvent extends ConversationEvent {
  val sender: ClientId
  val recipient: ClientId
  val ciphertext: Array[Byte]
}

final case class OtrMessageEvent(convId:       RConvId,
                                 convDomain:   Domain,
                                 time:         RemoteInstant,
                                 from:         UserId,
                                 fromDomain:   Domain,
                                 sender:       ClientId,
                                 recipient:    ClientId,
                                 ciphertext:   Array[Byte],
                                 externalData: Option[Array[Byte]] = None)
  extends OtrEvent

sealed trait PropertyEvent extends UserEvent

final case class ReadReceiptEnabledPropertyEvent(value: Int) extends PropertyEvent

// An event that contains a new folders/favorites list
final case class FoldersEvent(folders: Seq[RemoteFolderData]) extends PropertyEvent

final case class UnknownPropertyEvent(key: PropertyKey, value: String) extends PropertyEvent

final case class ConversationState(archived:         Option[Boolean] = None,
                                   archiveTime:      Option[RemoteInstant] = None,
                                   muted:            Option[Boolean] = None,
                                   muteTime:         Option[RemoteInstant] = None,
                                   mutedStatus:      Option[Int] = None,
                                   target:           Option[QualifiedId] = None,
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
    state.target.foreach { qId =>
      o.put("target", qId.id.str) // backward compatibility
      o.put("qualified_target", QualifiedId.Encoder(qId))
    }
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

      val target =
        QualifiedId.decodeOpt('qualified_target)
          .orElse(
            decodeOptId[UserId]('target)
              .orElse(decodeOptId[UserId]('id))
              .map(QualifiedId(_))
          )
      val conversationRole = decodeOptConversationRole('conversation_role)

      ConversationState(archived, archiveTime, muted, muteTime, mutedStatus, target, conversationRole)
    }
  }

}

object Event {
  def decodeRConvId(implicit js: JSONObject): (RConvId, Option[String]) = {
    val res = RConvQualifiedId.decodeOpt('qualified_conversation)
      .map(qId => (qId.id, if (qId.hasDomain) Some(qId.domain) else None))
      .getOrElse {
        if (js.has("convId")) (RConvId('convId), None)
        else (RConvId('conversation), None)
      }
    res
  }

  def decodeQUserId(nonQSymbol: Symbol, qSymbol: Symbol)(implicit js: JSONObject): (UserId, Option[String]) = {
    val res = QualifiedId.decodeOpt(qSymbol)
      .map(qId => (qId.id, if (qId.hasDomain) Some(qId.domain) else None))
      .getOrElse((UserId(nonQSymbol), None))
    res
  }

  implicit object EventDecoder extends JsonDecoder[Event] with DerivedLogTag {

    import com.waz.utils.JsonDecoder._

    def connectionEvent(implicit js: JSONObject, name: Option[Name]): UserConnectionEvent = {
      val (convId, convDomain) = Event.decodeRConvId
      val (to, toDomain) = Event.decodeQUserId('to, 'qualified_to)
      UserConnectionEvent(
        convId,
        Domain(convDomain),
        'from,
        to,
        Domain(toDomain),
        'message,
        ConnectionStatus('status),
        JsonDecoder.decodeISORemoteInstant('last_update),
        fromUserName = name
      )
    }

    def gcmTokenRemoveEvent(implicit js: JSONObject): PushTokenRemoveEvent =
      PushTokenRemoveEvent(token = 'token, senderId = 'app, client = 'client)

    override def apply(implicit js: JSONObject): Event = Try {
      decodeString('type) match {
        case tpe if tpe.startsWith("conversation") => ConversationEventDecoder(js)
        case tpe if tpe.startsWith("team")         => TeamEvent.TeamEventDecoder(js)
        case "user.update"                         => UserUpdateEvent(JsonDecoder[UserInfo]('user))
        case "user.identity-remove"                => UserUpdateEvent(JsonDecoder[UserInfo]('user), true)
        case "user.connection"                     => connectionEvent(js.getJSONObject("connection"), JsonDecoder.opt('user, _.getJSONObject("user")) flatMap (JsonDecoder.decodeOptName('name)(_)))
        case "user.push-remove"                    => gcmTokenRemoveEvent(js.getJSONObject("token"))
        case "user.delete"                         => UserDeleteEvent(user = 'id)
        case "user.client-add"                     => OtrClientAddEvent(OtrClient.ClientsResponse.Decoder(js.getJSONObject("client")))
        case "user.client-remove"                  => OtrClientRemoveEvent(decodeId[ClientId]('id)(js.getJSONObject("client"), implicitly))
        case "user.properties-set"                 => PropertyEvent.Decoder(js)
        case "user.properties-delete"              => PropertyEvent.Decoder(js)
        case "user.legalhold-request"              => LegalHoldRequestEvent(decodeId[UserId]('id), LegalHoldRequest.Decoder(js))
        case "user.legalhold-enable"               => LegalHoldEnableEvent(decodeId[UserId]('id))
        case "user.legalhold-disable"              => LegalHoldDisableEvent(decodeId[UserId]('id))
        case "feature-config.update"               => FeatureConfigUpdateEvent('name, 'data)
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
  import ConversationRole.decodeQualifiedIdsWithRoles

  def unapply(e: ConversationEvent): Option[(RConvId, RemoteInstant, UserId)] =
    Some((e.convId, e.time, e.from))

  implicit lazy val ConversationEventDecoder: JsonDecoder[ConversationEvent] = new JsonDecoder[ConversationEvent] {

    private def decodeMemberJoinEvent(data: JSONObject, time: RemoteInstant)(implicit js: JSONObject): MemberJoinEvent = {
      val (convId, convDomain) = Event.decodeRConvId
      val (from, fromDomain) = Event.decodeQUserId('from, 'qualified_from)

      MemberJoinEvent(
        convId,
        Domain(convDomain),
        time,
        from,
        Domain(fromDomain),
        decodeUserIdSeq('user_ids)(data),
        decodeQualifiedIdsWithRoles('users)(data),
        decodeString('id).startsWith("1.")
      )
    }

    override def apply(implicit js: JSONObject): ConversationEvent = Try {
      val (rConvId, cd) = Event.decodeRConvId
      val convDomain = Domain(cd)
      val (from, fd) = Event.decodeQUserId('from, 'qualified_from)
      val fromDomain = Domain(fd)
      lazy val d = if (js.has("data") && !js.isNull("data")) Try(js.getJSONObject("data")).toOption else None

      val time = RemoteInstant(decodeISOInstant('time))

      decodeString('type) match {
        case "conversation.create"               => CreateConversationEvent(rConvId, convDomain, time, from, fromDomain, JsonDecoder[ConversationResponse]('data))
        case "conversation.delete"               => DeleteConversationEvent(rConvId, convDomain, time, from, fromDomain)
        case "conversation.rename"               => RenameConversationEvent(rConvId, convDomain, time, from, fromDomain, decodeName('name)(d.get))
        case "conversation.member-join"          => decodeMemberJoinEvent(d.get, time)
        case "conversation.member-leave"         => MemberLeaveEvent(rConvId, convDomain, time, from, fromDomain, decodeUserIdSeq('user_ids)(d.get), decodeOptString('reason)(d.get).map(MemberLeaveReason(_)))
        case "conversation.member-update"        => MemberUpdateEvent(rConvId, convDomain, time, from, fromDomain, ConversationState.Decoder(d.get))
        case "conversation.connect-request"      => ConnectRequestEvent(rConvId, convDomain, time, from, fromDomain, decodeString('message)(d.get), decodeUserId('recipient)(d.get), decodeName('name)(d.get), decodeOptString('email)(d.get))
        case "conversation.typing"               => TypingEvent(rConvId, convDomain, time, from, fromDomain, isTyping = d.fold(false)(data => decodeString('status)(data) == "started"))
        case "conversation.otr-message-add"      => OtrMessageEvent(rConvId, convDomain, time, from, fromDomain, decodeClientId('sender)(d.get), decodeClientId('recipient)(d.get), decodeByteString('text)(d.get), decodeOptByteString('data)(d.get))
        case "conversation.access-update"        => ConversationAccessEvent(rConvId, convDomain, time, from, fromDomain, decodeAccess('access)(d.get), decodeAccessRole('access_role)(d.get))
        case "conversation.code-update"          => ConversationCodeUpdateEvent(rConvId, convDomain, time, from, fromDomain, ConversationData.Link(d.get.getString("uri")))
        case "conversation.code-delete"          => ConversationCodeDeleteEvent(rConvId, convDomain, time, from, fromDomain)
        case "conversation.receipt-mode-update"  => ConversationReceiptModeEvent(rConvId, convDomain, time, from, fromDomain, decodeInt('receipt_mode)(d.get))
        case "conversation.message-timer-update" => MessageTimerEvent(rConvId, convDomain, time, from, fromDomain, decodeOptLong('message_timer)(d.get).map(EphemeralDuration(_)))

          //Note, the following events are not from the backend, but are the result of decrypting and re-encoding conversation.otr-message-add events - hence the different name for `convId
        case "conversation.generic-message"      => GenericMessageEvent(rConvId, convDomain, time, from, fromDomain, 'content)
        case "conversation.otr-error"            => OtrErrorEvent(rConvId, convDomain, time, from, fromDomain, decodeOtrError('error))
        case "conversation.session-reset"        => SessionReset(rConvId, convDomain, time, from, fromDomain, 'sender)
        case _ =>
          error(l"unhandled event (1): ${js.toString}")
          UnknownConvEvent(js)
      }
    } .getOrElse {
      error(l"unhandled event (2): ${js.toString}")
      UnknownConvEvent(js)
    }
  }
}

object OtrErrorEvent extends DerivedLogTag {

  def decodeOtrError(s: Symbol)(implicit js: JSONObject): OtrError =
    OtrErrorDecoder(js.getJSONObject(s.name))

  implicit lazy val OtrErrorDecoder: JsonDecoder[OtrError] = new JsonDecoder[OtrError] {
    override def apply(implicit js: JSONObject): OtrError = Try {
      (decodeString('type), decodeOptInt('code)) match {
        case ("otr-error.decryption-error", code) =>
          DecryptionError('msg, code, 'from, 'sender)
        case ("otr-error.identity-changed-error", _) =>
          IdentityChangedError('from, 'sender)
        case ("otr-error.duplicate", _) => Duplicate
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

  def errorToEncodedEvent(error: OtrErrorEvent): EncodedEvent =
    EncodedEvent(MessageEventEncoder(error).toString)

  implicit lazy val MessageEventEncoder: JsonEncoder[MessageEvent] = new JsonEncoder[MessageEvent] {

    private val QualifiedEncoder: JsonEncoder[(String, String)] =
      JsonEncoder.build { case (id, domain) => js => {
        js.put("id", id)
        js.put("domain", domain)
      } }

    private def setFields(json: JSONObject,
                          convId: RConvId,
                          convDomain: Domain,
                          time: RemoteInstant,
                          from: UserId,
                          fromDomain: Domain,
                          eventType: String): JSONObject = {
      json
        .put("convId", convId.str)
        .put("time", JsonEncoder.encodeISOInstant(time.instant))
        .put("from", from.str)
        .put("type", eventType)
        .setType(eventType)
      if (convDomain.isDefined)
        json.put("qualified_conversation", QualifiedEncoder.apply((convId.str, convDomain.str)) )

      if (fromDomain.isDefined)
        json.put("qualified_from", QualifiedEncoder.apply((from.str, fromDomain.str)) )

      json
    }

    override def apply(event: MessageEvent): JSONObject = JsonEncoder { json =>
      event match {
        case GenericMessageEvent(convId, convDomain, time, from, fromDomain, content) =>
          setFields(json, convId, convDomain, time, from, fromDomain, "conversation.generic-message")
            .put("content", AESUtils.base64(content.proto.toByteArray))
        case OtrErrorEvent(convId, convDomain, time, from, fromDomain, error) =>
          setFields(json, convId, convDomain, time, from, fromDomain, "conversation.otr-error")
            .put("error", OtrError.OtrErrorEncoder(error))
        case CallMessageEvent(convId, convDomain, time, from, fromDomain, sender, content) =>
          setFields(json, convId, convDomain, time, from, fromDomain, "conversation.call-message")
            .put("sender", sender.str)
            .put("content", content)
        case SessionReset(convId, convDomain, time, from, fromDomain, sender) =>
          setFields(json, convId, convDomain, time, from, fromDomain, "conversation.session-reset")
            .put("sender", sender.str)
        case e => throw new JSONException(s"Encoder for event $e not implemented")
      }
    }
  }
}

object OtrError {
  import com.waz.utils._

  // artificial error codes; see CryptoException.Code for real ones
  val ERROR_CODE_SYMMETRIC_DECRYPTION_FAILED = 101
  val ERROR_CODE_DECRYPTION_OTHER = 102
  val ERROR_CODE_IDENTITY_CHANGED = 103

  implicit lazy val OtrErrorEncoder: JsonEncoder[OtrError] = new JsonEncoder[OtrError] {
    override def apply(error: OtrError): JSONObject = JsonEncoder { json =>
      error match {
        case DecryptionError(msg, Some(code), from, sender) =>
          json.put("msg", msg)
              .put("code", code)
              .put("from", from.str)
              .put("sender", sender.str)
              .setType("otr-error.decryption-error")
        case DecryptionError(msg, None, from, sender) =>
          json.put("msg", msg)
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
  case class MemberJoin(teamId: TeamId, userId: UserId) extends MemberEvent
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
        case "team.member-join"         => MemberJoin('team, UserId(decodeString('user)('data)))
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

sealed trait LegalHoldEvent extends UserEvent
final case class LegalHoldRequestEvent(userId: UserId, request: LegalHoldRequest) extends LegalHoldEvent
final case class LegalHoldEnableEvent(userId: UserId) extends LegalHoldEvent
final case class LegalHoldDisableEvent(userId: UserId) extends LegalHoldEvent

sealed trait FeatureConfigEvent extends Event
final case class FeatureConfigUpdateEvent(name: String, data: String) extends FeatureConfigEvent
