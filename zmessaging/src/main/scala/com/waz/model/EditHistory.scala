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

import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}
import org.threeten.bp.Instant

case class EditHistory(originalId: MessageId, updatedId: MessageId, time: RemoteInstant) extends Identifiable[MessageId] {
  override val id: MessageId = originalId
}

object EditHistory {

  implicit object EditHistoryDao extends Dao[EditHistory, MessageId] {
    val Original = id[MessageId]('original_id).apply(_.originalId)
    val Updated = id[MessageId]('updated_id).apply(_.updatedId)
    val Timestamp = remoteTimestamp('timestamp)(_.time)

    override val idCol = Original
    override val table = Table("EditHistory", Original, Updated, Timestamp)

    override def apply(implicit cursor: DBCursor) = EditHistory(Original, Updated, Timestamp)

    def deleteOlder(time: Instant)(implicit db: DB) =
      db.delete(table.name, s"${Timestamp.name} < ${time.toEpochMilli}", null)
  }
}
