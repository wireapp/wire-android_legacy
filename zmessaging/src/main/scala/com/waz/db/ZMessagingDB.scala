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
package com.waz.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waz.api.Message
import com.waz.content.PropertiesDao
import com.waz.db.ZMessagingDB.{DbVersion, daos, migrations}
import com.waz.db.migrate._
import com.waz.model.AssetData.AssetDataDao
import com.waz.model.ButtonData.ButtonDataDao
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.ConversationFolderData.ConversationFolderDataDao
import com.waz.model.ConversationMemberData.ConversationMemberDataDao
import com.waz.model.ConversationRoleAction.ConversationRoleActionDao
import com.waz.model.EditHistory.EditHistoryDao
import com.waz.model.ErrorData.ErrorDataDao
import com.waz.model.FolderData.FolderDataDao
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model.Liking.LikingDao
import com.waz.model.MessageData.MessageDataDao
import com.waz.model.MsgDeletion.MsgDeletionDao
import com.waz.model.PushNotificationEvents.PushNotificationEventsDao
import com.waz.model.ReadReceipt.ReadReceiptDao
import com.waz.model.UserData.UserDataDao
import com.waz.model._
import com.waz.model.otr.UserClients.UserClientsDao
import com.waz.model.sync.SyncJob.SyncJobDao
import com.waz.service.assets
import com.waz.service.assets.AssetStorageImpl.AssetDao
import com.waz.service.assets.DownloadAssetStorage.DownloadAssetDao
import com.waz.service.assets.UploadAssetStorage.UploadAssetDao

import scala.util.{Success, Try}

class ZMessagingDB(context: Context, dbName: String) extends DaoDB(context.getApplicationContext, dbName, DbVersion, daos, migrations) {

  override def onUpgrade(db: SupportSQLiteDatabase, from: Int, to: Int): Unit = {
    if (from < 60) {
      dropAllTables(db)
      onCreate(db)
    } else super.onUpgrade(db, from, to)
  }
}

object ZMessagingDB {
  val DbVersion = 126

  lazy val daos = Seq (
    UserDataDao, AssetDataDao, ConversationDataDao, ConversationMemberDataDao,
    MessageDataDao, KeyValueDataDao, SyncJobDao, ErrorDataDao,
    UserClientsDao, LikingDao, MsgDeletionDao, EditHistoryDao,
    PushNotificationEventsDao, ReadReceiptDao, PropertiesDao, UploadAssetDao, DownloadAssetDao,
    AssetDao, FolderDataDao, ConversationFolderDataDao,
    ConversationRoleActionDao, ButtonDataDao, MessageContentIndexDao
  )

  lazy val migrations = Seq(
    Migration(60, 61)(UserDataMigration.v61),
    Migration(61, 62) { _.execSQL("ALTER TABLE Errors ADD COLUMN messages TEXT") },
    Migration(62, 63) { _.execSQL("ALTER TABLE VoiceParticipants ADD COLUMN sends_video INTEGER DEFAULT 0") },
    Migration(63, 64)(ConversationDataMigration.v64),
    Migration(64, 65) { _.execSQL("ALTER TABLE GcmData ADD COLUMN clear_time INTEGER") },
    Migration(65, 66)(CallLogMigration.v66),
    Migration(66, 67)(LikingsMigration.v67),
    Migration(67, 68) { db =>
      db.execSQL("DROP TABLE IF EXISTS MsgDeletion")
      db.execSQL("CREATE TABLE MsgDeletion (message_id TEXT PRIMARY KEY, timestamp INTEGER)")
    },
    Migration(68, 69)(MessageDataMigration.v69),
    Migration(69, 70)(AssetDataMigration.v70),
    Migration(70, 71) {
      _.execSQL("ALTER TABLE Messages ADD COLUMN edit_time INTEGER DEFAULT 0")
    },
    Migration(70, 71) {
      _.execSQL("ALTER TABLE Messages ADD COLUMN edit_time INTEGER DEFAULT 0")
    },
    Migration(71, 72) { db =>
      MessageDataMigration.v72(db)
      ConversationDataMigration.v72(db)
      ConversationMembersMigration.v72(db)
    },
    Migration(72, 73) {
      _.execSQL("CREATE TABLE EditHistory (original_id TEXT PRIMARY KEY, updated_id TEXT, timestamp INTEGER)")
    },
    Migration(73, 74) { implicit db =>
      db.execSQL("DROP TABLE IF EXISTS GcmData") // just dropping all data, not worth the trouble to migrate that
      db.execSQL("CREATE TABLE NotificationData (_id TEXT PRIMARY KEY, data TEXT)")
    },
    Migration(74, 75) { db =>
      SearchQueryCacheMigration.v75(db)
      SyncJobMigration.v75(db)
    },
    Migration(75, 76) { db =>
      MessageDataMigration.v76(db)
      ConversationDataMigration.v76(db)
    },
    Migration(76, 77) { db =>
      MessageDataMigration.v77(db)
    },
    Migration(77, 78) { db =>
      UserDataMigration.v78(db)
    },
    Migration(78, 79) { db =>
      UserDataMigration.v79(db)
      ConversationMembersMigration.v79(db)
      ConversationDataMigration.v79(db)
    },
    Migration(79, 80) { db =>
      MessageDataMigration.v80(db)
    },
    Migration(80, 81) { db =>
      db.execSQL("CREATE VIRTUAL TABLE MessageContentIndex using fts3(conv_id TEXT, message_id TEXT, content TEXT, time TIMESTAMP)")
    },
    Migration(81, 82) { db =>
      ConversationMembersMigration.v82(db)
      ConversationDataMigration.v82(db)
    },
    Migration(82, 83) { db =>
      MessageDataMigration.v83(db)
    },
    Migration(83, 84){ db =>
      db.execSQL("INSERT INTO KeyValues (key, value) VALUES ('should_sync_conversations', 'true')")
    },
    Migration(84, 85){ db =>
      // bug in this migration - skip this version
    },
    Migration(85, 86) { db =>
      // new users won't have any push ti- fix for broken internal users due to last migration
      db.execSQL("DELETE FROM SyncJobs WHERE data LIKE '%push-token%'")
      db.execSQL("DELETE FROM SyncJobs WHERE data LIKE '%gcm-token%'")
    },
    Migration(86, 87) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN team TEXT")
      db.execSQL("ALTER TABLE Conversations ADD COLUMN is_managed INTEGER")
    },
    Migration(87, 88) { db =>
      db.execSQL("CREATE TABLE Teams (_id TEXT PRIMARY KEY, name TEXT, creator TEXT, icon TEXT, icon_key TEXT)")
      db.execSQL("CREATE TABLE TeamMembers (user_id TEXT, team_id TEXT, self_permissions INTEGER, copy_permissions INTEGER, PRIMARY KEY (user_id, team_id))")
      db.execSQL("UPDATE KeyValues SET value = 'true' WHERE key = 'should_sync_teams'")
    },
    Migration(88, 89) { db =>
      db.execSQL("DROP TABLE IF EXISTS VoiceParticipants")
    },
    Migration(89, 90) { db =>
      ConversationDataMigration.v90(db)
    },
    Migration(90, 91) { db =>
      db.execSQL("DROP TABLE IF EXISTS CommonConnections")
    },
    Migration(91, 92) { db =>
      db.execSQL("ALTER TABLE Users ADD COLUMN teamId TEXT")
      db.execSQL("DROP TABLE IF EXISTS TeamMembers")
      db.execSQL("UPDATE KeyValues SET value = 'true' WHERE key = 'should_sync_teams'")
    },
    Migration(92, 93) { db =>
      db.execSQL("DROP TABLE IF EXISTS Teams")
    },
    Migration(93, 94) { db =>
      db.execSQL("UPDATE KeyValues SET value = 'true' WHERE key = 'should_sync_teams'")
    },
    Migration(94, 95) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN unread_call_count INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE Conversations ADD COLUMN unread_ping_count INTEGER DEFAULT 0")
    },
    Migration(95, 96) { db =>
      db.execSQL("CREATE TABLE ReceivedPushes (_id TEXT PRIMARY KEY, data TEXT)")
    },
    Migration(96, 97) { db =>
      db.execSQL("ALTER TABLE Users ADD COLUMN availability INTEGER DEFAULT 0")
    },
    Migration(97, 98) { db =>
      db.execSQL("ALTER TABLE Users ADD COLUMN provider_id TEXT DEFAULT null")
      db.execSQL("ALTER TABLE Users ADD COLUMN integration_id TEXT DEFAULT null")
    },
    Migration(98, 99) { db =>
      db.execSQL("""CREATE TABLE PushNotificationEvents(pushId TEXT, event_index INTEGER,
                                                        decrypted INTEGER, event TEXT,
                                                        plain BLOB, transient BOOLEAN,
                    PRIMARY KEY (pushId, event_index))""")
    },
    Migration(99, 100) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN access TEXT DEFAULT null")
      db.execSQL("ALTER TABLE Conversations ADD COLUMN access_role TEXT DEFAULT null")
    },
    Migration(100, 101) { db =>
      db.execSQL("DROP TABLE IF EXISTS CallLog")
    },
    Migration(101, 102) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN link TEXT DEFAULT null")
    },
    Migration(102, 103) { db =>
      db.execSQL("ALTER TABLE Users ADD COLUMN expires_at INTEGER DEFAULT null")
    },
    Migration(103, 104) { db =>
      db.execSQL("delete from MessageContentIndex where message_id in (select message_id from MessageContentIndex left join Messages on MessageContentIndex.message_id == Messages._id WHERE Messages._id IS null)")
    },
    Migration(104, 105) { db =>
      db.execSQL("CREATE TABLE PushNotificationEventsCopy(pushId TEXT, event_index INTEGER PRIMARY KEY, decrypted INTEGER, event TEXT, plain BLOB, transient BOOLEAN);")
      db.execSQL("INSERT INTO PushNotificationEventsCopy (pushId, decrypted, event, plain, transient) SELECT pushid, decrypted, event, plain, transient FROM PushNotificationEvents;")
      db.execSQL("DROP TABLE PushNotificationEvents;")
      db.execSQL("ALTER TABLE PushNotificationEventsCopy RENAME TO PushNotificationEvents;")
    },
    Migration(105, 106) { db =>
      db.execSQL("DROP TABLE IF EXISTS InvitedContacts")
    },
    Migration(106, 107) { db =>
      db.execSQL("UPDATE Conversations SET cleared = null WHERE cleared = 0")
    },
    Migration(107, 108) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN global_ephemeral INTEGER DEFAULT null")
      db.execSQL("UPDATE Messages SET ephemeral = null WHERE ephemeral = 0")
      db.execSQL("UPDATE Messages SET duration = null WHERE duration = 0")
      db.execSQL("UPDATE Conversations SET ephemeral = null WHERE ephemeral = 0")
    },
    Migration(108, 109) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN unread_mentions_count INTEGER DEFAULT 0")
    },
    Migration(109, 110) { db =>
      db.execSQL(
        """
          | CREATE TABLE ConversationsCopy (
          | _id TEXT PRIMARY KEY,
          | remote_id TEXT, name TEXT, creator TEXT, conv_type INTEGER, team TEXT, is_managed INTEGER,
          | last_event_time INTEGER, is_active INTEGER, last_read INTEGER, mute_time INTEGER, archived INTEGER,
          | archive_time INTEGER, cleared INTEGER, generated_name TEXT, search_key TEXT, unread_count INTEGER,
          | unsent_count INTEGER, hidden INTEGER, missed_call TEXT, incoming_knock TEXT, verified TEXT,
          | ephemeral INTEGER, global_ephemeral INTEGER, unread_call_count INTEGER, unread_ping_count INTEGER,
          | access TEXT, access_role TEXT, link TEXT, unread_mentions_count INTEGER, muted_status INTEGER DEFAULT 0
          | );
        """.stripMargin)
      db.execSQL(
        """
          |INSERT INTO ConversationsCopy(
          | _id,
          | remote_id, name, creator, conv_type, team, is_managed, last_event_time, is_active, last_read, mute_time,
          | archived, archive_time, cleared, generated_name, search_key, unread_count, unsent_count, hidden,
          | missed_call, incoming_knock, verified, ephemeral, global_ephemeral, unread_call_count, unread_ping_count,
          | access, access_role, link, unread_mentions_count
          | )
          | SELECT
          | _id,
          | remote_id, name, creator, conv_type, team, is_managed, last_event_time, is_active, last_read, mute_time,
          | archived, archive_time, cleared, generated_name, search_key, unread_count, unsent_count, hidden,
          | missed_call, incoming_knock, verified, ephemeral, global_ephemeral, unread_call_count, unread_ping_count,
          | access, access_role, link, unread_mentions_count
          | FROM Conversations;
        """.stripMargin)
      db.execSQL("UPDATE ConversationsCopy SET muted_status = 2 WHERE _id in (SELECT _id FROM Conversations WHERE Conversations.muted = 1);") // muted_status == 2 => only mentions are displayed
      db.execSQL("DROP TABLE Conversations;")
      db.execSQL("ALTER TABLE ConversationsCopy RENAME TO Conversations;")
    },
    Migration(110, 111) { db =>
      db.execSQL("ALTER TABLE Messages ADD COLUMN quote TEXT")
      db.execSQL("ALTER TABLE Messages ADD COLUMN quote_validity INTEGER DEFAULT 0")
    },
    Migration(111, 112) { db =>
      db.execSQL("ALTER TABLE Conversations ADD COLUMN unread_quote_count INTEGER DEFAULT 0")
    },
    Migration(112, 113) { db =>
      db.execSQL("CREATE TABLE ReadReceipts(message_id TEXT, user_id, timestamp INTEGER, PRIMARY KEY (message_id, user_id))")
      db.execSQL("ALTER TABLE Conversations ADD COLUMN receipt_mode INTEGER DEFAULT 0")
      db.execSQL("CREATE TABLE Properties(key TEXT PRIMARY KEY, value TEXT)")
      db.execSQL("ALTER TABLE Messages ADD COLUMN force_read_receipts INTEGER DEFAULT null")
      db.execSQL("UPDATE KeyValues SET value = 'true' WHERE key = 'should_sync_conversations_1'")
    },
    Migration(113, 114) { db =>
       db.execSQL("ALTER TABLE Users ADD COLUMN managed_by TEXT DEFAULT null")
    },
    Migration(114, 115) { db =>
      db.execSQL("ALTER TABLE Users ADD COLUMN fields TEXT DEFAULT null")
    },
    Migration(115, 116) { db =>
      db.execSQL("ALTER TABLE Users ADD COLUMN self_permissions INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE Users ADD COLUMN copy_permissions INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE Users ADD COLUMN created_by TEXT DEFAULT null")
      db.execSQL("UPDATE KeyValues SET value = 'true' WHERE key = 'should_sync_teams'")
    },
    Migration(116, 117) { db => },
    Migration(117, 118) { db =>
      db.execSQL("DROP TABLE ReceivedPushes")
    },
    Migration(118, 119) { db => },
    Migration(119, 120) { db =>
      db.execSQL("UPDATE Conversations SET muted_status = 1 WHERE muted_status = 2") //fix for AN-6210
    },
    Migration(120, 121) { db =>
      import com.waz.model.AssetData.{AssetDataDao => OldAssetDao}
      import com.waz.service.assets.AssetStorageImpl.{AssetDao => NewAssetDao}
      import com.waz.service.assets._

      //Create new tables
      db.execSQL(UploadAssetDao.table.createSql)
      db.execSQL(DownloadAssetDao.table.createSql)
      db.execSQL(NewAssetDao.table.createSql)

      //Remove old assetId from user's profile pictures and sync to get the remote id instead
      db.execSQL("UPDATE Users SET picture = null")
      db.execSQL("UPDATE KeyValues SET value = 'true' WHERE key = 'should_sync_users_1'")

      //Convert old assets to new assets (public assets won't be converted)
      def convertAsset(old: AssetData): Try[Asset] = Try {
        val encryption = old.otrKey.map(k => AES_CBC_Encryption(AESKeyBytes(k.bytes)))

        assets.Asset(
          id = old.remoteId.map(rid => AssetId(rid.str)).getOrElse(old.id),
          token = old.token,
          sha = old.sha.get,
          mime = old.mime,
          encryption = encryption.getOrElse(NoEncryption),
          localSource = None, //we can not trust old information about local sources
          preview = old.previewId,
          name = old.name.getOrElse(s"old_asset_${old.id.str}"),
          size = old.size,
          details = old.metaData match {
            case Some(data: AssetMetaData.Video) =>
              VideoDetails(data.dimensions, data.duration)
            case Some(data: AssetMetaData.Audio) =>
              AudioDetails(data.duration, Loudness(data.loudness.map(_.levels.map(_.toByte)).getOrElse(Vector.empty)))
            case Some(data: AssetMetaData.Image) =>
              ImageDetails(data.dimensions)
            case Some(AssetMetaData.Empty) =>
              BlobDetails
            case None =>
              BlobDetails
          },
          convId = old.convId
        )
      }

      val oldAssets = OldAssetDao.list(db)
      val newAssets = oldAssets.map(convertAsset).collect { case Success(asset) => asset }

      newAssets.foreach { na =>
        NewAssetDao.insertOrIgnore(na)(db)
      }

      // Add asset_id to the message data, referencing the new assets
      db.execSQL(s"ALTER TABLE ${MessageDataDao.table.name} ADD COLUMN ${MessageDataDao.AssetId.name} TEXT DEFAULT null")

      val messages = MessageDataDao.findByTypes(Set(Message.Type.ANY_ASSET, Message.Type.VIDEO_ASSET, Message.Type.AUDIO_ASSET, Message.Type.IMAGE_ASSET))(db)

      messages.foreach { m =>
        m.genericMsgs.lastOption match {
          case Some(msg) => msg.unpackContent match {
            case GenericContent.Asset(proto) if proto.hasUploaded =>
              val newMessage = m.copy(assetId = Option(AssetId(proto.getUploaded.getAssetId)))
              MessageDataDao.insertOrReplace(newMessage)(db)
            case _ =>
          }
          case _ =>
        }
      }
    },
    Migration(121, 122) { db =>
      // To fetch team data to display the logo.
      db.execSQL(s"UPDATE ${KeyValueDataDao.table.name} SET ${KeyValueDataDao.Value.name} = 'true' WHERE ${KeyValueDataDao.Key.name} = 'should_sync_teams'")
    },
    Migration(122, 123) { db =>
      db.execSQL(FolderDataDao.table.createSql)
      db.execSQL(ConversationFolderDataDao.table.createSql)
    },
    Migration(123,124) { db =>
      db.execSQL("DROP TABLE IF EXISTS SearchQueries")
    },
    Migration(124,125) { db =>
      db.execSQL(s"ALTER TABLE ${ConversationMemberDataDao.table.name} ADD COLUMN ${ConversationMemberDataDao.Role.name} TEXT DEFAULT '${ConversationRole.AdminRole.label}'")
      db.execSQL(ConversationRoleActionDao.table.createSql)
    },
    Migration(125,126) { db =>
      db.execSQL(
        s"""
           | UPDATE Users
           | SET name = display_name
           | WHERE display_name IS NOT NULL AND display_name IS NOT "" AND name IS NOT display_name
        """.stripMargin)
      db.execSQL(
        """
          | CREATE TABLE UsersCopy (
          | _id TEXT PRIMARY KEY,
          | teamId TEXT, name TEXT, email TEXT, phone TEXT, tracking_id TEXT,
          | picture TEXT, accent INTEGER, skey TEXT, connection TEXT, conn_timestamp INTEGER,
          | conn_msg TEXT, conversation TEXT, relation TEXT, timestamp INTEGER,
          | verified TEXT, deleted INTEGER, availability INTEGER,
          | handle TEXT, provider_id TEXT, integration_id TEXT, expires_at INTEGER,
          | managed_by TEXT, self_permissions INTEGER, copy_permissions INTEGER, created_by TEXT
          | )
        """.stripMargin)
      db.execSQL(
        """
          |INSERT INTO UsersCopy(
          | _id,
          | teamId, name, email, phone, tracking_id,
          | picture, accent, skey, connection, conn_timestamp,
          | conn_msg, conversation, relation, timestamp,
          | verified, deleted, availability,
          | handle, provider_id, integration_id, expires_at,
          | managed_by, self_permissions, copy_permissions, created_by
          | )
          | SELECT
          | _id,
          | teamId, name, email, phone, tracking_id,
          | picture, accent, skey, connection, conn_timestamp,
          | conn_msg, conversation, relation, timestamp,
          | verified, deleted, availability,
          | handle, provider_id, integration_id, expires_at,
          | managed_by, self_permissions, copy_permissions, created_by
          | FROM Users
        """.stripMargin)
      db.execSQL("DROP TABLE Users")
      db.execSQL("ALTER TABLE UsersCopy RENAME TO Users")
    }
  )
}
