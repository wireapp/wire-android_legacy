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
import com.waz.api.{IConversation, Verification}
import com.waz.db.Col._
import com.waz.db.{Dao, Dao2}
import com.waz.log.LogSE._
import com.waz.log.LogShow.SafeToLog
import com.waz.model
import com.waz.model.ConversationData.{ConversationType, LegalHoldStatus, Link, UnreadCount}
import com.waz.service.SearchKey
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{JsonDecoder, JsonEncoder, _}
import org.json.JSONArray

import java.util.UUID
import scala.concurrent.duration._
import scala.util.Try

final case class ConversationData(override val id:      ConvId                 = ConvId(),
                                  remoteId:             RConvId                = RConvId(),
                                  name:                 Option[Name]           = None,
                                  creator:              UserId                 = UserId(),
                                  convType:             ConversationType       = ConversationType.Group,
                                  team:                 Option[TeamId]         = None,
                                  lastEventTime:        RemoteInstant          = RemoteInstant.Epoch,
                                  isActive:             Boolean                = true,
                                  lastRead:             RemoteInstant          = RemoteInstant.Epoch,
                                  muted:                MuteSet                = MuteSet.AllAllowed,
                                  muteTime:             RemoteInstant          = RemoteInstant.Epoch,
                                  archived:             Boolean                = false,
                                  archiveTime:          RemoteInstant          = RemoteInstant.Epoch,
                                  cleared:              Option[RemoteInstant]  = None,
                                  generatedName:        Name                   = Name.Empty, // deprecated
                                  searchKey:            Option[SearchKey]      = None,
                                  unreadCount:          UnreadCount            = UnreadCount(0, 0, 0, 0, 0),
                                  failedCount:          Int                    = 0,
                                  missedCallMessage:    Option[MessageId]      = None,
                                  incomingKnockMessage: Option[MessageId]      = None,
                                  hidden:               Boolean                = false,
                                  verified:             Verification           = Verification.UNKNOWN,
                                  localEphemeral:       Option[FiniteDuration] = None,
                                  globalEphemeral:      Option[FiniteDuration] = None,
                                  access:               Set[Access]            = Set.empty,
                                  accessRole:           Option[AccessRole]     = None, //option for migration purposes only - at some point we do a fetch and from that point it will always be defined
                                  link:                 Option[Link]           = None,
                                  receiptMode:          Option[Int]            = None,  //Some(1) if both users have RR enabled in a 1-to-1 convo
                                  legalHoldStatus:      LegalHoldStatus        = LegalHoldStatus.Disabled,
                                  domain:               Domain                 = Domain.Empty
                                 ) extends Identifiable[ConvId] {
  lazy val qualifiedId: Option[RConvQualifiedId] = domain.mapOpt(RConvQualifiedId(remoteId, _))

  def getName(): String = name.fold("")(_.str) // still used in Java

  def withFreshSearchKey: ConversationData = copy(searchKey = freshSearchKey)
  def savedOrFreshSearchKey: Option[SearchKey] = searchKey.orElse(freshSearchKey)
  def freshSearchKey: Option[SearchKey] = if (convType == ConversationType.Group) name.map(SearchKey(_)) else None

  lazy val completelyCleared: Boolean = cleared.exists(!_.isBefore(lastEventTime))

  lazy val isManaged: Option[Boolean] = team.map(_ => false) //can be returned to parameter list when we need it.

  lazy val ephemeralExpiration: Option[EphemeralDuration] = (globalEphemeral, localEphemeral) match {
    case (Some(d), _) => Some(ConvExpiry(d)) //global ephemeral takes precedence over local
    case (_, Some(d)) => Some(MessageExpiry(d))
    case _ => None
  }

  def withLastRead(time: RemoteInstant): ConversationData = copy(lastRead = lastRead max time)

  def withCleared(time: RemoteInstant): ConversationData = copy(cleared = Some(cleared.fold(time)(_ max time)))

  def withNewLegalHoldStatus(detectedLegalHoldDevice: Boolean): ConversationData = {
    import LegalHoldStatus._
    copy(legalHoldStatus = if (detectedLegalHoldDevice) Enabled else Disabled)
  }

  def isUnderLegalHold: Boolean = legalHoldStatus != LegalHoldStatus.Disabled

  def messageLegalHoldStatus: Messages.LegalHoldStatus =
    if (isUnderLegalHold) Messages.LegalHoldStatus.ENABLED
    else Messages.LegalHoldStatus.DISABLED

  val isTeamOnly: Boolean = accessRole match {
    case Some(TEAM) if access.contains(Access.INVITE) => true
    case _ => false
  }

  val isGuestRoom: Boolean = accessRole match {
    case Some(NON_ACTIVATED) if access == Set(Access.INVITE, Access.CODE) => true
    case _ => false
  }

  val isWirelessLegacy: Boolean = !(isTeamOnly || isGuestRoom)

  def isMemberFromTeamGuest(teamId: Option[TeamId]): Boolean = team.isDefined && teamId != team

  val isAllAllowed: Boolean = muted.isAllAllowed

  val isAllMuted: Boolean = muted.isAllMuted

  val onlyMentionsAllowed: Boolean = muted.onlyMentionsAllowed

  val readReceiptsAllowed: Boolean = team.isDefined && receiptMode.exists(_ > 0)

  val hasUnreadMessages: Boolean =
    (isAllAllowed && unreadCount.total > 0) || (onlyMentionsAllowed && (unreadCount.mentions > 0 || unreadCount.quotes > 0))

}


/**
 * Conversation user binding.
 */

case class ConversationMemberData(userId: UserId, convId: ConvId, role: String) extends Identifiable[(UserId, ConvId)] {
  override val id: (UserId, ConvId) = (userId, convId)
}

object ConversationMemberData {
  def apply(userId: UserId, convId: ConvId, role: ConversationRole): ConversationMemberData =
    ConversationMemberData(userId, convId, role.label)

  implicit object ConversationMemberDataDao extends Dao2[ConversationMemberData, UserId, ConvId] {
    val UserId = id[UserId]('user_id).apply(_.userId)
    val ConvId = id[ConvId]('conv_id).apply(_.convId)
    val Role = text('role).apply(_.role)

    override val idCol = (UserId, ConvId)
    override val table = Table("ConversationMembers", UserId, ConvId, Role)
    override def apply(implicit cursor: DBCursor): ConversationMemberData = ConversationMemberData(UserId, ConvId, Role)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_conv on ConversationMembers (${ConvId.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_userid on ConversationMembers (${UserId.name})")
    }

    def findForConv(convId: ConvId)(implicit db: DB) = iterating(find(ConvId, convId))
    def findForConvs(convs: Set[ConvId])(implicit db: DB) = iteratingMultiple(findInSet(ConvId, convs))
    def findForUser(userId: UserId)(implicit db: DB) = iterating(find(UserId, userId))
    def findForUsers(users: Set[UserId])(implicit db: DB) = iteratingMultiple(findInSet(UserId, users))
  }
}

object ConversationData {

  val Empty = ConversationData(ConvId(), RConvId(), None, UserId(), IConversation.Type.UNKNOWN)

  case class UnreadCount(normal: Int, call: Int, ping: Int, mentions: Int, quotes: Int) extends SafeToLog {
    def total = normal + call + ping + mentions + quotes
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

  final case class LegalHoldStatus(value: Int)
  object LegalHoldStatus {
    val Disabled = LegalHoldStatus(0)
    val Enabled = LegalHoldStatus(2)
  }

  def getAccessAndRoleForGroupConv(teamOnly: Boolean, teamId: Option[TeamId]): (Set[Access], AccessRole) = {
    teamId match {
      case Some(_) if teamOnly => (Set(INVITE), TEAM)
      case Some(_)             => (Set(INVITE, CODE), NON_ACTIVATED)
      case _                   => (Set(INVITE), ACTIVATED)
    }
  }

  case class Link(url: String)

  implicit object ConversationDataDao extends Dao[ConversationData, ConvId] {
    val Id                  = id[ConvId]('_id, "PRIMARY KEY").apply(_.id)
    val RemoteId            = id[RConvId]('remote_id).apply(_.remoteId)
    val Name                = opt(text[model.Name]('name, _.str, model.Name(_)))(_.name.filterNot(_.isEmpty))
    val Creator             = id[UserId]('creator).apply(_.creator)
    val ConvType            = int[ConversationType]('conv_type, _.id, ConversationType(_))(_.convType)
    val Team                = opt(id[TeamId]('team))(_.team)
    val IsManaged           = opt(bool('is_managed))(_.isManaged)
    val LastEventTime       = remoteTimestamp('last_event_time)(_.lastEventTime)
    val IsActive            = bool('is_active)(_.isActive)
    val LastRead            = remoteTimestamp('last_read)(_.lastRead)
    val MutedStatus         = int('muted_status)(_.muted.toInt)
    val MutedTime           = remoteTimestamp('mute_time)(_.muteTime)
    val Archived            = bool('archived)(_.archived)
    val ArchivedTime        = remoteTimestamp('archive_time)(_.archiveTime)
    val Cleared             = opt(remoteTimestamp('cleared))(_.cleared)
    val GeneratedName       = text[model.Name]('generated_name, _.str, model.Name(_))(_.generatedName)
    val SKey                = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))(_.searchKey)
    val UnreadCount         = int('unread_count)(_.unreadCount.normal)
    val UnreadCallCount     = int('unread_call_count)(_.unreadCount.call)
    val UnreadPingCount     = int('unread_ping_count)(_.unreadCount.ping)
    val FailedCount         = int('unsent_count)(_.failedCount)
    val Hidden              = bool('hidden)(_.hidden)
    val MissedCall          = opt(id[MessageId]('missed_call))(_.missedCallMessage)
    val IncomingKnock       = opt(id[MessageId]('incoming_knock))(_.incomingKnockMessage)
    val Verified            = text[Verification]('verified, _.name, getVerification)(_.verified)
    val LocalEphemeral      = opt(finiteDuration('ephemeral))(_.localEphemeral)
    val GlobalEphemeral     = opt(finiteDuration('global_ephemeral))(_.globalEphemeral)
    val Access              = set[Access]('access, JsonEncoder.encodeAccess(_).toString(), v => JsonDecoder.array[Access](new JSONArray(v), (arr: JSONArray, i: Int) => IConversation.Access.valueOf(arr.getString(i).toUpperCase)).toSet)(_.access)
    val AccessRole          = opt(text[IConversation.AccessRole]('access_role, JsonEncoder.encodeAccessRole, v => IConversation.AccessRole.valueOf(v.toUpperCase)))(_.accessRole)
    val Link                = opt(text[Link]('link, _.url, v => ConversationData.Link(v)))(_.link)
    val UnreadMentionsCount = int('unread_mentions_count)(_.unreadCount.mentions)
    val UnreadQuotesCount   = int('unread_quote_count)(_.unreadCount.quotes)
    val ReceiptMode         = opt(int('receipt_mode))(_.receiptMode)
    val LegalHoldStatus     = int[LegalHoldStatus]('legal_hold_status, _.value, ConversationData.LegalHoldStatus.apply)(_.legalHoldStatus)
    val Domain              = opt(text[model.Domain]('domain, _.str, model.Domain(_)))(c => Some(c.domain))

    private def getVerification(name: String): Verification =
      Try(Verification.valueOf(name)).getOrElse(Verification.UNKNOWN)

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
      MutedStatus,
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
      LocalEphemeral,
      GlobalEphemeral,
      UnreadCallCount,
      UnreadPingCount,
      Access,
      AccessRole,
      Link,
      UnreadMentionsCount,
      UnreadQuotesCount,
      ReceiptMode,
      LegalHoldStatus,
      Domain
    )

    override def apply(implicit cursor: DBCursor): ConversationData = {
      def orEmpty(domain: Option[model.Domain]): model.Domain = domain.getOrElse(model.Domain.Empty)
      ConversationData(
        Id,
        RemoteId,
        Name,
        Creator,
        ConvType,
        Team,
        LastEventTime,
        IsActive,
        LastRead,
        MuteSet(MutedStatus),
        MutedTime,
        Archived,
        ArchivedTime,
        Cleared,
        GeneratedName,
        SKey,
        ConversationData.UnreadCount(UnreadCount, UnreadCallCount, UnreadPingCount, UnreadMentionsCount, UnreadQuotesCount),
        FailedCount,
        MissedCall,
        IncomingKnock,
        Hidden,
        Verified,
        LocalEphemeral,
        GlobalEphemeral,
        Access,
        AccessRole,
        Link,
        ReceiptMode,
        LegalHoldStatus,
        orEmpty(Domain)
      )
    }

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
      """.stripMargin))

    def allConversations(implicit db: DB) =
      db.rawQuery(s"SELECT *, ${ConvType.name} = ${Self.id} as is_self, ${ConvType.name} = ${Incoming.id} as is_incoming, ${Archived.name} = 1 as is_archived FROM ${table.name} WHERE ${Hidden.name} = 0 ORDER BY is_self DESC, is_archived ASC, is_incoming DESC, ${LastEventTime.name} DESC")

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

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse("")))
    }

    def findByTeams(teams: Set[TeamId])(implicit db: DB) = iteratingMultiple(findInSet(Team, teams.map(Option(_))))

    def findByRemoteId(remoteId: RConvId, jobTag: Option[UUID])(implicit db: DB) = {
      verbose(l"SSSTAGES<JOB:$jobTag> ConversationData.findByRemoteId: $remoteId")
      iterating(find(RemoteId, remoteId, jobTag), jobTag)
    }
    def findByRemoteIds(remoteIds: Set[RConvId])(implicit db: DB) = iteratingMultiple(findInSet(RemoteId, remoteIds))
  }
}

