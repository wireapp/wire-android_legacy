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

import com.waz.api.Verification
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.UserData.ConnectionStatus
import com.waz.service.SearchKey
import com.waz.sync.client.UserSearchClient.UserSearchEntry
import com.waz.utils._
import com.waz.utils.wrappers.{DB, DBCursor}
import org.json.JSONObject

import scala.concurrent.duration._

case class UserData(id:                    UserId,
                    teamId:                Option[TeamId]        = None,
                    name:                  String,
                    email:                 Option[EmailAddress]  = None,
                    phone:                 Option[PhoneNumber]   = None,
                    trackingId:            Option[TrackingId]    = None,
                    picture:               Option[AssetId]       = None,
                    accent:                Int                   = 0, // accent color id
                    searchKey:             SearchKey,
                    connection:            ConnectionStatus      = ConnectionStatus.Unconnected,
                    connectionLastUpdated: RemoteInstant         = RemoteInstant.Epoch, // server side timestamp of last connection update
                    connectionMessage:     Option[String]        = None, // incoming connection request message
                    conversation:          Option[RConvId]       = None, // remote conversation id with this contact (one-to-one)
                    relation:              Relation              = Relation.Other, //unused - remove in future migration
                    syncTimestamp:         Option[LocalInstant]  = None,
                    displayName:           String                = "",
                    verified:              Verification          = Verification.UNKNOWN, // user is verified if he has any otr client, and all his clients are verified
                    deleted:               Boolean               = false,
                    availability:          Availability          = Availability.None,
                    handle:                Option[Handle]        = None,
                    providerId:            Option[ProviderId]    = None,
                    integrationId:         Option[IntegrationId] = None,
                    expiresAt:             Option[RemoteInstant] = None) {

  def isConnected = ConnectionStatus.isConnected(connection)
  def hasEmailOrPhone = email.isDefined || phone.isDefined
  def isSelf = connection == ConnectionStatus.Self
  def isAcceptedOrPending = connection == ConnectionStatus.Accepted || connection == ConnectionStatus.PendingFromOther || connection == ConnectionStatus.PendingFromUser
  def isVerified = verified == Verification.VERIFIED
  def isAutoConnect = isConnected && ! isSelf && connectionMessage.isEmpty
  lazy val isWireBot = integrationId.nonEmpty

  def getDisplayName = if (displayName.isEmpty) name else displayName

  def updated(user: UserInfo): UserData = updated(user, withSearchKey = true)
  def updated(user: UserInfo, withSearchKey: Boolean): UserData = copy(
    name = user.name.getOrElse(name),
    email = user.email.orElse(email),
    phone = user.phone.orElse(phone),
    accent = user.accentId.getOrElse(accent),
    trackingId = user.trackingId.orElse(trackingId),
    searchKey = SearchKey(if (withSearchKey) user.name.getOrElse(name) else ""),
    picture = user.mediumPicture.map(_.id).orElse(picture),
    deleted = user.deleted,
    handle = user.handle match {
      case Some(h) if !h.toString.isEmpty => Some(h)
      case _ => handle
    },
    providerId = user.service.map(_.provider),
    integrationId = user.service.map(_.id),
    expiresAt = user.expiresAt,
    teamId = user.teamId.orElse(teamId)
  )

  def updated(user: UserSearchEntry): UserData = copy(
    name = user.name,
    searchKey = SearchKey(user.name),
    accent = user.colorId.getOrElse(accent),
    handle = Some(user.handle)
  )

  def updateConnectionStatus(status: UserData.ConnectionStatus, time: Option[RemoteInstant] = None, message: Option[String] = None): UserData = {
    if (time.exists(_.isBefore(this.connectionLastUpdated))) this
    else if (this.connection == status) time.fold(this) { time => this.copy(connectionLastUpdated = time) }
    else {
      val relation = (this.relation, status) match {
        case (_, ConnectionStatus.Accepted) => Relation.First
        case (Relation.First, _) => Relation.Other
        case (rel, _) => rel
      }

      this.copy(
        connection = status,
        relation = relation,
        connectionLastUpdated = time.getOrElse(this.connectionLastUpdated + 1.millis),
        connectionMessage = message.orElse(this.connectionMessage))
    }
  }

  def isGuest(ourTeamId: TeamId): Boolean = isGuest(Some(ourTeamId))

  def isGuest(ourTeamId: Option[TeamId]): Boolean = ourTeamId.isDefined && teamId != ourTeamId

  def matchesFilter(filter: String): Boolean = {
    val isHandleSearch = Handle.isHandle(filter)
    matchesFilter(filter, isHandleSearch)
  }

  def matchesFilter(filter: String, handleOnly: Boolean): Boolean =
    handle.exists(_.startsWithQuery(filter)) ||
      (!handleOnly && (SearchKey(filter).isAtTheStartOfAnyWordIn(searchKey) || email.exists(e => filter.trim.equalsIgnoreCase(e.str))))
}

object UserData {

  lazy val Empty = UserData(UserId("EMPTY"), "")

  type ConnectionStatus = com.waz.api.User.ConnectionStatus
  object ConnectionStatus {
    import com.waz.api.User.ConnectionStatus._

    val Unconnected = UNCONNECTED
    val PendingFromUser = PENDING_FROM_USER
    val PendingFromOther = PENDING_FROM_OTHER
    val Accepted = ACCEPTED
    val Blocked = BLOCKED
    val Ignored = IGNORED
    val Self = SELF
    val Cancelled = CANCELLED

    val codeMap = Seq(Unconnected, PendingFromOther, PendingFromUser, Accepted, Blocked, Ignored, Self, Cancelled).map(v => v.code -> v).toMap

    def apply(code: String) = codeMap.getOrElse(code, Unconnected)

    def isConnected(status: ConnectionStatus) = status == Accepted || status == Blocked || status == Self
  }

  // used for testing only
  def apply(name: String): UserData = UserData(UserId(name), name = name, searchKey = SearchKey.simple(name))

  def apply(id: UserId, name: String): UserData = UserData(id, None, name, None, None, searchKey = SearchKey(name), handle = None)

  def apply(entry: UserSearchEntry): UserData =
    UserData(
      id        = entry.id,
      teamId    = None,
      name      = entry.name,
      accent    = entry.colorId.getOrElse(0),
      searchKey = SearchKey(entry.name),
      handle    = Some(entry.handle)
    ) // TODO: improve connection, relation, search level stuff

  def apply(user: UserInfo): UserData = apply(user, withSearchKey = true)

  def apply(user: UserInfo, withSearchKey: Boolean): UserData =
    UserData(user.id, None, user.name.getOrElse(""), user.email, user.phone, user.trackingId, user.mediumPicture.map(_.id),
      user.accentId.getOrElse(AccentColor.defaultColor.id), SearchKey(if (withSearchKey) user.name.getOrElse("") else ""), deleted = user.deleted,
      handle = user.handle, providerId = user.service.map(_.provider), integrationId = user.service.map(_.id))

  implicit lazy val Decoder: JsonDecoder[UserData] = new JsonDecoder[UserData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): UserData = UserData(
      id = 'id, teamId = decodeOptTeamId('teamId), name = 'name, email = decodeOptEmailAddress('email), phone = decodeOptPhoneNumber('phone),
      trackingId = decodeOptId[TrackingId]('trackingId), picture = decodeOptAssetId('assetId), accent = decodeInt('accent), searchKey = SearchKey('name),
      connection = ConnectionStatus('connection), connectionLastUpdated = RemoteInstant.ofEpochMilli(decodeLong('connectionLastUpdated)), connectionMessage = decodeOptString('connectionMessage),
      conversation = decodeOptRConvId('rconvId), relation = Relation.withId('relation),
      syncTimestamp = decodeOptLocalInstant('syncTimestamp), 'displayName, Verification.valueOf('verified), deleted = 'deleted,
      availability = Availability(decodeInt('activityStatus)), handle = decodeOptHandle('handle),
      providerId = decodeOptId[ProviderId]('providerId), integrationId = decodeOptId[IntegrationId]('integrationId),
      expiresAt = decodeOptISOInstant('expires_at).map(RemoteInstant(_))
    )
  }

  implicit lazy val Encoder: JsonEncoder[UserData] = new JsonEncoder[UserData] {
    override def apply(v: UserData): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      v.teamId foreach (id => o.put("teamId", id.str))
      o.put("name", v.name)
      v.email foreach (o.put("email", _))
      v.phone foreach (o.put("phone", _))
      v.trackingId foreach (id => o.put("trackingId", id.str))
      v.picture foreach (id => o.put("assetId", id.str))
      o.put("accent", v.accent)
      o.put("connection", v.connection.code)
      o.put("connectionLastUpdated", v.connectionLastUpdated.toEpochMilli)
      v.connectionMessage foreach (o.put("connectionMessage", _))
      v.conversation foreach (id => o.put("rconvId", id.str))
      o.put("relation", v.relation.id)
      v.syncTimestamp.foreach(v => o.put("syncTimestamp", v.toEpochMilli))
      o.put("displayName", v.displayName)
      o.put("verified", v.verified.name)
      o.put("deleted", v.deleted)
      o.put("availability", v.availability.id)
      v.handle foreach(u => o.put("handle", u.string))
      v.providerId.foreach { pId => o.put("providerId", pId.str) }
      v.integrationId.foreach { iId => o.put("integrationId", iId.str) }
      v.expiresAt.foreach(v => o.put("expires_at", v))
    }
  }

  implicit object UserDataDao extends Dao[UserData, UserId] {
    val Id = id[UserId]('_id, "PRIMARY KEY").apply(_.id)
    val TeamId = opt(id[TeamId]('teamId))(_.teamId)
    val Name = text('name)(_.name)
    val Email = opt(emailAddress('email))(_.email)
    val Phone = opt(phoneNumber('phone))(_.phone)
    val TrackingId = opt(id[TrackingId]('tracking_id))(_.trackingId)
    val Picture = opt(id[AssetId]('picture))(_.picture)
    val Accent = int('accent)(_.accent)
    val SKey = text[SearchKey]('skey, _.asciiRepresentation, SearchKey.unsafeRestore)(_.searchKey)
    val Conn = text[ConnectionStatus]('connection, _.code, ConnectionStatus(_))(_.connection)
    val ConnTime = date('conn_timestamp)(_.connectionLastUpdated.javaDate) //TODO: Migrate instead?
    val ConnMessage = opt(text('conn_msg))(_.connectionMessage)
    val Conversation = opt(id[RConvId]('conversation))(_.conversation)
    val Rel = text[Relation]('relation, _.name, Relation.valueOf)(_.relation)
    val Timestamp = opt(localTimestamp('timestamp))(_.syncTimestamp)
    val DisplayName = text('display_name)(_.displayName)
    val Verified = text[Verification]('verified, _.name, Verification.valueOf)(_.verified)
    val Deleted = bool('deleted)(_.deleted)
    val AvailabilityStatus = int[Availability]('availability, _.id, Availability.apply)(_.availability)
    val Handle = opt(handle('handle))(_.handle)
    val ProviderId = opt(id[ProviderId]('provider_id))(_.providerId)
    val IntegrationId = opt(id[IntegrationId]('integration_id))(_.integrationId)
    val ExpiresAt = opt(remoteTimestamp('expires_at))(_.expiresAt)

    override val idCol = Id
    override val table = Table(
      "Users", Id, TeamId, Name, Email, Phone, TrackingId, Picture, Accent, SKey, Conn, ConnTime, ConnMessage,
      Conversation, Rel, Timestamp, DisplayName, Verified, Deleted, AvailabilityStatus, Handle, ProviderId, IntegrationId, ExpiresAt
    )

    override def apply(implicit cursor: DBCursor): UserData = new UserData(
      Id, TeamId, Name, Email, Phone, TrackingId, Picture, Accent, SKey, Conn, RemoteInstant.ofEpochMilli(ConnTime.getTime), ConnMessage,
      Conversation, Rel, Timestamp, DisplayName, Verified, Deleted, AvailabilityStatus, Handle, ProviderId, IntegrationId, ExpiresAt
    )

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Conversation_id on Users (${Id.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS UserData_search_key on Users (${SKey.name})")
    }

    def get(id: UserId)(implicit db: DB): Option[UserData] = single(find(Id, id)(db))

    override def getCursor(id: UserId)(implicit db: DB): DBCursor = find(Id, id)(db)

    override def delete(id: UserId)(implicit db: DB): Int = db.delete(table.name, Id.name + "=?", Array(id.toString))

    def findByConnectionStatus(status: Set[ConnectionStatus])(implicit db: DB): Managed[Iterator[UserData]] = iterating(findInSet(Conn, status))

    def findAll(users: Set[UserId])(implicit db: DB) = iterating(findInSet(Id, users))

    def listContacts(implicit db: DB) = list(db.query(table.name, null, s"(${Conn.name} = ? or ${Conn.name} = ?) and ${Deleted.name} = 0", Array(ConnectionStatus.Accepted.code, ConnectionStatus.Blocked.code), null, null, null))

    def topPeople(implicit db: DB): Managed[Iterator[UserData]] =
      search(s"${Conn.name} = ? and ${Deleted.name} = 0", Array(Conn(ConnectionStatus.Accepted)))

    def recommendedPeople(prefix: String)(implicit db: DB): Managed[Iterator[UserData]] = {
      val query = SearchKey(prefix)
      search(s"""(
                |  (
                |    (
                |      ${SKey.name} LIKE ? OR ${SKey.name} LIKE ?
                |    ) AND (${Rel.name} = '${Rel(Relation.First)}' OR ${Rel.name} = '${Rel(Relation.Second)}' OR ${Rel.name} = '${Rel(Relation.Third)}')
                |  ) OR ${Email.name} = ?
                |    OR ${Handle.name} LIKE ?
                |) AND ${Deleted.name} = 0
                |  AND ${Conn.name} != '${Conn(ConnectionStatus.Accepted)}' AND ${Conn.name} != '${Conn(ConnectionStatus.Blocked)}' AND ${Conn.name} != '${Conn(ConnectionStatus.Self)}'
              """.stripMargin,
        Array(s"${query.asciiRepresentation}%", s"% ${query.asciiRepresentation}%", prefix, s"%${query.asciiRepresentation}%"))
    }

    def search(whereClause: String, args: Array[String])(implicit db: DB): Managed[Iterator[UserData]] =
      iterating(db.query(table.name, null, whereClause, args, null, null,
        s"case when ${Conn.name} = '${Conn(ConnectionStatus.Accepted)}' then 0 when ${Rel.name} != '${Relation.Other.name}' then 1 else 2 end ASC, ${Name.name} ASC"))

    def search(prefix: SearchKey, handleOnly: Boolean, teamId: Option[TeamId])(implicit db: DB): Set[UserData] = {
      val select = s"SELECT u.* FROM ${table.name} u WHERE "
      val handleCondition =
        if (handleOnly){
          s"""u.${Handle.name} LIKE '%${prefix.asciiRepresentation}%'""".stripMargin
        } else {
          s"""(
             |     u.${SKey.name} LIKE '${SKey(prefix)}%'
             |     OR u.${SKey.name} LIKE '% ${SKey(prefix)}%'
             |     OR u.${Handle.name} LIKE '%${prefix.asciiRepresentation}%')""".stripMargin
        }
      val teamCondition = teamId.map(tId => s"AND u.${TeamId.name} = '$tId'")

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse(""), null)).toSet
    }

    def findForTeams(teams: Set[TeamId])(implicit db: DB) = iterating(findInSet(TeamId, teams.map(Option(_))))

    def findService(integrationId: IntegrationId)(implicit db: DB) = iterating(find(IntegrationId, Some(integrationId)))
  }
}
