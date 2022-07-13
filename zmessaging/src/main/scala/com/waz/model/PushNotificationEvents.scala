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

import com.waz.db.{Dao, Dao2}
import com.waz.db.Col._
import com.waz.sync.client.{EncodedEvent, PushNotificationEncoded}
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}

object PushNotificationEvents {
  implicit object EncryptedPushNotificationEventsDao extends Dao2[PushNotificationEvent, Uid, Int] {
    private val PushId = id[Uid]('pushId).apply(_.pushId)
    private val Index = int('event_index)(_.index)
    private val EventStr = text('event)(_.event.str)
    private val Transient = bool('transient)(_.transient)

    override val idCol = (PushId, Index)
    override val table = Table("EncryptedPushNotificationEvents", PushId, Index, EventStr, Transient)

    override def apply(implicit cursor: DBCursor): PushNotificationEvent =
      PushNotificationEvent(PushId, Index, false, EncodedEvent(cursor.getString(EventStr.index)), None, Transient)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
    }

    def maxIndex()(implicit db: DB): Int = queryForLong(maxWithDefault(Index.name)).toInt

    def listAll()(implicit db: DB): Vector[PushNotificationEvent] =
      list(db.query(table.name, null, null, null, null, null, "event_index ASC"))
  }

  implicit object DecryptedPushNotificationEventsDao extends Dao2[PushNotificationEvent, Uid, Int] {
    private val PushId = id[Uid]('pushId).apply(_.pushId)
    private val Index = int('event_index)(_.index)
    private val EventStr = text('event)(_.event.str)
    private val Plain = opt(blob('plain))(_.plain)
    private val Transient = bool('transient)(_.transient)

    override val idCol = (PushId, Index)
    override val table = Table("DecryptedPushNotificationEvents", PushId, Index, EventStr, Plain, Transient)

    override def apply(implicit cursor: DBCursor): PushNotificationEvent =
      PushNotificationEvent(PushId, Index, true, EncodedEvent(cursor.getString(EventStr.index)), Plain, Transient)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
    }

    def maxIndex()(implicit db: DB): Int = queryForLong(maxWithDefault(Index.name)).toInt

    def listAll()(implicit db: DB): Vector[PushNotificationEvent] =
      list(db.query(table.name, null, null, null, null, null, "event_index ASC"))
  }
}

final case class PushNotificationEvent(pushId:    Uid,
                                       index:     Int,
                                       decrypted: Boolean = false,
                                       event:     EncodedEvent,
                                       plain:     Option[Array[Byte]] = None,
                                       transient: Boolean) extends Identifiable[(Uid, Int)] {
  override val id: (Uid, Int) = (pushId, index)
}
