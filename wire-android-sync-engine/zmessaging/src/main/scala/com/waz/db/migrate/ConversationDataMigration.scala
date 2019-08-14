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
package com.waz.db.migrate

import com.waz.api.{EphemeralExpiration, Verification}
import com.waz.db.Col._
import com.waz.db._
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.utils._
import com.waz.utils.wrappers.{DB, DBCursor}
import org.threeten.bp.Instant

object ConversationDataMigration {

  lazy val v64 = { db: DB =>

    val moveConvs = new TableMigration(TableDesc("Conversations", Columns.v63.all), TableDesc("Conversations_tmp", Columns.v64.all)) {
      import Columns.{v63 => src, v64 => dst}

      override val bindings: Seq[Binder] = Seq(
        dst.Id := src.Id,
        dst.RemoteId := src.RemoteId,
        dst.Name := src.Name,
        dst.Creator := src.Creator,
        dst.ConvType := src.ConvType,
        dst.LastEventTime := src.LastEventTime.andThen(_.instant),
        dst.LastEvent := src.LastEvent,
        dst.Status := src.Status,
        dst.StatusTime := src.StatusTime.andThen(_.instant),
        dst.LastRead := { c: DBCursor =>
          src.LastReadTime(c).getOrElse(src.LastEventTime(c).instant)
        },
        dst.Muted := src.Muted,
        dst.MutedTime := src.MutedTime.andThen(_.fold(Instant.EPOCH)(_.instant)),
        dst.Archived := src.Archived.andThen(_.isDefined),
        dst.ArchivedTime := src.ArchivedTime.andThen(_.getOrElse(Instant.EPOCH)),
        dst.Cleared := src.ClearedTime.andThen(_.getOrElse(Instant.EPOCH)),
        dst.GeneratedName := src.GeneratedName,
        dst.SKey := src.SKey,
        dst.UnreadCount := src.UnreadCount,
        dst.FailedCount := src.FailedCount,
        dst.HasVoice := src.HasVoice,
        dst.UnjoinedCall := src.UnjoinedCall,
        dst.MissedCall := src.MissedCall,
        dst.IncomingKnock := src.IncomingKnock,
        dst.RenameEvent := src.RenameEvent,
        dst.VoiceMuted := src.VoiceMuted,
        dst.Hidden := src.Hidden,
        dst.Verified := src.Verified
      )
    }

    db.execSQL("DROP TABLE IF EXISTS Conversations_tmp")
    moveConvs.migrate(db)
    db.execSQL("ALTER TABLE Conversations RENAME TO Conversations_old")
    db.execSQL("ALTER TABLE Conversations_tmp RENAME TO Conversations")
    db.execSQL("DROP TABLE Conversations_old")
  }

  lazy val v72 = { implicit db: DB =>
    val table = TableDesc("Conversations_tmp", Columns.v72.all)

    inTransaction { tr: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS Conversations_tmp")
      db.execSQL(table.createSql)

      // copy all data
      db.execSQL("INSERT INTO Conversations_tmp SELECT _id, remote_id, name, creator, conv_type, last_event_time, status, " +
        "status_time, last_read, muted, mute_time, archived, archive_time, cleared, generated_name, search_key, " +
        "unread_count, unsent_count, has_voice, voice_muted, hidden, missed_call, incoming_knock, (CASE conv_type WHEN 0 THEN last_event_time ELSE 0 END), unjoined_call, verified FROM Conversations")

      db.execSQL("DROP TABLE Conversations")
      db.execSQL("ALTER TABLE Conversations_tmp RENAME TO Conversations")
    }
  }

  lazy val v76 = { implicit db: DB =>
    db.execSQL("ALTER TABLE Conversations ADD COLUMN ephemeral INTEGER DEFAULT 0")
  }

  lazy val v79 = { implicit db: DB =>
    db.execSQL(s"CREATE INDEX IF NOT EXISTS Conversation_search_key on Conversations (search_key)")
  }

  lazy val v82 = { implicit db: DB =>
    val table = TableDesc("Conversations_tmp", Columns.v82.all)

    inTransaction { tr: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS Conversations_tmp")
      db.execSQL(table.createSql)

      // copy all data
      db.execSQL("INSERT INTO Conversations_tmp SELECT _id, remote_id, name, creator, conv_type, last_event_time, status, " +
        "last_read, muted, mute_time, archived, archive_time, cleared, generated_name, search_key, unread_count, " +
        "unsent_count, has_voice, voice_muted, hidden, missed_call, incoming_knock, (CASE conv_type WHEN 0 THEN last_event_time ELSE 0 END), " +
        "unjoined_call, verified, ephemeral FROM Conversations")

      db.execSQL("DROP TABLE Conversations")
      db.execSQL("ALTER TABLE Conversations_tmp RENAME TO Conversations")
    }
  }

  lazy val v90 = { implicit db: DB =>

    val v90Cols = {
      Seq(
        id[ConvId]('_id, "PRIMARY KEY"),
        id[RConvId]('remote_id),
        opt(text('name)),
        id[UserId]('creator),
        opt(id[TeamId]('team)),
        opt(bool('is_managed)),
        int[ConversationType]('conv_type, _.id, ConversationType(_)),
        timestamp('last_event_time),
        bool('is_active),
        timestamp('last_read),
        bool('muted),
        timestamp('mute_time),
        bool('archived),
        timestamp('archive_time),
        timestamp('cleared),
        text('generated_name),
        opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore)),
        int('unread_count),
        int('unsent_count),
        bool('hidden),
        opt(id[MessageId]('missed_call)),
        opt(id[MessageId]('incoming_knock)),
        text[Verification]('verified, _.name, Verification.valueOf),
        long[EphemeralExpiration]('ephemeral, _.milliseconds, EphemeralExpiration.getForMillis)
      )
    }

    val table = TableDesc("Conversations_tmp", v90Cols)

    inTransaction { _: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS Conversations_tmp")
      db.execSQL(table.createSql)

      // copy all data
      db.execSQL(
        """
          |INSERT INTO Conversations_tmp SELECT
          |   _id,
          |   remote_id,
          |   name,
          |   creator,
          |   team,
          |   is_managed,
          |   conv_type,
          |   last_event_time,
          |   (CASE status WHEN 1 THEN 0 ELSE 1 END),
          |   last_read,
          |   muted,
          |   mute_time,
          |   archived,
          |   archive_time,
          |   cleared,
          |   generated_name,
          |   search_key,
          |   unread_count,
          |   unsent_count,
          |   hidden,
          |   missed_call,
          |   incoming_knock,
          |   verified,
          |   ephemeral
          |FROM Conversations""".stripMargin)

      db.execSQL("DROP TABLE Conversations")
      db.execSQL("ALTER TABLE Conversations_tmp RENAME TO Conversations")
    }
  }

  object Columns {

    object v63 {
      val Id = id[ConvId]('_id, "PRIMARY KEY")
      val RemoteId = id[RConvId]('remote_id)
      val Name = opt(text('name))
      val Creator = id[UserId]('creator)
      val ConvType = int[ConversationType]('conv_type, _.id, ConversationType(_))
      val LastEventTime = date('last_event_time)
      val LastEvent = text('last_event)
      val Status = int('status)
      val StatusTime = date('status_time)
      val StatusRef = text('status_ref)
      val LastRead = opt(text('last_read))
      val LastReadTime = opt(timestamp('last_read_time))
      val Muted = bool('muted)
      val MutedTime = opt(date('mute_time))
      val Archived = opt(text('archived))
      val ArchivedTime = opt(timestamp('archived_time))
      val Cleared = opt(text('cleared))
      val ClearedTime = opt(timestamp('cleared_time))
      val GeneratedName = text('generated_name)
      val SKey = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))
      val UnreadCount = int('unread_count)
      val FailedCount = int('unsent_count)
      val HasVoice = bool('has_voice)
      val UnjoinedCall = bool('unjoined_call)
      val MissedCall = opt(id[MessageId]('missed_call))
      val IncomingKnock = opt(id[MessageId]('incoming_knock))
      val RenameEvent = opt(text('rename_event))
      val VoiceMuted = bool('voice_muted)
      val Hidden = bool('hidden)
      val Verified = text[Verification]('verified, _.name, Verification.valueOf)

      val all = Seq(Id, RemoteId, Name, Creator, ConvType, LastEventTime, LastEvent, Status, StatusTime, StatusRef, LastRead, LastReadTime, Muted, MutedTime, Archived, ArchivedTime, Cleared, ClearedTime, GeneratedName, SKey, UnreadCount, FailedCount, HasVoice, VoiceMuted, Hidden, MissedCall, IncomingKnock, RenameEvent, UnjoinedCall, Verified)
    }

    object v64 {
      val Id = id[ConvId]('_id, "PRIMARY KEY")
      val RemoteId = id[RConvId]('remote_id)
      val Name = opt(text('name))
      val Creator = id[UserId]('creator)
      val ConvType = int[ConversationType]('conv_type, _.id, ConversationType(_))
      val LastEventTime = timestamp('last_event_time)
      val LastEvent = text('last_event)
      val Status = int('status)
      val StatusTime = timestamp('status_time)
      val LastRead = timestamp('last_read)
      val Muted = bool('muted)
      val MutedTime = timestamp('mute_time)
      val Archived = bool('archived)
      val ArchivedTime = timestamp('archive_time)
      val Cleared = timestamp('cleared)
      val GeneratedName = text('generated_name)
      val SKey = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))
      val UnreadCount = int('unread_count)
      val FailedCount = int('unsent_count)
      val HasVoice = bool('has_voice)
      val UnjoinedCall = bool('unjoined_call)
      val MissedCall = opt(id[MessageId]('missed_call))
      val IncomingKnock = opt(id[MessageId]('incoming_knock))
      val RenameEvent = opt(text('rename_event))
      val VoiceMuted = bool('voice_muted)
      val Hidden = bool('hidden)
      val Verified = text[Verification]('verified, _.name, Verification.valueOf)

      val all = Seq(Id, RemoteId, Name, Creator, ConvType, LastEventTime, LastEvent, Status, StatusTime, LastRead, Muted, MutedTime, Archived, ArchivedTime, Cleared, GeneratedName, SKey, UnreadCount, FailedCount, HasVoice, VoiceMuted, Hidden, MissedCall, IncomingKnock, RenameEvent, UnjoinedCall, Verified)
    }

    object v72 {
      val Id            = id[ConvId]('_id, "PRIMARY KEY")
      val RemoteId      = id[RConvId]('remote_id)
      val Name          = opt(text('name))
      val Creator       = id[UserId]('creator)
      val ConvType      = int[ConversationType]('conv_type, _.id, ConversationType(_))
      val LastEventTime = timestamp('last_event_time)
      val Status        = int('status)
      val StatusTime    = timestamp('status_time)
      val LastRead      = timestamp('last_read)
      val Muted         = bool('muted)
      val MutedTime     = timestamp('mute_time)
      val Archived      = bool('archived)
      val ArchivedTime  = timestamp('archive_time)
      val Cleared       = timestamp('cleared)
      val GeneratedName = text('generated_name)
      val SKey          = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))
      val UnreadCount   = int('unread_count)
      val FailedCount   = int('unsent_count)
      val HasVoice      = bool('has_voice)
      val UnjoinedCall  = bool('unjoined_call)
      val MissedCall    = opt(id[MessageId]('missed_call))
      val IncomingKnock = opt(id[MessageId]('incoming_knock))
      val RenameEvent   = timestamp('rename_event_time)
      val VoiceMuted    = bool('voice_muted)
      val Hidden        = bool('hidden)
      val Verified      = text[Verification]('verified, _.name, Verification.valueOf)

      val all = Seq(Id, RemoteId, Name, Creator, ConvType, LastEventTime, Status, StatusTime, LastRead, Muted, MutedTime, Archived, ArchivedTime, Cleared, GeneratedName, SKey, UnreadCount, FailedCount, HasVoice, VoiceMuted, Hidden, MissedCall, IncomingKnock, RenameEvent, UnjoinedCall, Verified)
    }

    object v82 {
      val Id            = id[ConvId]('_id, "PRIMARY KEY")
      val RemoteId      = id[RConvId]('remote_id)
      val Name          = opt(text('name))
      val Creator       = id[UserId]('creator)
      val ConvType      = int[ConversationType]('conv_type, _.id, ConversationType(_))
      val LastEventTime = timestamp('last_event_time)
      val Status        = int('status)
      val LastRead      = timestamp('last_read)
      val Muted         = bool('muted)
      val MutedTime     = timestamp('mute_time)
      val Archived      = bool('archived)
      val ArchivedTime  = timestamp('archive_time)
      val Cleared       = timestamp('cleared)
      val GeneratedName = text('generated_name)
      val SKey          = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))
      val UnreadCount   = int('unread_count)
      val FailedCount   = int('unsent_count)
      val HasVoice      = bool('has_voice)
      val UnjoinedCall  = bool('unjoined_call)
      val MissedCall    = opt(id[MessageId]('missed_call))
      val IncomingKnock = opt(id[MessageId]('incoming_knock))
      val RenameEvent   = timestamp('rename_event_time)
      val VoiceMuted    = bool('voice_muted)
      val Hidden        = bool('hidden)
      val Verified      = text[Verification]('verified, _.name, Verification.valueOf)
      val Ephemeral     = long('ephemeral)

      val all = Seq(Id, RemoteId, Name, Creator, ConvType, LastEventTime, Status, LastRead, Muted, MutedTime, Archived, ArchivedTime, Cleared, GeneratedName, SKey, UnreadCount, FailedCount, HasVoice, VoiceMuted, Hidden, MissedCall, IncomingKnock, RenameEvent, UnjoinedCall, Verified, Ephemeral)
    }

  }
}
