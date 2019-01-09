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
package com.waz.service.push

import android.content.Context
import com.waz.api.NetworkMode
import com.waz.content.Database
import com.waz.db.Col.{id, text}
import com.waz.db.Dao
import com.waz.model.Uid
import com.waz.service.push.ReceivedPushData.ReceivedPushDataDao
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.DBCursor
import com.waz.utils.{CachedStorage, CachedStorageImpl, Identifiable, JsonDecoder, JsonEncoder, TrimmingLruCache, returning}
import org.json.JSONObject
import org.threeten.bp.{Duration, Instant}


trait ReceivedPushStorage extends CachedStorage[Uid, ReceivedPushData]

class ReceivedPushStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[Uid, ReceivedPushData](new TrimmingLruCache(context, Fixed(100)), storage)(ReceivedPushDataDao)
    with ReceivedPushStorage

/**
  * @param receivedAt instant the push notification was received
  * @param toFetch time elapsed until we successfully fetched the notification
  */
case class ReceivedPushData(override val id: Uid, //for notifications where we don't get the id, we will generate a random one for storage's sake
                            sinceSent:       Duration,
                            receivedAt:      Instant,
                            networkMode:     NetworkMode,
                            networkOperator: String,
                            isDeviceIdle:    Boolean,
                            toFetch:         Option[Duration] = None) extends Identifiable[Uid]

object ReceivedPushData {

  implicit lazy val Encoder: JsonEncoder[ReceivedPushData] = new JsonEncoder[ReceivedPushData] {
    override def apply(v: ReceivedPushData) = returning(new JSONObject()) { o =>
      o.put("id", v.id)
      o.put("since_sent", v.sinceSent.toMillis)
      o.put("received_at", v.receivedAt.toEpochMilli)
      o.put("network_mode", v.networkMode)
      o.put("network_operator", v.networkOperator)
      o.put("is_device_idle", v.isDeviceIdle)
      v.toFetch.foreach(d => o.put("to_fetch", d.toMillis))
    }
  }

  implicit lazy val Decoder: JsonDecoder[ReceivedPushData] = new JsonDecoder[ReceivedPushData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): ReceivedPushData = {
      ReceivedPushData(
        decodeId[Uid]('id),
        decodeDuration('since_sent),
        decodeInstant('received_at),
        NetworkMode.valueOf(decodeString('network_mode)),
        decodeString('network_operator),
        decodeBool('is_device_idle),
        decodeOptDuration('to_fetch)
      )
    }
  }

  implicit object ReceivedPushDataDao extends Dao[ReceivedPushData, Uid] {
    val Id = id[Uid]('_id, "PRIMARY KEY").apply(_.id)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("ReceivedPushes", Id, Data)

    override def apply(implicit cursor: DBCursor): ReceivedPushData = JsonDecoder.decode(cursor.getString(1))
  }

}

case class MissedPushes(time:            Instant,
                        countMissed:     Int,
                        inBackground:    Boolean, //will help rule out false-positives - missed pushes in foreground may be legitimate misses!
                        networkMode:     NetworkMode,
                        networkOperator: String)
