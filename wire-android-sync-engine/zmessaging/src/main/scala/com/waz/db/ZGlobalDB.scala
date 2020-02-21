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
import com.waz.cache.CacheEntryData.CacheEntryDao
import com.waz.content.ZmsDatabase
import com.waz.db.Col._
import com.waz.db.ZGlobalDB.{DbName, DbVersion, daos}
import com.waz.db.migrate.AccountDataMigration
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AccountData.AccountDataDao
import com.waz.model.TeamData.TeamDataDao
import com.waz.model.otr.ClientId
import com.waz.model.{AccountId, UserId}
import com.waz.service.tracking.TrackingService
import com.waz.sync.client.AuthenticationManager.AccessToken
import com.waz.utils.wrappers.DB
import com.waz.utils.{JsonDecoder, JsonEncoder, Resource}

class ZGlobalDB(context: Context, dbNameSuffix: String = "", tracking: TrackingService)
  extends DaoDB(context.getApplicationContext, DbName + dbNameSuffix, DbVersion, daos, ZGlobalDB.migrations, tracking)
    with DerivedLogTag {

  override def onUpgrade(db: SupportSQLiteDatabase, from: Int, to: Int): Unit = {
    if (from < 5) clearAllData(db)
    else super.onUpgrade(db, from, to)
  }

  def clearAllData(db: SupportSQLiteDatabase) = {
    debug(l"wiping global db...")
    dropAllTables(db)
    onCreate(db)
  }
}

object ZGlobalDB {
  val DbName = "ZGlobal.db"
  val DbVersion = 24

  lazy val daos = Seq(AccountDataDao, CacheEntryDao, TeamDataDao)

  lazy val migrations = Seq(
    Migration(13, 14) {
      implicit db => AccountDataMigration.v14(db)
    },
    Migration(14, 15) { db =>
      //      no longer valid
    },
    Migration(15, 16) { db =>
      //      no longer valid
    },
    Migration(16, 17) { db =>
      db.execSQL(s"ALTER TABLE Accounts ADD COLUMN registered_push TEXT")
    },
    Migration(17, 18) { db =>
      db.execSQL("ALTER TABLE Accounts ADD COLUMN teamId TEXT")
      db.execSQL("UPDATE Accounts SET teamId = ''")
      db.execSQL("ALTER TABLE Accounts ADD COLUMN self_permissions INTEGER DEFAULT 0")
      db.execSQL("ALTER TABLE Accounts ADD COLUMN copy_permissions INTEGER DEFAULT 0")
    },
    Migration(18, 19) { db =>
      db.execSQL("CREATE TABLE IF NOT EXISTS Teams (_id TEXT PRIMARY KEY, name TEXT, creator TEXT, icon TEXT, icon_key TEXT)")
    },
    Migration(19, 20) { db =>
      AccountDataMigration.v20(db)
    },
    Migration(20, 21) { db =>
      db.execSQL("ALTER TABLE Accounts ADD COLUMN pending_team_name TEXT DEFAULT NULL")
    },
    Migration(21, 22) { db =>
      db.execSQL("CREATE TABLE IF NOT EXISTS ActiveAccounts (_id TEXT PRIMARY KEY, team_id TEXT, cookie TEXT NOT NULL, access_token TEXT, registered_push TEXT)")
    },
    Migration(22, 23) { db =>
      db.execSQL("ALTER TABLE ActiveAccounts ADD COLUMN sso_id TEXT DEFAULT NULL")
    },
    Migration(23, 24) { db =>
      // Team icons are now public assets, so we no longer need icon_key. Sqlite doesn't support
      // dropping columns, so we have to do it manually by renaming the table, recreating it,
      // and finally restoring that data back into the table.

      import TeamDataDao._
      val TeamsTable = table.name
      val BackupTable = "TeamsBackup"
      val ColumnsToKeep = s"${Id.name}, ${Name.name}, ${Creator.name}, ${Icon.name}"

      db.execSQL(s"ALTER TABLE $TeamsTable RENAME TO $BackupTable")
      db.execSQL(s"${TeamDataDao.table.createSql}")
      db.execSQL(s"INSERT INTO $TeamsTable SELECT $ColumnsToKeep FROM $BackupTable")
      db.execSQL(s"DROP TABLE $BackupTable")
    }
  )

    implicit object ZmsDatabaseRes extends Resource[ZmsDatabase] {
      override def close(r: ZmsDatabase): Unit = r.close()
    }

    implicit object DbRes extends Resource[DB] {
      override def close(r: DB): Unit = r.close()
    }


  object Columns {

    object v12 {
      val Id = id[AccountId]('_id, "PRIMARY KEY")
      val Email = opt(emailAddress('email))
      val Hash = text('hash)
      val EmailVerified = bool('verified)
      val PhoneVerified = bool('phone_verified)
      val Cookie = opt(text('cookie))
      val Phone = opt(phoneNumber('phone))

      val all = Seq(Id, Email, Hash, EmailVerified, PhoneVerified, Cookie, Phone)
    }

    object v13 {
      val Id = id[AccountId]('_id, "PRIMARY KEY")
      val Email = opt(emailAddress('email))
      val Hash = text('hash)
      val EmailVerified = bool('verified)
      val Cookie = opt(text('cookie))
      val Phone = opt(phoneNumber('phone))
      val Token = opt(text[AccessToken]('access_token, JsonEncoder.encodeString[AccessToken], JsonDecoder.decode[AccessToken]))
      val UserId = opt(id[UserId]('user_id))
      val ClientId = opt(id[ClientId]('client_id))
      val ClientRegState = text('reg_state)

      val all = Seq(Id, Email, Hash, EmailVerified, Cookie, Phone, Token, UserId, ClientId, ClientRegState)
    }

  }

}
