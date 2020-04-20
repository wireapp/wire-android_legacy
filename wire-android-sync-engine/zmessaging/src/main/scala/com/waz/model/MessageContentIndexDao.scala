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

import com.waz.api.{ContentSearchQuery, Message}
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.log.BasicLogging.LogTag
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}

case class MessageContentIndexEntry(messageId: MessageId, convId: ConvId, content: String, time: RemoteInstant) extends Identifiable[MessageId] {
  override val id: MessageId = messageId
}

object MessageContentIndexDao extends Dao[MessageContentIndexEntry, MessageId] {
  import MessageContentIndex._
  private implicit val tag: LogTag = LogTag("MessageContentIndex")

  val MessageId = id[MessageId]('message_id).apply(_.messageId)
  val Conv = id[ConvId]('conv_id).apply(_.convId)
  val Content = text('content)(_.content)
  val Time = remoteTimestamp('time)(_.time)

  override def onCreate(db: DB) = {
    db.execSQL(table.createFtsSql)
  }
  override val idCol = MessageId
  override val table = Table("MessageContentIndex", MessageId, Conv, Content, Time)

  override def apply(implicit c: DBCursor): MessageContentIndexEntry =
    MessageContentIndexEntry(MessageId, Conv, Content, Time)

  private val IndexColumns = Array(MessageId.name, Time.name)

  def findContent(contentSearchQuery: ContentSearchQuery, convId: Option[ConvId])(implicit db: DB): DBCursor ={
    findContentFts(contentSearchQuery.toFtsQuery, convId)
  }

  def deleteForConv(id: ConvId)(implicit db: DB) = delete(Conv, id)

  def deleteUpTo(id: ConvId, upTo: RemoteInstant)(implicit db: DB) = db.delete(table.name, s"${Conv.name} = '${id.str}' AND ${Time.name} <= ${Time(upTo)}", null)

  def findContentFts(queryText: String, convId: Option[ConvId])(implicit db: DB): DBCursor = {
    convId match {
      case Some(conv) =>
        db.query(table.name, IndexColumns, s"${Conv.name} = '$conv' AND ${Content.name} MATCH '$queryText'", null, null, null, s"${Time.name} DESC", SearchLimit)
      case _ =>
        db.query(table.name, IndexColumns, s"${Content.name} MATCH '$queryText'", null, null, null, s"${Time.name} DESC", SearchLimit)
    }
  }
}

object MessageContentIndex {
  val MaxSearchResults = 1024 // don't want to read whole db on common search query
  val SearchLimit = MaxSearchResults.toString
  val TextMessageTypes = Set(Message.Type.TEXT, Message.Type.TEXT_EMOJI_ONLY, Message.Type.RICH_MEDIA, Message.Type.COMPOSITE)
}
