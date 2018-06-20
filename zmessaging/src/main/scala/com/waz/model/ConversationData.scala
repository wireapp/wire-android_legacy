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

import com.waz.api.IConversation.Access.{CODE, INVITE}
import com.waz.api.IConversation.AccessRole._
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.{EphemeralExpiration, IConversation, Verification}
import com.waz.db.Col._
import com.waz.db.{Dao, Dao2}
import com.waz.model.ConversationData.{Link, ConversationType, UnreadCount}
import com.waz.service.SearchKey
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{JsonDecoder, JsonEncoder, _}
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

case class ConversationData(id:                   ConvId              = ConvId(),
                            remoteId:             RConvId             = RConvId(),
                            name:                 Option[String]      = None,
                            creator:              UserId              = UserId(),
                            convType:             ConversationType    = ConversationType.Group,
                            team:                 Option[TeamId]      = None,
                            isManaged:            Option[Boolean]     = None,
                            lastEventTime:        Instant             = Instant.now(),
                            isActive:             Boolean             = true,
                            lastRead:             Instant             = Instant.EPOCH,
                            muted:                Boolean             = false,
                            muteTime:             Instant             = Instant.EPOCH,
                            archived:             Boolean             = false,
                            archiveTime:          Instant             = Instant.EPOCH,
                            cleared:              Option[Instant]     = None,
                            generatedName:        String              = "",
                            searchKey:            Option[SearchKey]   = None,
                            unreadCount:          UnreadCount         = UnreadCount(0, 0, 0),
                            failedCount:          Int                 = 0,
                            missedCallMessage:    Option[MessageId]   = None,
                            incomingKnockMessage: Option[MessageId]   = None,
                            hidden:               Boolean             = false,
                            verified:             Verification        = Verification.UNKNOWN,
                            ephemeral:            EphemeralExpiration = EphemeralExpiration.NONE,
                            access:               Set[Access]         = Set.empty,
                            accessRole:           Option[AccessRole]  = None,
                            link:                 Option[Link]        = None) {

  def displayName = if (convType == ConversationType.Group) name.getOrElse(generatedName) else generatedName

  def withFreshSearchKey = copy(searchKey = freshSearchKey)
  def savedOrFreshSearchKey = searchKey.orElse(freshSearchKey)
  def freshSearchKey = if (convType == ConversationType.Group) name map SearchKey else None

  lazy val completelyCleared = cleared.exists(!_.isBefore(lastEventTime))

  def withLastRead(time: Instant) = copy(lastRead = lastRead max time)

  def withCleared(time: Instant) = copy(cleared = Some(cleared.fold(time)(_ max time)))

  def updated(d: ConversationData): Option[ConversationData] = {
    val ct = if (ConversationType.isOneToOne(convType) && d.convType != ConversationType.OneToOne) convType else d.convType

    val updated = copy(
      remoteId = d.remoteId,
      name = d.name,
      creator = d.creator,
      team = d.team,
      convType = ct,
      lastEventTime = lastEventTime max d.lastEventTime,
      isActive = d.isActive,
      lastRead = lastRead max d.lastRead,
      muted = d.muted,
      muteTime = d.muteTime,
      archived = d.archived,
      cleared = d.cleared.map(c => cleared.fold(c)(_ max c)),
      searchKey = d.searchKey,
      access = d.access,
      accessRole = d.accessRole)

    if (updated == this) None else Some(updated)
  }

  def isTeamOnly: Boolean = accessRole match {
    case Some(TEAM) if access.contains(Access.INVITE) => true
    case _ => false
  }

  def isGuestRoom: Boolean = accessRole match {
    case Some(NON_ACTIVATED) if access == Set(Access.INVITE, Access.CODE) => true
    case _ => false
  }

  def isWirelessLegacy: Boolean = !(isTeamOnly || isGuestRoom)

  def isUserAllowed(userData: UserData): Boolean =
    !(userData.isGuest(team) && isTeamOnly)

  def isMemberFromTeamGuest(teamId: Option[TeamId]): Boolean = team.isDefined && teamId != team
}

/**
 * Conversation user binding.
 */
case class ConversationMemberData(userId: UserId, convId: ConvId)

object ConversationData {

  val Empty = ConversationData(ConvId(), RConvId(), None, UserId(), IConversation.Type.UNKNOWN)

  case class UnreadCount(normal: Int, call: Int, ping: Int) {
    def total = normal + call + ping
    def messages = normal + ping
  }

  // total (!) ordering for use in ordered sets; handwritten (instead of e.g. derived from tuples) to avoid allocations
  implicit val ConversationDataOrdering: Ordering[ConversationData] = new Ordering[ConversationData] {
    override def compare(b: ConversationData, a: ConversationData): Int =
      if (a.id == b.id) 0
      else {
        val c = a.lastEventTime.compareTo(b.lastEventTime)
        if (c != 0) c
        else a.id.str.compareTo(b.id.str)
      }
  }

  type ConversationType = IConversation.Type
  object ConversationType {
    val Unknown = IConversation.Type.UNKNOWN
    val Group = IConversation.Type.GROUP
    val OneToOne = IConversation.Type.ONE_TO_ONE
    val Self = IConversation.Type.SELF
    val WaitForConnection = IConversation.Type.WAIT_FOR_CONNECTION
    val Incoming = IConversation.Type.INCOMING_CONNECTION

    def apply(id: Int) = IConversation.Type.withId(id)

    def isOneToOne(tp: IConversation.Type) = tp == OneToOne || tp == WaitForConnection || tp == Incoming

    def values = Set(Unknown, Group, OneToOne, Self, WaitForConnection, Incoming)
  }

  def getAccessAndRoleForGroupConv(teamOnly: Boolean, teamId: Option[TeamId]): (Set[Access], AccessRole) = {
    teamId match {
      case Some(_) if teamOnly => (Set(INVITE), TEAM)
      case Some(_)             => (Set(INVITE, CODE), NON_ACTIVATED)
      case _                   => (Set(INVITE), ACTIVATED)
    }
  }

  case class Link(url: String)

  implicit lazy val Decoder: JsonDecoder[ConversationData] = new JsonDecoder[ConversationData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): ConversationData = ConversationData(
      id                   = 'id,
      remoteId             = 'remoteId,
      name                 = decodeOptString('name),
      creator              = 'creator,
      convType             = ConversationType('convType),
      team                 = decodeOptId[TeamId]('team),
      isManaged            = decodeOptBoolean('is_managed),
      lastEventTime        = decodeInstant('lastEventTime),
      isActive             = decodeBool('is_active),
      lastRead             = decodeInstant('lastReadTime),
      muted                = 'muted,
      muteTime             = decodeInstant('muteTime),
      archived             = 'archived,
      archiveTime          = decodeInstant('archiveTime),
      cleared              = decodeOptInstant('cleared),
      generatedName        = 'generatedName,
      searchKey            = decodeOptString('name) map SearchKey,
      unreadCount          = UnreadCount('unreadCount, 'unreadCallCount, 'unreadPingCount),
      failedCount          = 'failedCount,
      missedCallMessage    = decodeOptMessageId('missedCallMessage),
      incomingKnockMessage = decodeOptMessageId('incomingKnockMessage),
      hidden               = 'hidden,
      verified             = decodeOptString('verified).fold(Verification.UNKNOWN)(Verification.valueOf),
      ephemeral            = EphemeralExpiration.getForMillis(decodeLong('ephemeral)),
      access               = 'access,
      accessRole           = 'accessRole,
      link                 = decodeOptString('link).map(Link)
    )
  }

  implicit lazy val Encoder: JsonEncoder[ConversationData] = new JsonEncoder[ConversationData] {
    import JsonEncoder._
    override def apply(c: ConversationData): JSONObject = JsonEncoder { o =>
      o.put("id", c.id.str)
      o.put("remoteId", c.remoteId.str)
      c.name foreach (o.put("name", _))
      o.put("creator", c.creator.str)
      o.put("convType", c.convType.id)
      c.team.foreach(v => o.put("team", v.str))
      c.isManaged.foreach(v => o.put("is_managed", v))
      o.put("lastEventTime", c.lastEventTime.toEpochMilli)
      o.put("is_active", c.isActive)
      o.put("lastReadTime", c.lastRead.toEpochMilli)
      o.put("muted", c.muted)
      o.put("muteTime", c.muteTime.toEpochMilli)
      o.put("archived", c.archived)
      o.put("archiveTime", c.archiveTime.toEpochMilli)
      c.cleared.foreach(cl => o.put("cleared", cl.toEpochMilli))
      o.put("generatedName", c.generatedName)
      o.put("unreadCount", c.unreadCount.normal)
      o.put("unreadCallCount", c.unreadCount.call)
      o.put("unreadPingCount", c.unreadCount.ping)
      o.put("failedCount", c.failedCount)
      c.missedCallMessage foreach (id => o.put("missedCallMessage", id.str))
      c.incomingKnockMessage foreach (id => o.put("incomingKnockMessage", id.str))
      o.put("hidden", c.hidden)
      o.put("trusted", c.verified)
      o.put("ephemeral", c.ephemeral.milliseconds)
      o.put("access", encodeAccess(c.access))
      o.put("access_role", encodeAccessRoleOpt(c.accessRole))
      c.link.foreach(l => o.put("link", l.url))
    }
  }

  implicit object ConversationDataDao extends Dao[ConversationData, ConvId] {
    val Id               = id[ConvId]('_id, "PRIMARY KEY").apply(_.id)
    val RemoteId         = id[RConvId]('remote_id).apply(_.remoteId)
    val Name             = opt(text('name))(_.name.filterNot(_.isEmpty))
    val Creator          = id[UserId]('creator).apply(_.creator)
    val ConvType         = int[ConversationType]('conv_type, _.id, ConversationType(_))(_.convType)
    val Team             = opt(id[TeamId]('team))(_.team)
    val IsManaged        = opt(bool('is_managed))(_.isManaged)
    val LastEventTime    = timestamp('last_event_time)(_.lastEventTime)
    val IsActive         = bool('is_active)(_.isActive)
    val LastRead         = timestamp('last_read)(_.lastRead)
    val Muted            = bool('muted)(_.muted)
    val MutedTime        = timestamp('mute_time)(_.muteTime)
    val Archived         = bool('archived)(_.archived)
    val ArchivedTime     = timestamp('archive_time)(_.archiveTime)
    val Cleared          = opt(timestamp('cleared))(_.cleared)
    val GeneratedName    = text('generated_name)(_.generatedName)
    val SKey             = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))(_.searchKey)
    val UnreadCount      = int('unread_count)(_.unreadCount.normal)
    val UnreadCallCount  = int('unread_call_count)(_.unreadCount.call)
    val UnreadPingCount  = int('unread_ping_count)(_.unreadCount.ping)
    val FailedCount      = int('unsent_count)(_.failedCount)
    val Hidden           = bool('hidden)(_.hidden)
    val MissedCall       = opt(id[MessageId]('missed_call))(_.missedCallMessage)
    val IncomingKnock    = opt(id[MessageId]('incoming_knock))(_.incomingKnockMessage)
    val Verified         = text[Verification]('verified, _.name, Verification.valueOf)(_.verified)
    val Ephemeral        = long[EphemeralExpiration]('ephemeral, _.milliseconds, EphemeralExpiration.getForMillis)(_.ephemeral)
    val Access           = set[Access]('access, JsonEncoder.encodeAccess(_).toString(), v => JsonDecoder.array[Access](new JSONArray(v), (arr: JSONArray, i: Int) => IConversation.Access.valueOf(arr.getString(i).toUpperCase)).toSet)(_.access)
    val AccessRole       = opt(text[IConversation.AccessRole]('access_role, JsonEncoder.encodeAccessRole, v => IConversation.AccessRole.valueOf(v.toUpperCase)))(_.accessRole)
    val Link             = opt(text[Link]('link, _.url, v => ConversationData.Link(v)))(_.link)

    override val idCol = Id
    override val table = Table(
      "Conversations",
      Id,
      RemoteId,
      Name,
      Creator,
      ConvType,
      Team,
      IsManaged,
      LastEventTime,
      IsActive,
      LastRead,
      Muted,
      MutedTime,
      Archived,
      ArchivedTime,
      Cleared,
      GeneratedName,
      SKey,
      UnreadCount,
      FailedCount,
      Hidden,
      MissedCall,
      IncomingKnock,
      Verified,
      Ephemeral,
      UnreadCallCount,
      UnreadPingCount,
      Access,
      AccessRole,
      Link)

    override def apply(implicit cursor: DBCursor): ConversationData =
      ConversationData(
        Id,
        RemoteId,
        Name,
        Creator,
        ConvType,
        Team,
        IsManaged,
        LastEventTime,
        IsActive,
        LastRead,
        Muted,
        MutedTime,
        Archived,
        ArchivedTime,
        Cleared,
        GeneratedName,
        SKey,
        ConversationData.UnreadCount(UnreadCount, UnreadCallCount, UnreadPingCount),
        FailedCount,
        MissedCall,
        IncomingKnock,
        Hidden,
        Verified,
        Ephemeral,
        Access,
        AccessRole,
        Link)

    import com.waz.model.ConversationData.ConversationType._

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Conversation_search_key on Conversations (${SKey.name})")
    }

    def establishedConversations(implicit db: DB) = iterating(db.rawQuery(
      s"""SELECT *
         |  FROM ${table.name}
         | WHERE (${ConvType.name} = ${ConvType(ConversationType.OneToOne)} OR ${ConvType.name} = ${ConvType(ConversationType.Group)})
         |   AND ${IsActive.name} = ${IsActive(true)}
         |   AND ${Hidden.name} = 0
      """.stripMargin, null))

    def allConversations(implicit db: DB) =
      db.rawQuery(s"SELECT *, ${ConvType.name} = ${Self.id} as is_self, ${ConvType.name} = ${Incoming.id} as is_incoming, ${Archived.name} = 1 as is_archived FROM ${table.name} WHERE ${Hidden.name} = 0 ORDER BY is_self DESC, is_archived ASC, is_incoming DESC, ${LastEventTime.name} DESC", null)

    import ConversationMemberData.{ConversationMemberDataDao => CM}
    import UserData.{UserDataDao => U}

    def search(prefix: SearchKey, self: UserId, handleOnly: Boolean, teamId: Option[TeamId])(implicit db: DB) = {
      val select =
        s"""SELECT c.* ${if (teamId.isDefined) ", COUNT(*)" else ""}
            |  FROM ${table.name} c
            |  JOIN ${CM.table.name} cm ON cm.${CM.ConvId.name} = c.${Id.name}
            |  JOIN ${U.table.name} u ON cm.${CM.UserId.name} = u.${U.Id.name}
            | WHERE c.${ConvType.name} = ${ConvType(ConversationType.Group)}
            |   AND c.${Hidden.name} = ${Hidden(false)}
            |   AND u.${U.Id.name} != '${U.Id(self)}'
            |   AND (c.${Cleared.name} IS NULL OR c.${Cleared.name} < c.${LastEventTime.name} OR c.${IsActive.name} = ${IsActive(true)})""".stripMargin
      val handleCondition =
        if (handleOnly){
          s"""AND u.${U.Handle.name} LIKE '${prefix.asciiRepresentation}%'""".stripMargin
        } else {
          s"""AND (    c.${SKey.name}   LIKE '${SKey(Some(prefix))}%'
              |     OR c.${SKey.name}   LIKE '% ${SKey(Some(prefix))}%'
              |     OR u.${U.SKey.name} LIKE '${U.SKey(prefix)}%'
              |     OR u.${U.SKey.name} LIKE '% ${U.SKey(prefix)}%'
              |     OR u.${U.Handle.name} LIKE '%${prefix.asciiRepresentation}%')""".stripMargin
        }
      val teamCondition = teamId.map(_ =>
        s"""AND c.${Team.name} = ${Team(teamId)}
           | GROUP BY cm.${CM.ConvId.name}
           | HAVING COUNT(*) > 2
         """.stripMargin)

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse(""), null))
    }

    def findByTeams(teams: Set[TeamId])(implicit db: DB) = iterating(findInSet(Team, teams.map(Option(_))))
  }
}

object ConversationMemberData {

  implicit object ConversationMemberDataDao extends Dao2[ConversationMemberData, UserId, ConvId] {
    val UserId = id[UserId]('user_id).apply(_.userId)
    val ConvId = id[ConvId]('conv_id).apply(_.convId)

    override val idCol = (UserId, ConvId)
    override val table = Table("ConversationMembers", UserId, ConvId)
    override def apply(implicit cursor: DBCursor): ConversationMemberData = ConversationMemberData(UserId, ConvId)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_conv on ConversationMembers (${ConvId.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_userid on ConversationMembers (${UserId.name})")
    }

    def findForConv(convId: ConvId)(implicit db: DB) = iterating(find(ConvId, convId))
    def findForConvs(convs: Set[ConvId])(implicit db: DB) = iterating(findInSet(ConvId, convs))
    def findForUser(userId: UserId)(implicit db: DB) = iterating(find(UserId, userId))
    def findForUsers(users: Set[UserId])(implicit db: DB) = iterating(findInSet(UserId, users))
  }
}
