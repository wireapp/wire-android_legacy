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

import android.database.DatabaseUtils.{sqlEscapeString => escape}
import com.waz.db.Col._
import com.waz.db.{Dao2, Reader, iteratingWithReader}
import com.waz.log.LogShow.SafeToLog
import com.waz.utils.JsonEncoder.encodeInstant
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder, RichWireInstant}
import org.json.JSONObject

final case class Liking(message: MessageId, user: UserId, timestamp: RemoteInstant, action: Liking.Action)
  extends Identifiable[Liking.Id] {
  override val id: Liking.Id = (message, user)

  def max(other: Liking) =
    if (other.message == message && other.user == user && other.timestamp.isAfter(timestamp)) other else this
}

object Liking {
  type Id = (MessageId, UserId)

  def like: Action = Action.Like
  def unlike: Action = Action.Unlike

  @SerialVersionUID(1L) sealed abstract class Action(val serial: Int) extends Serializable with SafeToLog

  object Action {
    case object Unlike extends Action(0)
    case object Like extends Action(1)

    val decode: Int => Action = {
      case Unlike.serial => Unlike
      case Like.serial => Like
    }

    val values = Set(Like, Unlike)
  }

  type Aggregate = (MessageId, Map[UserId, (Action, RemoteInstant)])

  implicit object LikingDao extends Dao2[Liking, MessageId, UserId] {
    val Message = id[MessageId]('message_id).apply(_.message)
    val User = id[UserId]('user_id).apply(_.user)
    val Timestamp = remoteTimestamp('timestamp)(_.timestamp)
    val ActionCol = int[Action]('action, _.serial, Liking.Action.decode)(_.action)

    override val idCol = (Message, User)

    override val table = Table("Likings", Message, User, Timestamp, ActionCol)

    override def apply(implicit cursor: DBCursor): Liking = Liking(Message, User, Timestamp, ActionCol)

    def findForMessage(id: MessageId)(implicit db: DB) = iterating(find(Message, id))

    def findForMessages(ids: Set[MessageId])(implicit db: DB) = iterating {
      db.query(table.name, null, s"${Message.name} in (${ids.map(id => escape(id.str)).mkString(", ")})", null, null, null, null)
    }

    def findMaxTime(implicit db: DB) =
      iteratingWithReader(InstantReader)(db.rawQuery(s"SELECT MAX(${Timestamp.name}) FROM ${table.name}"))
        .acquire(t => if (t.hasNext) t.next else RemoteInstant.Epoch)

    object InstantReader extends Reader[RemoteInstant] {
      override def apply(implicit c: DBCursor): RemoteInstant = Timestamp.load(c, 0)
    }
  }

  implicit lazy val LikingEncoder: JsonEncoder[Liking] = new JsonEncoder[Liking] {
    override def apply(liking: Liking): JSONObject = JsonEncoder { o =>
      o.put("message", liking.message.str)
      o.put("user", liking.user.str)
      o.put("timestamp", encodeInstant(liking.timestamp.instant))
      o.put("action", liking.action.serial)
    }
  }

  import com.waz.utils.JsonDecoder._

  implicit lazy val LikingDecoder: JsonDecoder[Liking] = new JsonDecoder[Liking] {
    override def apply(implicit js: JSONObject): Liking = Liking(
      decodeId[MessageId]('message),
      decodeId[UserId]('user),
      decodeRemoteInstant('timestamp),
      Action.decode(decodeInt('action)))
  }
}
