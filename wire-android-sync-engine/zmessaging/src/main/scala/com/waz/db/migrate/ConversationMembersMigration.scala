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

import com.waz.db.Col._
import com.waz.db._
import com.waz.model.{ConvId, UserId}
import com.waz.utils.wrappers.DB


object ConversationMembersMigration {

  lazy val v72 = { implicit db: DB =>
    val table = TableDesc("ConversationMembers_tmp", Columns.v72.all)

    inTransaction { tr: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS ConversationMembers_tmp")
      db.execSQL(table.createSql)

      // copy all data
      db.execSQL("INSERT INTO ConversationMembers_tmp SELECT user_id, conv_id, active, 0 FROM ConversationMembers")

      db.execSQL("DROP TABLE ConversationMembers")
      db.execSQL("ALTER TABLE ConversationMembers_tmp RENAME TO ConversationMembers")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_conv on ConversationMembers (conv_id)")
    }
  }

  lazy val v79 = { implicit db: DB =>
    db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_userid on ConversationMembers (user_id)")
  }

  lazy val v82 = { implicit db: DB =>
    val table = TableDesc("ConversationMembers_tmp", Columns.v82.all)

    inTransaction { tr: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS ConversationMembers_tmp")
      db.execSQL(table.createSql)

      // copy all data
      db.execSQL("INSERT INTO ConversationMembers_tmp SELECT user_id, conv_id FROM ConversationMembers WHERE active = 1")

      db.execSQL("DROP TABLE ConversationMembers")
      db.execSQL("ALTER TABLE ConversationMembers_tmp RENAME TO ConversationMembers")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_conv on ConversationMembers (conv_id)")
    }
  }

  object Columns {

    object v72 {
      val UserId = id[UserId]('user_id)
      val ConvId = id[ConvId]('conv_id)
      val Active = bool('active)
      val Time = timestamp('time)

      val all = Seq(UserId, ConvId, Active, Time)
    }

    object v82 {
      val UserId = id[UserId]('user_id)
      val ConvId = id[ConvId]('conv_id)

      val all = Seq(UserId, ConvId)
    }
  }
}
