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
package com.waz.model.sync

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.ClientId
import com.waz.model.{AccentColor, Availability, _}
import com.waz.service.assets.{Codec, StorageCodecs, UploadAssetStatus}
import com.waz.service.{PropertyKey, SearchQuery}
import com.waz.sync.client.{ConversationsClient, UsersClient}
import com.waz.sync.queue.SyncJobMerger._
import com.waz.utils._
import org.json.JSONObject

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

sealed abstract class SyncRequest {
  val cmd: SyncCommand

  /**
    * Note, having a the same mergeKey does not guarantee a merge. It only guarantees that merge will be called if the mergeKeys
    * match: merge may also need to be overridden.
    */
  def mergeKey: Any = cmd

  /**
    * Try merging, assuming that merge key is the same.
    */
  def merge(req: SyncRequest): MergeResult[SyncRequest] = if (this == req) Merged(this) else Unchanged

  /**
    * Checks if this requests is same or a part of given req.
    */
  def isDuplicateOf(req: SyncRequest): Boolean = this == req
}

object SyncRequest {

  sealed abstract class BaseRequest(val cmd: SyncCommand) extends SyncRequest
  sealed abstract class RequestForConversation(cmd: SyncCommand) extends BaseRequest(cmd) {
    val convId: ConvId
    override val mergeKey: Any = (cmd, convId)
  }

  sealed abstract class RequestForUser(cmd: SyncCommand) extends BaseRequest(cmd) {
    val userId: UserId
    override val mergeKey = (cmd, userId)
  }

  sealed trait Serialized extends SyncRequest

  import sync.{SyncCommand => Cmd}

  case object Unknown             extends BaseRequest(Cmd.Unknown)
  case object SyncSelf            extends BaseRequest(Cmd.SyncSelf)
  case object DeleteAccount       extends BaseRequest(Cmd.DeleteAccount)
  case object SyncConversations   extends BaseRequest(Cmd.SyncConversations)
  case object SyncConnections     extends BaseRequest(Cmd.SyncConnections)
  case object SyncSelfClients     extends BaseRequest(Cmd.SyncSelfClients)
  case object SyncSelfPermissions extends BaseRequest(Cmd.SyncSelfPermissions)
  case object SyncClientsLocation extends BaseRequest(Cmd.SyncClientLocation)
  case object SyncTeam            extends BaseRequest(Cmd.SyncTeam)
  case object SyncProperties      extends BaseRequest(Cmd.SyncProperties)
  case object PostFolders         extends BaseRequest(Cmd.PostFolders)
  case object SyncFolders         extends BaseRequest(Cmd.SyncFolders)

  case class SyncTeamMember(userId: UserId) extends BaseRequest(Cmd.SyncTeam) {
    override val mergeKey: Any = (cmd, userId)
  }

  case class PostSelf(data: UserInfo) extends BaseRequest(Cmd.PostSelf) {
    override def merge(req: SyncRequest) = mergeHelper[PostSelf](req)(Merged(_))
  }

  case class RegisterPushToken(token: PushToken) extends BaseRequest(Cmd.RegisterPushToken) {
    override def merge(req: SyncRequest) = mergeHelper[RegisterPushToken](req) { r =>
      Merged(this.copy(token = r.token))
    }
  }

  case class DeletePushToken(token: PushToken) extends BaseRequest(Cmd.DeletePushToken) {
    override val mergeKey: Any = (cmd, token)
  }

  case class SyncSearchResults(users: Set[UserId]) extends BaseRequest(Cmd.SyncSearchResults) {

    override def toString = s"SyncSearchResults(${users.size} users: ${users.take(5)}...)"

    override def merge(req: SyncRequest): MergeResult[SyncRequest.SyncSearchResults] = mergeHelper[SyncSearchResults](req) { other =>
      if (other.users.subsetOf(users)) Merged(this)
      else {
        val union = users ++ other.users
        if (union.size <= UsersClient.IdsCountThreshold) Merged(SyncSearchResults(union))
        else if (union.size == users.size + other.users.size) Unchanged
        else Updated(other.copy(other.users -- users))
      }
    }

    override def isDuplicateOf(req: SyncRequest): Boolean = req match {
      case SyncSearchResults(us) => users.subsetOf(us)
      case _ => false
    }
  }

  case class SyncSearchQuery(query: SearchQuery) extends BaseRequest(Cmd.SyncSearchQuery) {
    override val mergeKey: Any = (cmd, query)
  }

  case class ExactMatchHandle(handle: Handle) extends BaseRequest(Cmd.ExactMatchHandle) {
    override val mergeKey: Any = (cmd, handle)
  }

  case class SyncRichMedia(messageId: MessageId) extends BaseRequest(Cmd.SyncRichMedia) {
    override val mergeKey: Any = (cmd, messageId)
  }

  case class PostSelfPicture(assetId: UploadAssetId) extends BaseRequest(Cmd.PostSelfPicture) {
    override def merge(req: SyncRequest) = mergeHelper[PostSelfPicture](req)(Merged(_))
  }

  case class PostSelfName(name: Name) extends BaseRequest(Cmd.PostSelfName) {
    override def merge(req: SyncRequest) = mergeHelper[PostSelfName](req)(Merged(_))
  }

  case class PostSelfAccentColor(color: AccentColor) extends BaseRequest(Cmd.PostSelfAccentColor) {
    override def merge(req: SyncRequest) = mergeHelper[PostSelfAccentColor](req)(Merged(_))
  }

  case class PostAvailability(availability: Availability) extends BaseRequest(Cmd.PostAvailability) {
    override val mergeKey: Any = (cmd, availability.id)
  }

  case class PostConv(convId:       ConvId,
                      users:        Set[UserId],
                      name:         Option[Name],
                      team:         Option[TeamId],
                      access:       Set[Access],
                      accessRole:   AccessRole,
                      receiptMode:  Option[Int],
                      defaultRole:  ConversationRole
                     ) extends RequestForConversation(Cmd.PostConv) with Serialized {
    override def merge(req: SyncRequest) = mergeHelper[PostConv](req)(Merged(_))
  }

  case class PostConvReceiptMode(convId: ConvId, receiptMode: Int)
    extends RequestForConversation(Cmd.PostConvReceiptMode) with Serialized {
    override def merge(req: SyncRequest) = mergeHelper[PostConvReceiptMode](req)(Merged(_))
  }

  case class PostConvName(convId: ConvId, name: Name) extends RequestForConversation(Cmd.PostConvName) with Serialized {
    override def merge(req: SyncRequest) = mergeHelper[PostConvName](req)(Merged(_))
  }

  case class PostConvState(convId: ConvId, state: ConversationState) extends RequestForConversation(Cmd.PostConvState) with Serialized {

    private def mergeConvState(o: ConversationState, n: ConversationState) = {
      val a = if (o.archiveTime.exists(t => n.archiveTime.forall(_.isBefore(t)))) o else n
      val m = if (o.muteTime.exists(t => n.muteTime.forall(_.isBefore(t)))) o else n

      ConversationState(a.archived, a.archiveTime, m.muted, m.muteTime, m.mutedStatus)
    }

    override def merge(req: SyncRequest) = mergeHelper[PostConvState](req)(other => Merged(copy(state = mergeConvState(state, other.state))))
  }

  case class PostConvRole(convId: ConvId, userId: UserId, newRole: ConversationRole, origRole: ConversationRole)
    extends RequestForConversation(Cmd.PostConvRole) with Serialized {
    override val mergeKey: Any = (cmd, convId, userId)
    override def merge(req: SyncRequest) = mergeHelper[PostConvRole](req)(Merged(_))
  }

  case class PostLastRead(convId: ConvId, time: RemoteInstant) extends RequestForConversation(Cmd.PostLastRead) {
    override val mergeKey: Any = (cmd, convId)
    override def merge(req: SyncRequest) = mergeHelper[PostLastRead](req)(Merged(_))
  }

  case class PostCleared(convId: ConvId, time: RemoteInstant) extends RequestForConversation(Cmd.PostCleared) with Serialized {
    override val mergeKey: Any = (cmd, convId)
    override def merge(req: SyncRequest) = mergeHelper[PostCleared](req) { other =>
      Merged(PostCleared(convId, time max other.time))
    }
  }

  case class PostTypingState(convId: ConvId, isTyping: Boolean) extends RequestForConversation(Cmd.PostTypingState) with Serialized {
    override def merge(req: SyncRequest) = mergeHelper[PostTypingState](req)(Merged(_))
  }

  case class PostMessage(convId: ConvId, messageId: MessageId, editTime: RemoteInstant) extends RequestForConversation(Cmd.PostMessage) with Serialized {
    override val mergeKey = (cmd, convId, messageId)
    override def merge(req: SyncRequest) = mergeHelper[PostMessage](req) { r =>
      // those requests are merged if message was edited multiple times (or unsent message was edited before sync is finished)
      // editTime == Instant.EPOCH is a special value, it marks initial message sync, we need to preserve that info
      // sync handler will check editTime and will just upload regular message (with current content) instead of an edit if it's EPOCH
      val time = if (editTime.isEpoch) RemoteInstant.Epoch else editTime max r.editTime
      Merged(PostMessage(convId, messageId, time))
    }
  }

  case class PostOpenGraphMeta(convId: ConvId, messageId: MessageId, editTime: RemoteInstant) extends RequestForConversation(Cmd.PostOpenGraphMeta) {
    override val mergeKey = (cmd, convId, messageId)
    override def merge(req: SyncRequest) = mergeHelper[PostOpenGraphMeta](req)(r => Merged(PostOpenGraphMeta(convId, messageId, editTime max r.editTime)))
  }

  case class PostReceipt(convId: ConvId, messages: Seq[MessageId], userId: UserId, tpe: ReceiptType) extends RequestForConversation(Cmd.PostReceipt) {
    override val mergeKey = (cmd, messages, userId, tpe)
  }

  case class PostDeleted(convId: ConvId, messageId: MessageId) extends RequestForConversation(Cmd.PostDeleted) {
    override val mergeKey = (cmd, convId, messageId)
  }

  case class PostRecalled(convId: ConvId, msg: MessageId, recalledId: MessageId) extends RequestForConversation(Cmd.PostRecalled) {
    override val mergeKey = (cmd, convId, msg, recalledId)
  }

  case class PostAssetStatus(convId: ConvId, messageId: MessageId, exp: Option[FiniteDuration], status: UploadAssetStatus) extends RequestForConversation(Cmd.PostAssetStatus) with Serialized {
    override val mergeKey = (cmd, convId, messageId)
    override def merge(req: SyncRequest) = mergeHelper[PostAssetStatus](req)(Merged(_))
  }

  case class SyncConvLink(convId: ConvId) extends RequestForConversation(Cmd.SyncConvLink)

  case class PostClientLabel(id: ClientId, label: String) extends BaseRequest(Cmd.PostClientLabel) {
    override val mergeKey = (cmd, id)
  }

  case class SyncClients(userId: UserId) extends RequestForUser(Cmd.SyncClients)

  case class SyncPreKeys(userId: UserId, clients: Set[ClientId]) extends RequestForUser(Cmd.SyncPreKeys) {

    override def merge(req: SyncRequest) = mergeHelper[SyncPreKeys](req) { other =>
      if (other.clients.subsetOf(clients)) Merged(this)
      else Merged(SyncPreKeys(userId, clients ++ other.clients))
    }

    override def isDuplicateOf(req: SyncRequest): Boolean = req match {
      case SyncPreKeys(u, cs) => u == userId && clients.subsetOf(cs)
      case _ => false
    }
  }

  case class PostAddBot(cId: ConvId, pId: ProviderId, iId: IntegrationId) extends BaseRequest(Cmd.PostAddBot) {
    override val mergeKey = (cmd, cId, iId)
  }

  case class PostRemoveBot(cId: ConvId, botId: UserId) extends BaseRequest(Cmd.PostRemoveBot) {
    override val mergeKey = (cmd, cId, botId)
  }

  case class PostLiking(convId: ConvId, liking: Liking) extends RequestForConversation(Cmd.PostLiking) {
    override val mergeKey = (cmd, convId, liking.id)
  }

  case class PostButtonAction(messageId: MessageId, buttonId: ButtonId, senderId: UserId) extends BaseRequest(Cmd.PostButtonAction) {
    override val mergeKey: Any = (cmd, messageId, buttonId)
  }

  case class PostSessionReset(convId: ConvId, userId: UserId, client: ClientId) extends RequestForConversation(Cmd.PostSessionReset) {
    override val mergeKey: Any = (cmd, convId, userId, client)
  }

  case class PostConnection(userId: UserId, name: Name, message: String) extends RequestForUser(Cmd.PostConnection)

  case class PostConnectionStatus(userId: UserId, status: Option[ConnectionStatus]) extends RequestForUser(Cmd.PostConnectionStatus) {
    override def merge(req: SyncRequest) = mergeHelper[PostConnectionStatus](req)(Merged(_)) // always use incoming request value
  }

  case class SyncUser(users: Set[UserId]) extends BaseRequest(Cmd.SyncUser) {

    override def toString = s"SyncUser(${users.size} users: ${users.take(5)}...)"

    override def merge(req: SyncRequest): MergeResult[SyncRequest.SyncUser] = mergeHelper[SyncUser](req) { other =>
      if (other.users.subsetOf(users)) Merged(this)
      else {
        val union = users ++ other.users
        if (union.size <= UsersClient.IdsCountThreshold) Merged(SyncUser(union))
        else if (union.size == users.size + other.users.size) Unchanged
        else Updated(other.copy(other.users -- users))
      }
    }

    override def isDuplicateOf(req: SyncRequest): Boolean = req match {
      case SyncUser(us) => users.subsetOf(us)
      case _ => false
    }
  }

  case class SyncConversation(convs: Set[ConvId]) extends BaseRequest(Cmd.SyncConversation) {

    override def merge(req: SyncRequest) = mergeHelper[SyncConversation](req) { other =>
      if (other.convs.subsetOf(convs)) Merged(this)
      else {
        val union = convs ++ other.convs
        if (union.size <= ConversationsClient.IdsCountThreshold) Merged(SyncConversation(union))
        else if (union.size == convs.size + other.convs.size) Unchanged
        else Updated(other.copy(other.convs -- convs))
      }
    }

    override def isDuplicateOf(req: SyncRequest): Boolean = req match {
      case SyncConversation(cs) => convs.subsetOf(cs)
      case _ => false
    }
  }

  case class PostConvJoin(convId: ConvId, users: Set[UserId], conversationRole: ConversationRole) extends RequestForConversation(Cmd.PostConvJoin) with Serialized {
    override def merge(req: SyncRequest) =
      mergeHelper[PostConvJoin](req) { other => Merged(PostConvJoin(convId, users ++ other.users, conversationRole)) }

    override def isDuplicateOf(req: SyncRequest): Boolean = req match {
      case PostConvJoin(`convId`, us, _) => users.subsetOf(us)
      case _ => false
    }
  }

  // leave endpoint on backend accepts only one user as parameter (no way to remove multiple users at once)
  case class PostConvLeave(convId: ConvId, user: UserId) extends RequestForConversation(Cmd.PostConvLeave) with Serialized {
    override val mergeKey = (cmd, convId, user)
  }

  case class PostStringProperty(key: PropertyKey, value: String) extends BaseRequest(Cmd.PostStringProperty) {
    override def mergeKey: Any = (cmd, key)
  }

  case class PostBoolProperty(key: PropertyKey, value: Boolean) extends BaseRequest(Cmd.PostBoolProperty) {
    override def mergeKey: Any = (cmd, key)
  }

  case class PostIntProperty(key: PropertyKey, value: Int) extends BaseRequest(Cmd.PostIntProperty) {
    override def mergeKey: Any = (cmd, key)
  }

  case class DeleteGroupConversation(teamId: TeamId, convId: RConvId) extends BaseRequest(Cmd.DeleteGroupConv)

  private def mergeHelper[A <: SyncRequest : ClassTag](other: SyncRequest)(f: A => MergeResult[A]): MergeResult[A] = other match {
    case req: A if req.mergeKey == other.mergeKey => f(req)
    case _ => Unchanged
  }

    implicit lazy val Decoder: JsonDecoder[SyncRequest] = new JsonDecoder[SyncRequest] with StorageCodecs {
    import JsonDecoder._

    override def apply(implicit js: JSONObject): SyncRequest = {
      def convId = decodeId[ConvId]('conv)
      def rConvId = decodeId[RConvId]('rConv)
      def userId = decodeId[UserId]('user)
      def messageId = decodeId[MessageId]('message)
      def teamId = decodeId[TeamId]('teamId)
      def users = decodeUserIdSeq('users).toSet
      val cmd = js.getString("cmd")

      try {
        SyncCommand.fromName(cmd) match {
          case Cmd.SyncUser                  => SyncUser(users)
          case Cmd.SyncConversation          => SyncConversation(decodeConvIdSeq('convs).toSet)
          case Cmd.SyncConvLink              => SyncConvLink('conv)
          case Cmd.SyncSearchQuery           => SyncSearchQuery(SearchQuery.fromCacheKey(decodeString('queryCacheKey)))
          case Cmd.SyncSearchResults         => SyncSearchResults(users)
          case Cmd.ExactMatchHandle          => ExactMatchHandle(Handle(decodeString('handle)))
          case Cmd.PostConv                  => PostConv(convId, decodeStringSeq('users).map(UserId(_)).toSet, 'name, 'team, 'access, 'access_role, 'receipt_mode, 'default_role)
          case Cmd.PostConvName              => PostConvName(convId, 'name)
          case Cmd.PostConvReceiptMode       => PostConvReceiptMode(convId, 'receipt_mode)
          case Cmd.PostConvState             => PostConvState(convId, JsonDecoder[ConversationState]('state))
          case Cmd.PostConvRole              => PostConvRole(convId, userId, 'new_role, 'orig_role)
          case Cmd.PostLastRead              => PostLastRead(convId, 'time)
          case Cmd.PostCleared               => PostCleared(convId, 'time)
          case Cmd.PostTypingState           => PostTypingState(convId, 'typing)
          case Cmd.PostConnectionStatus      => PostConnectionStatus(userId, opt('status, js => ConnectionStatus(js.getString("status"))))
          case Cmd.PostSelfPicture           => PostSelfPicture(decodeUploadAssetId('asset))
          case Cmd.PostSelfName              => PostSelfName('name)
          case Cmd.PostSelfAccentColor       => PostSelfAccentColor(AccentColor(decodeInt('color)))
          case Cmd.PostAvailability          => PostAvailability(Availability(decodeInt('availability)))
          case Cmd.PostMessage               => PostMessage(convId, messageId, 'time)
          case Cmd.PostDeleted               => PostDeleted(convId, messageId)
          case Cmd.PostRecalled              => PostRecalled(convId, messageId, decodeId[MessageId]('recalled))
          case Cmd.PostAssetStatus           => PostAssetStatus(convId, messageId, decodeOptLong('ephemeral).map(_.millis), Codec[UploadAssetStatus, Int].deserialize(decodeInt('status)))
          case Cmd.PostConvJoin              => PostConvJoin(convId, users, 'conversation_role)
          case Cmd.PostConvLeave             => PostConvLeave(convId, userId)
          case Cmd.PostConnection            => PostConnection(userId, 'name, 'message)
          case Cmd.DeletePushToken           => DeletePushToken(decodeId[PushToken]('token))
          case Cmd.SyncRichMedia             => SyncRichMedia(messageId)
          case Cmd.SyncSelf                  => SyncSelf
          case Cmd.DeleteAccount             => DeleteAccount
          case Cmd.SyncConversations         => SyncConversations
          case Cmd.SyncTeam                  => SyncTeam
          case Cmd.SyncTeamMember            => SyncTeamMember(userId)
          case Cmd.SyncConnections           => SyncConnections
          case Cmd.RegisterPushToken         => RegisterPushToken(decodeId[PushToken]('token))
          case Cmd.PostSelf                  => PostSelf(JsonDecoder[UserInfo]('user))
          case Cmd.SyncSelfClients           => SyncSelfClients
          case Cmd.SyncSelfPermissions       => SyncSelfPermissions
          case Cmd.SyncClients               => SyncClients(userId)
          case Cmd.SyncClientLocation        => SyncClientsLocation
          case Cmd.SyncPreKeys               => SyncPreKeys(userId, decodeClientIdSeq('clients).toSet)
          case Cmd.PostClientLabel           => PostClientLabel(decodeId[ClientId]('client), 'label)
          case Cmd.PostLiking                => PostLiking(convId, JsonDecoder[Liking]('liking))
          case Cmd.PostAddBot                => PostAddBot(decodeId[ConvId]('convId), decodeId[ProviderId]('providerId), decodeId[IntegrationId]('integrationId))
          case Cmd.PostRemoveBot             => PostRemoveBot(decodeId[ConvId]('convId), decodeId[UserId]('botId))
          case Cmd.PostButtonAction          => PostButtonAction(messageId, decodeId[ButtonId]('button), decodeId[UserId]('sender))
          case Cmd.PostSessionReset          => PostSessionReset(convId, userId, decodeId[ClientId]('client))
          case Cmd.PostOpenGraphMeta         => PostOpenGraphMeta(convId, messageId, 'time)
          case Cmd.PostReceipt               => PostReceipt(convId, decodeMessageIdSeq('messages), userId, ReceiptType.fromName('type))
          case Cmd.PostBoolProperty          => PostBoolProperty('key, 'value)
          case Cmd.PostIntProperty           => PostIntProperty('key, 'value)
          case Cmd.PostStringProperty        => PostStringProperty('key, 'value)
          case Cmd.SyncProperties            => SyncProperties
          case Cmd.PostFolders               => PostFolders
          case Cmd.SyncFolders               => SyncFolders
          case Cmd.DeleteGroupConv           => DeleteGroupConversation(teamId, rConvId)
          case Cmd.Unknown                   => Unknown
        }
      } catch {
        case NonFatal(e) => Unknown
      }
    }
  }

  implicit lazy val Encoder: JsonEncoder[SyncRequest] = new JsonEncoder[SyncRequest] with StorageCodecs {
    import JsonEncoder._

    override def apply(req: SyncRequest): JSONObject = JsonEncoder { o =>
      def putId[A: Id](name: String, id: A) = o.put(name, implicitly[Id[A]].encode(id))

      o.put("cmd", req.cmd.name)

      req match {
        case user: RequestForUser => o.put("user", user.userId)
        case conv: RequestForConversation => o.put("conv", conv.convId)
        case _ => ()
      }

      req match {
        case SyncUser(users)                  => o.put("users", arrString(users.toSeq map (_.str)))
        case SyncSearchResults(users)         => o.put("users", arrString(users.toSeq map (_.str)))
        case SyncConversation(convs)          => o.put("convs", arrString(convs.toSeq map (_.str)))
        case SyncConvLink(conv)               => o.put("conv", conv.str)
        case SyncSearchQuery(queryCacheKey)   => o.put("queryCacheKey", queryCacheKey.cacheKey)
        case PostAddBot(cId, pId, iId)        =>
          o.put("convId", cId.str)
          o.put("providerId", pId.str)
          o.put("integrationId", iId.str)
        case PostRemoveBot(cId, botId)        =>
          o.put("convId", cId.str)
          o.put("botId", botId.str)
        case PostButtonAction(messageId, buttonId, senderId) =>
          putId("message", messageId)
          putId("button", buttonId)
          putId("sender", senderId)
        case ExactMatchHandle(handle)         => o.put("handle", handle.string)
        case SyncTeamMember(userId)           => o.put("user", userId.str)
        case DeletePushToken(token)           => putId("token", token)
        case RegisterPushToken(token)         => putId("token", token)
        case SyncRichMedia(messageId)         => putId("message", messageId)
        case PostSelfPicture(assetId)         => putId("asset", assetId)
        case PostSelfName(name)               => o.put("name", name)
        case PostSelfAccentColor(color)       => o.put("color", color.id)
        case PostAvailability(availability)   => o.put("availability", availability.id)
        case PostMessage(_, messageId, time)  =>
          putId("message", messageId)
          o.put("time", time.toEpochMilli)

        case PostDeleted(_, messageId)        => putId("message", messageId)
        case PostRecalled(_, msg, recalled)   =>
          putId("message", msg)
          putId("recalled", recalled)

        case PostConnectionStatus(_, status)  => status foreach { status => o.put("status", status.code) }
        case PostConvJoin(_, users, conversationRole)           =>
          o.put("users", arrString(users.toSeq map (_.str)))
          o.put("conversation_role", conversationRole.label)
        case PostConvLeave(_, user)           => putId("user", user)
        case PostOpenGraphMeta(_, messageId, time) =>
          putId("message", messageId)
          o.put("time", time.toEpochMilli)

        case PostReceipt(_, messages, userId, tpe) =>
          o.put("messages", arrString(messages.map(_.str)))
          putId("user", userId)
          o.put("type", tpe.name)

        case PostConnection(_, name, message) =>
          o.put("name", name)
          o.put("message", message)

        case PostLastRead(_, time) =>
          o.put("time", time.toEpochMilli)

        case PostCleared(_, time) =>
          o.put("time", time.toEpochMilli)

        case PostAssetStatus(_, mid, exp, status) =>
          putId("message", mid)
          exp.foreach(v => o.put("ephemeral", v.toMillis))
          o.put("status", Codec[UploadAssetStatus, Int].serialize(status))

        case PostSelf(info) => o.put("user", JsonEncoder.encode(info))
        case PostTypingState(_, typing) => o.put("typing", typing)
        case PostConvState(_, state) => o.put("state", JsonEncoder.encode(state))
        case PostConvName(_, name) => o.put("name", name)
        case PostConvReceiptMode(_, receiptMode) => o.put("receipt_mode", receiptMode)
        case PostConv(_, users, name, team, access, accessRole, receiptMode, defaultRole) =>
          o.put("users", arrString(users.map(_.str).toSeq))
          name.foreach(o.put("name", _))
          team.foreach(o.put("team", _))
          o.put("access", JsonEncoder.encodeAccess(access))
          o.put("access_role", JsonEncoder.encodeAccessRole(accessRole))
          receiptMode.foreach(o.put("receipt_mode", _))
          o.put("default_role", defaultRole)
        case PostConvRole(_, userId, newRole, origRole) =>
          o.put("user", userId)
          o.put("new_role", newRole)
          o.put("orig_role", origRole)
        case PostLiking(_, liking) =>
          o.put("liking", JsonEncoder.encode(liking))
        case PostClientLabel(id, label) =>
          o.put("client", id.str)
          o.put("label", label)
        case PostSessionReset(_, user, client) =>
          o.put("client", client.str)
          o.put("user", user)
        case SyncClients(user) => o.put("user", user.str)
        case SyncPreKeys(user, clients) =>
          o.put("user", user.str)
          o.put("clients", arrString(clients.toSeq map (_.str)))
        case PostBoolProperty(key, value) =>
          o.put("key", key)
          o.put("value", value)
        case PostIntProperty(key, value) =>
          o.put("key", key)
          o.put("value", value)
        case PostStringProperty(key, value) =>
          o.put("key", key)
          o.put("value", value)
        case PostFolders | SyncFolders | SyncSelf | SyncTeam | DeleteAccount | SyncConversations | SyncConnections |
             SyncSelfClients | SyncSelfPermissions | SyncClientsLocation | SyncProperties | Unknown => () // nothing to do
        case DeleteGroupConversation(teamId, rConvId)  =>
          o.put("teamId", teamId.str)
          o.put("rConv", rConvId.str)
      }
    }
  }
}

object SerialConvRequest {
  import SyncRequest._
  def unapply(req: SyncRequest): Option[ConvId] = req match {
    case p: RequestForConversation with Serialized => Some(p.convId)
    case _ => None
  }
}
