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
package com.waz.sync.client

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.{ConversationType, Link}
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationResponse.{ConversationsResult, Decoder, QConversationsResult}
import com.waz.utils.JsonDecoder.{decodeBool, decodeSeq, decodeOptString}
import com.waz.utils.JsonEncoder.{encodeAccess, encodeAccessRole}
import com.waz.utils.{Json, JsonDecoder, JsonEncoder, returning, _}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import org.json
import org.json.JSONObject

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Right
import scala.util.control.NonFatal

trait ConversationsClient {
  import ConversationsClient._
  def loadConversations(start: Option[RConvId] = None, limit: Int = ConversationsPageSize): ErrorOrResponse[ConversationsResult]
  def loadConversations(ids: Set[RConvId]): ErrorOrResponse[Seq[ConversationResponse]]
  def loadQualifiedConversations(ids: Set[RConvQualifiedId]): ErrorOrResponse[QConversationsResult]
  def loadQualifiedConversationsIds(pagingState: Option[String]): ErrorOrResponse[ListConversationsIdsResponse]
  def loadConversationRoles(remoteIds: Set[RConvId], defRoles: Set[ConversationRole]): Future[Map[RConvId, Set[ConversationRole]]]
  def postName(convId: RConvId, name: Name): ErrorOrResponse[Option[RenameConversationEvent]]
  def postConversationState(convId: RConvId, state: ConversationState): ErrorOrResponse[Unit]
  def postMessageTimer(convId: RConvId, duration: Option[FiniteDuration]): ErrorOrResponse[Unit]
  def postMemberJoin(conv: RConvId, members: Set[UserId], defaultRole: ConversationRole): ErrorOrResponse[Option[MemberJoinEvent]]
  def postQualifiedMemberJoin(conv: RConvId, members: Set[QualifiedId], defaultRole: ConversationRole): ErrorOrResponse[Option[MemberJoinEvent]]
  def postMemberLeave(conv: RConvId, user: UserId): ErrorOrResponse[Option[MemberLeaveEvent]]
  def postQualifiedMemberLeave(qConvId: RConvQualifiedId, qUserId: QualifiedId): ErrorOrResponse[Option[MemberLeaveEvent]]
  def createLink(conv: RConvId): ErrorOrResponse[Link]
  def removeLink(conv: RConvId): ErrorOrResponse[Unit]
  def getLink(conv: RConvId): ErrorOrResponse[Option[Link]]
  def postAccessUpdate(conv: RConvId, access: Set[Access], accessRole: AccessRole): ErrorOrResponse[Unit]
  def postReceiptMode(conv: RConvId, receiptMode: Int): ErrorOrResponse[Unit]
  def postConversation(state: ConversationInitState): ErrorOrResponse[ConversationResponse]
  def postConversationRole(id: RConvId, userId: UserId, role: ConversationRole): ErrorOrResponse[Unit]
  def getGuestroomOverview(key: String, code: String): ErrorOrResponse[ConversationOverviewResponse]
  def postJoinConversation(key: String, code: String): ErrorOrResponse[Option[MemberJoinEvent]]
}

class ConversationsClientImpl(implicit
                              urlCreator: UrlCreator,
                              httpClient: HttpClient,
                              authRequestInterceptor: AuthRequestInterceptor) extends ConversationsClient with DerivedLogTag {

  import ConversationsClient._
  import HttpClient.AutoDerivationOld._
  import HttpClient.dsl._
  import com.waz.threading.Threading.Implicits.Background

  private implicit val ConversationsResultDeserializer: RawBodyDeserializer[ConversationsResult] =
    RawBodyDeserializer[JSONObject].map { implicit json =>
      val ids = decodeSeq[ConversationResponse]('conversations)
      val hasMore = decodeBool('has_more)
      ConversationsResult(ids, hasMore)
    }

  private implicit val QConversationsResultDeserializer: RawBodyDeserializer[QConversationsResult] =
    RawBodyDeserializer[JSONObject].map { implicit json =>
      val found = decodeSeq[ConversationResponse]('found)
      val notFound = decodeSeq[RConvQualifiedId]('not_found)
      val failed = decodeSeq[RConvQualifiedId]('failed)
      QConversationsResult(found, notFound.toSet, failed.toSet)
    }

  private implicit val ListConversationIdsResponseDeserializer: RawBodyDeserializer[ListConversationsIdsResponse] =
    RawBodyDeserializer[JSONObject].map { implicit json =>
      val qIds = decodeSeq[RConvQualifiedId]('qualified_conversations)
      val hasMore = decodeBool('has_more)
      val pagingState = decodeOptString('paging_state)
      ListConversationsIdsResponse(qIds.toSet, hasMore, pagingState)
    }

  override def loadConversations(start: Option[RConvId] = None, limit: Int = ConversationsPageSize): ErrorOrResponse[ConversationsResult] = {
    Request
      .Get(
        relativePath = ConversationsPath,
        queryParameters = queryParameters("size" -> limit, "start" -> start)
      )
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadConversations(ids: Set[RConvId]): ErrorOrResponse[Seq[ConversationResponse]] = {
    Request
      .Get(relativePath = ConversationsPath, queryParameters = queryParameters("ids" -> ids.mkString(",")))
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map(_.map(_.conversations))
  }

  override def loadQualifiedConversations(ids: Set[RConvQualifiedId]): ErrorOrResponse[QConversationsResult] = {
    Request
      .Post(
        relativePath = ListConversationsPath,
        body = Json("qualified_ids" -> RConvQualifiedId.encode(ids))
      )
      .withResultType[QConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadQualifiedConversationsIds(pagingState: Option[String]): ErrorOrResponse[ListConversationsIdsResponse] = {
    Request
      .Post(
        relativePath = ListConversationsIdsPath,
        body = pagingState.map(state => Json("paging_state" -> state)).getOrElse(Json())
      )
      .withResultType[ListConversationsIdsResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  private def loadConversationRoles(id: RConvId): ErrorOrResponse[Set[ConversationRole]] = {
    Request.Get(relativePath = rolesPath(id))
      .withResultType[ConvRoles]
      .withErrorType[ErrorResponse]
      .executeSafe(_.toConversationRoles)
  }

  override def loadConversationRoles(remoteIds: Set[RConvId], defRoles: Set[ConversationRole]): Future[Map[RConvId, Set[ConversationRole]]] =
    Future.sequence(
      remoteIds.map(rConvId => loadConversationRoles(rConvId).future.map {
        case Right(roles) => rConvId -> roles
        case _            => rConvId -> defRoles
      })
    ).map(_.toMap)

  private implicit val EventsResponseDeserializer: RawBodyDeserializer[List[ConversationEvent]] =
    RawBodyDeserializer[JSONObject].map(json => EventsResponse.unapplySeq(JsonObjectResponse(json)).get)

  override def postName(convId: RConvId, name: Name): ErrorOrResponse[Option[RenameConversationEvent]] = {
    Request.Put(relativePath = s"$ConversationsPath/$convId", body = Json("name" -> name))
      .withResultType[List[ConversationEvent]]
      .withErrorType[ErrorResponse]
      .executeSafe {
        case (event: RenameConversationEvent) :: Nil => Some(event)
        case _ => None
      }
  }

  override def postMessageTimer(convId: RConvId, duration: Option[FiniteDuration]): ErrorOrResponse[Unit] = {
    Request
      .Put(
        relativePath = s"$ConversationsPath/$convId/message-timer",
        body = Json("message_timer" -> duration.map(_.toMillis))
      )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def postConversationState(convId: RConvId, state: ConversationState): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = s"$ConversationsPath/$convId/self", body = state)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def postMemberJoin(conv: RConvId, members: Set[UserId], defaultRole: ConversationRole): ErrorOrResponse[Option[MemberJoinEvent]] = {
    Request.Post(
      relativePath = membersPath(conv),
      body = Json("users" -> Json(members), "conversation_role" -> defaultRole.label)
    )
      .withResultType[Option[List[ConversationEvent]]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.collect { case (event: MemberJoinEvent) :: Nil => event })
  }

  override def postQualifiedMemberJoin(conv: RConvId, members: Set[QualifiedId], defaultRole: ConversationRole): ErrorOrResponse[Option[MemberJoinEvent]] = {
    Request.Post(
      relativePath = qualifiedMembersPath(conv),
      body = Json("qualified_users" -> QualifiedId.encode(members), "conversation_role" -> defaultRole.label)
    )
      .withResultType[Option[List[ConversationEvent]]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.collect { case (event: MemberJoinEvent) :: Nil => event })
  }

  override def postMemberLeave(convId: RConvId, userId: UserId): ErrorOrResponse[Option[MemberLeaveEvent]] =
    Request.Delete(relativePath = memberLeavePath(convId, userId))
      .withResultType[Option[List[ConversationEvent]]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.collect { case (event: MemberLeaveEvent) :: Nil => event })

  override def postQualifiedMemberLeave(qConvId: RConvQualifiedId, qUserId: QualifiedId): ErrorOrResponse[Option[MemberLeaveEvent]] =
    Request.Delete(relativePath = qualifiedMemberLeavePath(qConvId, qUserId))
      .withResultType[Option[List[ConversationEvent]]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.collect { case (event: MemberLeaveEvent) :: Nil => event })

  override def createLink(conv: RConvId): ErrorOrResponse[Link] = {
    Request.Post(relativePath = s"$ConversationsPath/$conv/code", body = "")
      .withResultType[Response[JSONObject]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        val js = response.body
        if (response.code == ResponseCode.Success && js.has("uri"))
          Link(js.getString("uri"))
        else if (response.code == ResponseCode.Created && js.getJSONObject("data").has("uri"))
          Link(js.getJSONObject("data").getString("uri"))
        else
          throw new IllegalArgumentException(s"Can not extract link from json: $js")
      }

  }

  def removeLink(conv: RConvId): ErrorOrResponse[Unit] = {
    Request.Delete(relativePath = s"$ConversationsPath/$conv/code")
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def getLink(conv: RConvId): ErrorOrResponse[Option[Link]] = {
    Request.Get(relativePath = s"$ConversationsPath/$conv/code")
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[Response[JSONObject]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        val js = response.body
        if (ResponseCode.isSuccessful(response.code) && js.has("uri"))
          Some(Link(js.getString("uri")))
        else if (response.code == ResponseCode.NotFound)
          None
        else
          throw new IllegalArgumentException(s"Can not extract link from json: $js")
      }
  }

  def postAccessUpdate(conv: RConvId, access: Set[Access], accessRole: AccessRole): ErrorOrResponse[Unit] = {
    Request
      .Put(
        relativePath = accessUpdatePath(conv),
        body = Json(
          "access" -> encodeAccess(access),
          "access_role" -> encodeAccessRole(accessRole)
        )
      )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def postReceiptMode(conv: RConvId, receiptMode: Int): ErrorOrResponse[Unit] = {
    Request.Put(
      relativePath = receiptModePath(conv),
      body = Json("receipt_mode" -> receiptMode)
    )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def postConversation(state: ConversationInitState): ErrorOrResponse[ConversationResponse] = {
    verbose(l"postConversation($state): \n${ConversationInitState.Encoder(state).toString(2)}")
    Request.Post(relativePath = ConversationsPath, body = state)
      .withResultType[ConversationResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def postConversationRole(conv: RConvId, userId: UserId, role: ConversationRole): ErrorOrResponse[Unit] = {
    verbose(l"postConversationRole($conv, $userId, $role)")
    Request.Put(
      relativePath = s"${membersPath(conv)}/$userId",
      body = Json("conversation_role" -> role.label)
    )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getGuestroomOverview(key: String, code: String): ErrorOrResponse[ConversationOverviewResponse] = {
    verbose(l"getGuestroomOverview($key, $code)")
    Request.Get(
      relativePath = JoinConversationPath,
      queryParameters("key" -> key, "code" -> code)
    )
      .withResultType[ConversationOverviewResponse]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  private implicit val MemberJoinEventDeserializer: RawBodyDeserializer[Option[MemberJoinEvent]] =
    RawBodyDeserializer[JSONObject].map { json =>
      val convEvent = EventsResponse.unapply(JsonObjectResponse(json)).get
      convEvent match {
        case event: MemberJoinEvent => Some(event)
        case _                      => None
      }
    }

  override def postJoinConversation(key: String, code: String): ErrorOrResponse[Option[MemberJoinEvent]] = {
    verbose(l"postJoinConversation($key, $code)")
    Request.Post(
      relativePath = JoinConversationPath,
      body = Json("key" -> key, "code" -> code)
    )
      .withResultType[Option[MemberJoinEvent]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object ConversationsClient {
  val ConversationsPath = "/conversations"
  val ListConversationsPath = "/conversations/list/v2"
  val ListConversationsIdsPath = "/conversations/list-ids"
  val JoinConversationPath = "/conversations/join"
  val ConversationsPageSize = 100
  val ConversationIdsPageSize = 1000
  val IdsCountThreshold = 32

  def accessUpdatePath(id: RConvId): String = s"$ConversationsPath/${id.str}/access"
  def receiptModePath(id: RConvId): String = s"$ConversationsPath/${id.str}/receipt-mode"
  def rolesPath(id: RConvId): String = s"$ConversationsPath/${id.str}/roles"
  def membersPath(id: RConvId): String = s"$ConversationsPath/${id.str}/members"
  def qualifiedMembersPath(id: RConvId): String = s"$ConversationsPath/${id.str}/members/v2"
  def memberLeavePath(convId: RConvId, userId: UserId): String =
    s"$ConversationsPath/${convId.str}/members/${userId.str}"
  def qualifiedMemberLeavePath(qConvId: RConvQualifiedId, qUserId: QualifiedId): String =
    if (qConvId.hasDomain && qUserId.hasDomain)
      s"$ConversationsPath/${qConvId.domain}/${qConvId.id.str}/members/${qUserId.domain}/${qUserId.id.str}"
    else
      memberLeavePath(qConvId.id, qUserId.id)

  final case class ConversationInitState(users:            Set[UserId],
                                         qualifiedUsers:   Set[QualifiedId],
                                         name:             Option[Name] = None,
                                         team:             Option[TeamId],
                                         access:           Set[Access],
                                         accessRole:       AccessRole,
                                         receiptMode:      Option[Int],
                                         conversationRole: ConversationRole
                                        )

  object ConversationInitState {
    implicit lazy val Encoder: JsonEncoder[ConversationInitState] = new JsonEncoder[ConversationInitState] {
      override def apply(state: ConversationInitState): JSONObject = JsonEncoder { o =>
        if (state.users.nonEmpty)
          o.put("users", Json(state.users))
        if (state.qualifiedUsers.nonEmpty)
          o.put("qualified_users", QualifiedId.encode(state.qualifiedUsers))
        state.name.foreach(o.put("name", _))
        state.team.foreach(t => o.put("team", returning(new json.JSONObject()) { o =>
          o.put("teamid", t.str)
          o.put("managed", false)
        }))
        o.put("access", encodeAccess(state.access))
        o.put("access_role", encodeAccessRole(state.accessRole))
        state.receiptMode.foreach(o.put("receipt_mode", _))
        o.put("conversation_role", state.conversationRole.label)
      }
    }
  }

  final case class ConversationResponse(id:           RConvId,
                                        domain:       Domain,
                                        name:         Option[Name],
                                        creator:      UserId,
                                        convType:     ConversationType,
                                        team:         Option[TeamId],
                                        muted:        MuteSet,
                                        mutedTime:    RemoteInstant,
                                        archived:     Boolean,
                                        archivedTime: RemoteInstant,
                                        access:       Set[Access],
                                        accessRole:   Option[AccessRole],
                                        link:         Option[Link],
                                        messageTimer: Option[FiniteDuration],
                                        members:      Map[QualifiedId, ConversationRole],
                                        receiptMode:  Option[Int]
                                       ) {
    lazy val qualifiedId: Option[RConvQualifiedId] = domain.mapOpt(RConvQualifiedId(id, _))
    def hasDomain: Boolean = domain.isDefined
    lazy val memberIds: Set[UserId] = members.keySet.map(_.id)
    lazy val qualifiedMemberIds: Set[QualifiedId] = members.keySet.filter(_.hasDomain)
  }

  object ConversationResponse extends DerivedLogTag {
    import ConversationRole._
    import com.waz.utils.JsonDecoder._

    implicit lazy val Decoder: JsonDecoder[ConversationResponse] = new JsonDecoder[ConversationResponse] {
      override def apply(implicit js: JSONObject): ConversationResponse = {
        verbose(l"ConversationResponse: ${js.toString}")
        val members = js.getJSONObject("members")
        val state = ConversationState.Decoder(members.getJSONObject("self"))
        val selfRole: Map[QualifiedId, ConversationRole] = (state.target, state.conversationRole) match {
          case (Some(qId), Some(role)) => Map(qId -> role)
          case _                       => Map.empty
        }

        val (id, domain) =
          RConvQualifiedId.decodeOpt('qualified_id)
            .map(qId => (qId.id, if (qId.hasDomain) Domain(qId.domain) else Domain.Empty))
            .getOrElse((JsonDecoder.decodeRConvId('id), Domain.Empty))

        ConversationResponse(
          id,
          domain,
          'name,
          'creator,
          'type,
          'team,
          MuteSet.resolveMuted(state, isTeam = true),
          state.muteTime.getOrElse(RemoteInstant.Epoch),
          state.archived.getOrElse(false),
          state.archiveTime.getOrElse(RemoteInstant.Epoch),
          'access,
          'access_role,
          'link,
          decodeOptLong('message_timer).map(EphemeralDuration(_)),
          decodeQualifiedIdsWithRoles('others)(members) ++ selfRole,
          decodeOptInt('receipt_mode)
        )
      }
    }

    final case class ConversationsResult(conversations: Seq[ConversationResponse], hasMore: Boolean)
    final case class QConversationsResult(found: Seq[ConversationResponse], notFound: Set[RConvQualifiedId], failed: Set[RConvQualifiedId])

    object QConversationsResult {
      val Empty: QConversationsResult = QConversationsResult(Nil, Set.empty, Set.empty)
    }
  }

  final case class ConvRole(conversation_role: String, actions: Seq[String]) {
    def toConversationRole: ConversationRole = ConversationRole(conversation_role, actions.flatMap(a => ConversationAction.allActions.find(_.name == a)).toSet)
  }

  object ConvRole extends DerivedLogTag {
    import com.waz.utils.JsonDecoder._
    implicit lazy val Decoder: JsonDecoder[ConvRole] = new JsonDecoder[ConvRole] {
      override def apply(implicit js: JSONObject): ConvRole = ConvRole('conversation_role, decodeStringSeq('actions))
    }
  }

  final case class ConvRoles(conversation_roles: Seq[ConvRole]) {
    def toConversationRoles: Set[ConversationRole] = conversation_roles.map(_.toConversationRole).toSet
  }

  object ConvRoles extends DerivedLogTag {
    import com.waz.utils.JsonDecoder._

    implicit lazy val Decoder: JsonDecoder[ConvRoles] = new JsonDecoder[ConvRoles] {
      override def apply(implicit js: JSONObject): ConvRoles = ConvRoles(decodeSeq[ConvRole]('conversation_roles))
    }
  }

  object EventsResponse extends DerivedLogTag {
    import com.waz.utils.JsonDecoder._

    def unapplySeq(response: ResponseContent): Option[List[ConversationEvent]] = try {
      response match {
        case JsonObjectResponse(js) if js.has("events") => Some(array[ConversationEvent](js.getJSONArray("events")).toList)
        case JsonArrayResponse(js) => Some(array[ConversationEvent](js).toList)
        case JsonObjectResponse(js) => Some(List(implicitly[JsonDecoder[ConversationEvent]].apply(js)))
        case _ => None
      }
    } catch {
      case NonFatal(e) =>
        warn(l"couldn't parse events response", e)
        None
    }

    def unapply(response: ResponseContent): Option[ConversationEvent] = try {
      response match {
        case JsonObjectResponse(js) => Some(implicitly[JsonDecoder[ConversationEvent]].apply(js))
        case _ => None
      }
    } catch {
      case NonFatal(e) =>
        warn(l"couldn't parse events response", e)
        None
    }
  }

  final case class ConversationOverviewResponse(id: RConvId, name: String)

  object ConversationOverviewResponse extends DerivedLogTag {
    import com.waz.utils.JsonDecoder._
    implicit lazy val Decoder: JsonDecoder[ConversationOverviewResponse] = new JsonDecoder[ConversationOverviewResponse] {
      override def apply(implicit js: JSONObject): ConversationOverviewResponse =
        ConversationOverviewResponse(decodeRConvId('id), decodeString('name))
    }
  }

  final case class ListConversationsIdsResponse(qIds: Set[RConvQualifiedId], hasMore: Boolean, pagingState: Option[String])
}
