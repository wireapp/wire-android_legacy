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

import java.util.Date

import com.waz.api.ErrorType
import com.waz.api.impl.ErrorResponse
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.utils.Identifiable
import com.waz.utils.wrappers.{DB, DBCursor}

case class ErrorData(override val id: Uid,
                     errType:         ErrorType,
                     users:           Seq[UserId] = Nil,
                     messages:        Seq[MessageId] = Nil,
                     convId:          Option[ConvId] = None,
                     responseCode:    Int = 0,
                     responseMessage: String = "",
                     responseLabel:   String = "",
                     time: Date = new Date) extends Identifiable[Uid]

object ErrorData {

  def apply(errType: ErrorType, resp: ErrorResponse): ErrorData =
    new ErrorData(Uid(), errType, responseCode = resp.code, responseMessage = resp.message, responseLabel = resp.label)

  def apply(errType: ErrorType, resp: ErrorResponse, convId: ConvId): ErrorData =
    new ErrorData(Uid(), errType, convId = Some(convId), responseCode = resp.code, responseMessage = resp.message, responseLabel = resp.label)

  def apply(errType: ErrorType, resp: ErrorResponse, convId: ConvId, users: Set[UserId]): ErrorData =
    new ErrorData(Uid(), errType, convId = Some(convId), users = users.toSeq, responseCode = resp.code, responseMessage = resp.message, responseLabel = resp.label)

  implicit object ErrorDataDao extends Dao[ErrorData, Uid] {
    val Id = uid('_id, "PRIMARY KEY")(_.id)
    val Type = text[ErrorType]('err_type, _.name(), ErrorType.valueOf)(_.errType)
    val Users = text[Seq[UserId]]('users, _.mkString(","), { str => if(str.isEmpty) Nil else str.split(',').map(str => UserId(str)) })(_.users)
    val Messages = text[Seq[MessageId]]('messages, _.mkString(","), { str => if(str.isEmpty) Nil else str.split(',').map(str => MessageId(str)) })(_.messages)
    val ConvId = opt(id[ConvId]('conv_id))(_.convId)
    val ResCode = int('res_code)(_.responseCode)
    val ResMessage = text('res_msg)(_.responseMessage)
    val ResLabel = text('res_label)(_.responseLabel)
    val Time = date('time)(_.time)

    override val idCol = Id

    override val table = Table("Errors", Id, Type, Users, Messages, ConvId, ResCode, ResMessage, ResLabel, Time)

    override def apply(implicit cursor: DBCursor): ErrorData = ErrorData(Id, Type, Users, Messages, ConvId, ResCode, ResMessage, ResLabel, Time)

    def listErrors(implicit db: DB) = list(db.query(table.name, null, null, null, null, null, Time.name))
  }

  object AssetError {
    def unapply(err: ErrorData): Option[Seq[MessageId]] = err match {
      case ErrorData(_, ErrorType.CANNOT_SEND_ASSET_FILE_NOT_FOUND | ErrorType.CANNOT_SEND_ASSET_TOO_LARGE, _, ms, _, _, _, _, _) => Some(ms)
      case _ => None
    }
  }
}
