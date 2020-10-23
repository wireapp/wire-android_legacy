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

import com.waz.api
import com.waz.api.{ContentSearchQuery, Message}
import com.waz.db.Col._
import com.waz.db._
import com.waz.model.GenericContent.Knock
import com.waz.model.MessageData.MessageState
import com.waz.model.{GenericMessage, _}
import com.waz.utils.wrappers.DB

import scala.collection.breakOut

object MessageDataMigration {
  import GenericMessage._

  lazy val v69 = { implicit db: DB =>
    // removes Edit, Otr and HotKnock columns
    // adds protos column
    // generates protos for KNOCK msgs, to preserve hot knock info

    import Columns.{v68 => src, v69 => dst}
    val from = TableDesc("Messages", src.all)
    val to = TableDesc("Messages_tmp", dst.all)

    def updateProtos() = {
      withStatement("UPDATE Messages_tmp SET protos = ? WHERE _id = ?") { stmt =>
        forEachRow(db.query("Messages", Array("_id", "hot"), "msg_type = 'Knock'", null, null, null, null)) { c =>
          stmt.clearBindings()
          val id = c.getString(0)
          val protos = Seq(GenericMessage(Uid(id), Knock(false)))

          dst.Protos.bind(protos, 1, stmt)
          stmt.bindString(2, id)
          stmt.execute()
        }
      }
    }

    inTransaction { tr: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS Messages_tmp")
      db.execSQL(to.createSql)

      // copy all messages
      db.execSQL("INSERT INTO Messages_tmp SELECT _id, conv_id, source_seq, source_hex, msg_type, user_id, content, NULL, time, local_time, first_msg, members, recipient, email, name, msg_state, content_size FROM Messages")

      // update protos field for knocks
      updateProtos()

      db.execSQL("DROP TABLE Messages")
      db.execSQL("ALTER TABLE Messages_tmp RENAME TO Messages")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Messages_conv_source_idx on Messages ( conv_id, source_seq, source_hex )")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Messages_conv_time_source_idx on Messages ( conv_id, time, source_seq, source_hex )")
    }
  }

  lazy val v72 = { implicit db: DB =>
    val table = TableDesc("Messages_tmp", Columns.v72.all)

    inTransaction { tr: Transaction =>
      db.execSQL("DROP TABLE IF EXISTS Messages_tmp")
      db.execSQL(table.createSql)

      // copy all data
      val firstMessage = """
                           |CASE
                           |  WHEN first_msg = 1 THEN 1
                           |  WHEN msg_type = 'MemberJoin' AND source_seq = 1 THEN 1
                           |  ELSE 0
                           |END""".stripMargin

      db.execSQL(s"INSERT INTO Messages_tmp SELECT _id, conv_id, msg_type, user_id, content, protos, time, local_time, ($firstMessage), members, recipient, email, name, msg_state, content_size, edit_time FROM Messages")

      db.execSQL("DROP TABLE Messages")
      db.execSQL("DROP INDEX IF EXISTS Messages_conv_source_idx")
      db.execSQL("DROP INDEX IF EXISTS Messages_conv_time_source_idx")

      db.execSQL("ALTER TABLE Messages_tmp RENAME TO Messages")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Messages_conv_time on Messages ( conv_id, time)")
    }
  }

  lazy val v76 = { implicit db: DB =>
    db.execSQL("ALTER TABLE Messages ADD COLUMN ephemeral INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE Messages ADD COLUMN expiry_time INTEGER")
  }

  lazy val v77 = { implicit db: DB =>
    db.execSQL("ALTER TABLE Messages ADD COLUMN expired INTEGER DEFAULT 0")
  }

  lazy val v80 = { implicit db: DB =>
    db.execSQL("ALTER TABLE Messages ADD COLUMN duration INTEGER DEFAULT 0")
  }

  lazy val v83 = { implicit db: DB =>
    // ensures MessageContentIndex is fully populated

    val Type = text[Message.Type]('msg_type, MessageData.MessageTypeCodec.encode, MessageData.MessageTypeCodec.decode)
    val Content = jsonArray[MessageContent, Seq, Vector]('content)
    val Protos = protoSeq[GenericMessage, Seq, Vector]('protos)

    val queryString =
      "SELECT _id, conv_id, msg_type, content, protos, time FROM Messages WHERE msg_type IN ('Text', 'TextEmojiOnly', 'RichMedia') AND _id NOT IN (SELECT message_id FROM MessageContentIndex)"

    withStatement("INSERT OR REPLACE INTO MessageContentIndex (message_id, conv_id, content, time) VALUES (?, ?, ?, ?)") { stmt =>
      forEachRow(db.rawQuery(queryString)) { c =>
        stmt.clearBindings()
        val id = c.getString(0)
        val convId = c.getString(1)
        val msgType = Type.load(c, 2)
        val content = Content.load(c, 3)
        val protos = Protos.load(c, 4)
        val time = c.getLong(5)

        val contentString = protos.lastOption match {
          case Some(TextMessage(ct, _, _, _, _)) => ct
          case _ if msgType == api.Message.Type.RICH_MEDIA => content.map(_.content).mkString(" ")
          case _ => content.headOption.fold("")(_.content)
        }

        val normalized = ContentSearchQuery.transliterated(contentString)

        stmt.bindString(1, id)
        stmt.bindString(2, convId)
        stmt.bindString(3, normalized)
        stmt.bindLong(4, time)
        stmt.execute()
      }
    }
  }

  object Columns {

    object v68 {
      val Id = id[MessageId]('_id, "PRIMARY KEY")
      val Conv = id[ConvId]('conv_id)
      val SourceSeq = long('source_seq)
      val SourceHex = text('source_hex)
      val Edit = text('edit)
      val Type = text[Message.Type]('msg_type, MessageData.MessageTypeCodec.encode, MessageData.MessageTypeCodec.decode)
      val User = id[UserId]('user_id)
      val Content = jsonArray[MessageContent, Seq, Vector]('content)
      val ContentSize = int('content_size)
      val HotKnock = bool('hot)
      val FirstMessage = bool('first_msg)
      val Otr = bool('otr)
      val Members = set[UserId]('members, _.mkString(","), _.split(",").filter(!_.isEmpty).map(UserId(_))(breakOut))
      val Recipient = opt(id[UserId]('recipient))
      val Email = opt(text('email))
      val Name = opt(text('name))
      val State = text[MessageState]('msg_state, _.name, Message.Status.valueOf)
      val Time = timestamp('time)
      val LocalTime = timestamp('local_time)

      val all = Seq(Id, Conv, SourceSeq, SourceHex, Edit, Type, User, Content, HotKnock, Otr, Time, LocalTime, FirstMessage, Members, Recipient, Email, Name, State, ContentSize)
    }

    object v69 {
      val Id = id[MessageId]('_id, "PRIMARY KEY")
      val Conv = id[ConvId]('conv_id)
      val SourceSeq = long('source_seq)
      val SourceHex = text('source_hex)
      val Type = text[Message.Type]('msg_type, MessageData.MessageTypeCodec.encode, MessageData.MessageTypeCodec.decode)
      val User = id[UserId]('user_id)
      val Content = jsonArray[MessageContent, Seq, Vector]('content)
      val Protos = protoSeq[GenericMessage, Seq, Vector]('protos)
      val ContentSize = int('content_size)
      val FirstMessage = bool('first_msg)
      val Members = set[UserId]('members, _.mkString(","), _.split(",").filter(!_.isEmpty).map(UserId(_))(breakOut))
      val Recipient = opt(id[UserId]('recipient))
      val Email = opt(text('email))
      val Name = opt(text('name))
      val State = text[MessageState]('msg_state, _.name, Message.Status.valueOf)
      val Time = timestamp('time)
      val LocalTime = timestamp('local_time)

      val all = Seq(Id, Conv, SourceSeq, SourceHex, Type, User, Content, Protos, Time, LocalTime, FirstMessage, Members, Recipient, Email, Name, State, ContentSize)
    }

    object v72 {

      val Id = id[MessageId]('_id, "PRIMARY KEY")
      val Conv = id[ConvId]('conv_id)
      val Type = text[Message.Type]('msg_type, MessageData.MessageTypeCodec.encode, MessageData.MessageTypeCodec.decode)
      val User = id[UserId]('user_id)
      val Content = jsonArray[MessageContent, Seq, Vector]('content)
      val Protos = protoSeq[GenericMessage, Seq, Vector]('protos)
      val ContentSize = int('content_size)
      val FirstMessage = bool('first_msg)
      val Members = set[UserId]('members, _.mkString(","), _.split(",").filter(!_.isEmpty).map(UserId(_))(breakOut))
      val Recipient = opt(id[UserId]('recipient))
      val Email = opt(text('email))
      val Name = opt(text('name))
      val State = text[MessageState]('msg_state, _.name, Message.Status.valueOf)
      val Time = timestamp('time)
      val LocalTime = timestamp('local_time)
      val EditTime = timestamp('edit_time)

      val all = Seq(Id, Conv, Type, User, Content, Protos, Time, LocalTime, FirstMessage, Members, Recipient, Email, Name, State, ContentSize, EditTime)
    }
  }

}
