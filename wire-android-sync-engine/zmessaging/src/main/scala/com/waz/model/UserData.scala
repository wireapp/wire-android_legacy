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
import com.waz.{api, model}
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.ManagedBy.ManagedBy
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.UserPermissions._
import com.waz.service.{SearchKey, SearchQuery}
import com.waz.service.UserSearchService.UserSearchEntry
import com.waz.service.assets.StorageCodecs
import com.waz.utils._
import com.waz.utils.wrappers.{DB, DBCursor}

import scala.concurrent.duration._
import scala.util.Try

case class UserData(override val id:       UserId,
                    teamId:                Option[TeamId]         = None,
                    name:                  Name,
                    email:                 Option[EmailAddress]  = None,
                    phone:                 Option[PhoneNumber]   = None,
                    trackingId:            Option[TrackingId]    = None,
                    picture:               Option[Picture] = None,
                    accent:                Int                   = 0, // accent color id
                    searchKey:             SearchKey,
                    connection:            ConnectionStatus       = ConnectionStatus.Unconnected,
                    connectionLastUpdated: RemoteInstant          = RemoteInstant.Epoch, // server side timestamp of last connection update
                    connectionMessage:     Option[String]         = None, // incoming connection request message
                    conversation:          Option[RConvId]        = None, // remote conversation id with this contact (one-to-one)
                    relation:              Relation               = Relation.Other, //unused - remove in future migration
                    syncTimestamp:         Option[LocalInstant]   = None,
                    verified:              Verification           = Verification.UNKNOWN, // user is verified if he has any otr client, and all his clients are verified
                    deleted:               Boolean                = false,
                    availability:          Availability           = Availability.None,
                    handle:                Option[Handle]         = None,
                    providerId:            Option[ProviderId]     = None,
                    integrationId:         Option[IntegrationId]  = None,
                    expiresAt:             Option[RemoteInstant]  = None,
                    managedBy:             Option[ManagedBy]      = None,
                    fields:                Seq[UserField]         = Seq.empty,
                    permissions:           PermissionsMasks       = (0,0),
                    createdBy:             Option[UserId]         = None) extends Identifiable[UserId] {

  lazy val isConnected: Boolean         = ConnectionStatus.isConnected(connection)
  lazy val hasEmailOrPhone: Boolean     = email.isDefined || phone.isDefined
  lazy val isSelf: Boolean              = connection == ConnectionStatus.Self
  lazy val isAcceptedOrPending: Boolean = connection == ConnectionStatus.Accepted || connection == ConnectionStatus.PendingFromOther || connection == ConnectionStatus.PendingFromUser
  lazy val isVerified: Boolean          = verified == Verification.VERIFIED
  lazy val isAutoConnect: Boolean       = isConnected && ! isSelf && connectionMessage.isEmpty
  lazy val isReadOnlyProfile: Boolean   = managedBy.exists(_ != ManagedBy.Wire) //if none or "Wire", then it's not read only.
  lazy val isWireBot: Boolean           = integrationId.nonEmpty

  def updated(user: UserInfo): UserData = updated(user, withSearchKey = true, permissions = permissions)
  def updated(user: UserInfo, withSearchKey: Boolean, permissions: PermissionsMasks): UserData = copy(
    name          = user.name.getOrElse(name),
    email         = user.email.orElse(email),
    phone         = user.phone.orElse(phone),
    accent        = user.accentId.getOrElse(accent),
    trackingId    = user.trackingId.orElse(trackingId),
    searchKey     = SearchKey(if (withSearchKey) user.name.getOrElse(name).str else ""),
    picture       = user.picture.flatMap(_.collectFirst { case p if p.tag == Medium => PictureUploaded(p.id) }).orElse(picture),
    deleted       = user.deleted,
    providerId    = user.service.map(_.provider).orElse(providerId),
    integrationId = user.service.map(_.id).orElse(integrationId),
    expiresAt     = user.expiresAt.orElse(expiresAt),
    teamId        = user.teamId.orElse(teamId),
    managedBy     = user.managedBy.orElse(managedBy),
    fields        = user.fields.getOrElse(fields),
    handle        = user.handle match {
      case Some(h) if !h.toString.isEmpty => Some(h)
      case _ => handle
    },
    permissions = permissions
  )

  def updated(user: UserSearchEntry): UserData = copy(
    name      = user.name,
    teamId    = user.teamId,
    searchKey = SearchKey(user.name),
    accent    = user.colorId.getOrElse(accent),
    handle    = Some(user.handle)
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

  def isExternal(ourTeamId: Option[TeamId]): Boolean =
    teamId.isDefined && teamId == ourTeamId && decodeBitmask(permissions._1) == ExternalPermissions

  def isInTeam(otherTeamId: Option[TeamId]): Boolean = teamId.isDefined && teamId == otherTeamId

  def matchesQuery(query: SearchQuery): Boolean =
      handle.exists(_.startsWithQuery(query.str)) ||
        (!query.handleOnly && (SearchKey(query.str).isAtTheStartOfAnyWordIn(searchKey) || email.exists(e => query.str.trim.equalsIgnoreCase(e.str))))

  def matchesQuery(query: Option[SearchKey] = None, handleOnly: Boolean = false): Boolean = query match {
    case Some(q) =>
      this.handle.map(_.string).contains(q.asciiRepresentation) || (!handleOnly && q.isAtTheStartOfAnyWordIn(this.searchKey))
    case _ => true
  }
}

trait Picture
case class PictureNotUploaded(id: UploadAssetId) extends Picture
case class PictureUploaded(id: AssetId)          extends Picture

object UserData {

  lazy val Empty = UserData(UserId("EMPTY"), "")

  type ConnectionStatus = api.ConnectionStatus
  object ConnectionStatus {
    import com.waz.api.ConnectionStatus._

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
  def apply(name: String): UserData = UserData(UserId(name), name = Name(name), searchKey = SearchKey.simple(name))

  def apply(id: UserId, name: String): UserData = UserData(id, None, Name(name), None, None, searchKey = SearchKey(name), handle = None)

  def apply(entry: UserSearchEntry): UserData =
    UserData(
      id        = entry.id,
      teamId    = entry.teamId,
      name      = entry.name,
      accent    = entry.colorId.getOrElse(0),
      searchKey = SearchKey(entry.name),
      handle    = Some(entry.handle)
    ) // TODO: improve connection, relation, search level stuff

  def apply(user: UserInfo): UserData = apply(user, withSearchKey = true)

  def apply(user: UserInfo, withSearchKey: Boolean): UserData =
    UserData(user.id, user.name.getOrElse(Name.Empty)).updated(user, withSearchKey, permissions = (0L, 0L))

  implicit object UserDataDao extends Dao[UserData, UserId] with StorageCodecs {
    val Id = id[UserId]('_id, "PRIMARY KEY").apply(_.id)
    val TeamId = opt(id[TeamId]('teamId))(_.teamId)
    val Name = text[model.Name]('name, _.str, model.Name(_))(_.name)
    val Email = opt(emailAddress('email))(_.email)
    val Phone = opt(phoneNumber('phone))(_.phone)
    val TrackingId = opt(id[TrackingId]('tracking_id))(_.trackingId)
    val Picture = opt(text[Picture]('picture, UserPictureCodec.serialize, UserPictureCodec.deserialize))(_.picture)
    val Accent = int('accent)(_.accent)
    val SKey = text[SearchKey]('skey, _.asciiRepresentation, SearchKey.unsafeRestore)(_.searchKey)
    val Conn = text[ConnectionStatus]('connection, _.code, ConnectionStatus(_))(_.connection)
    val ConnTime = date('conn_timestamp)(_.connectionLastUpdated.javaDate) //TODO: Migrate instead?
    val ConnMessage = opt(text('conn_msg))(_.connectionMessage)
    val Conversation = opt(id[RConvId]('conversation))(_.conversation)
    val Rel = text[Relation]('relation, _.name, Relation.valueOf)(_.relation)
    val Timestamp = opt(localTimestamp('timestamp))(_.syncTimestamp)
    val Verified = text[Verification]('verified, _.name, getVerification)(_.verified)
    val Deleted = bool('deleted)(_.deleted)
    val AvailabilityStatus = int[Availability]('availability, _.id, Availability.apply)(_.availability)
    val Handle = opt(handle('handle))(_.handle)
    val ProviderId = opt(id[ProviderId]('provider_id))(_.providerId)
    val IntegrationId = opt(id[IntegrationId]('integration_id))(_.integrationId)
    val ExpiresAt = opt(remoteTimestamp('expires_at))(_.expiresAt)
    val Managed = opt(text[ManagedBy]('managed_by, _.toString, ManagedBy(_)))(_.managedBy)
    val SelfPermissions = long('self_permissions)(_.permissions._1)
    val CopyPermissions = long('copy_permissions)(_.permissions._2)
    val CreatedBy = opt(id[UserId]('created_by))(_.createdBy)

    private def getVerification(name: String): Verification =
      Try(Verification.valueOf(name)).getOrElse(Verification.UNKNOWN)

    override val idCol = Id
    override val table = Table(
      "Users", Id, TeamId, Name, Email, Phone, TrackingId, Picture, Accent, SKey, Conn, ConnTime, ConnMessage,
      Conversation, Rel, Timestamp, Verified, Deleted, AvailabilityStatus, Handle, ProviderId, IntegrationId,
      ExpiresAt, Managed, SelfPermissions, CopyPermissions, CreatedBy // Fields are now lazy-loaded from BE every time the user opens a profile
    )

    override def apply(implicit cursor: DBCursor): UserData = new UserData(
      Id, TeamId, Name, Email, Phone, TrackingId, Picture, Accent, SKey, Conn, RemoteInstant.ofEpochMilli(ConnTime.getTime), ConnMessage,
      Conversation, Rel, Timestamp, Verified, Deleted, AvailabilityStatus, Handle, ProviderId, IntegrationId, ExpiresAt, Managed,
      Seq.empty, (SelfPermissions, CopyPermissions), CreatedBy
    )

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Conversation_id on Users (${Id.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS UserData_search_key on Users (${SKey.name})")
    }

    def get(id: UserId)(implicit db: DB): Option[UserData] = single(find(Id, id)(db))

    override def getCursor(id: UserId)(implicit db: DB): DBCursor = find(Id, id)(db)

    override def delete(id: UserId)(implicit db: DB): Int = db.delete(table.name, Id.name + "=?", Array(id.toString))

    def findByConnectionStatus(status: Set[ConnectionStatus])(implicit db: DB): Managed[Iterator[UserData]] = iteratingMultiple(findInSet(Conn, status))

    def findAll(users: Set[UserId])(implicit db: DB) = iteratingMultiple(findInSet(Id, users))

    def topPeople(implicit db: DB): Managed[Iterator[UserData]] =
      search(s"${Conn.name} = ? and ${Deleted.name} = 0", Array(Conn(ConnectionStatus.Accepted)))

    def recommendedPeople(prefix: String)(implicit db: DB): Managed[Iterator[UserData]] = {
      val query = SearchKey(prefix)
      search(s"""(
                |  (
                |    (
                |      ${SKey.name} LIKE ? OR ${SKey.name} LIKE ?
                |    ) AND (${Rel.name} = '${Rel(Relation.First)}' OR ${Rel.name} = '${Rel(Relation.Second)}' OR ${Rel.name} = '${Rel(Relation.Third)}')
                |  ) OR ${Handle.name} LIKE ?
                |) AND ${Deleted.name} = 0
                |  AND ${Conn.name} != '${Conn(ConnectionStatus.Accepted)}' AND ${Conn.name} != '${Conn(ConnectionStatus.Blocked)}' AND ${Conn.name} != '${Conn(ConnectionStatus.Self)}'
              """.stripMargin,
        Array(s"${query.asciiRepresentation}%", s"% ${query.asciiRepresentation}%", s"%${query.asciiRepresentation}%"))
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

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse(""))).toSet
    }

    def findForTeams(teams: Set[TeamId])(implicit db: DB) = iteratingMultiple(findInSet(TeamId, teams.map(Option(_))))

    def findService(integrationId: IntegrationId)(implicit db: DB) = iterating(find(IntegrationId, Some(integrationId)))
  }
}
