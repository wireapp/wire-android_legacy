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
import com.waz.db.Col.{id, remoteTimestamp}
import com.waz.db.{Dao2, Table}
import com.waz.utils.{Identifiable, Managed}
import com.waz.utils.wrappers.{DB, DBCursor}

case class ReadReceipt(message: MessageId, user: UserId, timestamp: RemoteInstant) extends Identifiable[ReadReceipt.Id] {
  override val id: ReadReceipt.Id = (message, user)
}

object ReadReceipt {
  type Id = (MessageId, UserId)

  implicit object ReadReceiptDao extends Dao2[ReadReceipt, MessageId, UserId] {

    val Message = id[MessageId]('message_id).apply(_.message)
    val User = id[UserId]('user_id).apply(_.user)
    val Timestamp = remoteTimestamp('timestamp)(_.timestamp)

    override val idCol: (ReadReceiptDao.Column[MessageId], ReadReceiptDao.Column[UserId]) = (Message, User)
    override val table: Table[ReadReceipt] = Table("ReadReceipts", Message, User, Timestamp)
    override def apply(implicit c:  DBCursor): ReadReceipt = ReadReceipt(Message, User, Timestamp)

    def findForMessage(message: MessageId)(implicit db: DB): Managed[Iterator[ReadReceipt]] = iterating(find(Message, message))
    def findForMessages(messages: Set[MessageId])(implicit db: DB): Managed[Iterator[ReadReceipt]] = iteratingMultiple(findInSet(Message, messages))
  }
}
