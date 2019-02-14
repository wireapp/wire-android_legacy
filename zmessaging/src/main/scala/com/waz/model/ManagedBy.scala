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

import com.waz.utils.JsonDecoder.decodeOptString
import org.json.JSONObject

object ManagedBy {

  def apply(s: String): ManagedBy = s match {
    case "wire" => Wire
    case "scim" => SCIM
    case u => Unknown(u)
  }

  sealed trait ManagedBy
  final case object Wire extends ManagedBy {
    override def toString: String = "wire"
  }
  final case object SCIM extends ManagedBy {
    override def toString: String = "scim"
  }
  final case class Unknown(str: String) extends ManagedBy {
    override def toString: String = str
  }

  def decodeOptManagedBy(s: Symbol)(implicit js: JSONObject): Option[ManagedBy] = decodeOptString(s).map(ManagedBy(_))
}

