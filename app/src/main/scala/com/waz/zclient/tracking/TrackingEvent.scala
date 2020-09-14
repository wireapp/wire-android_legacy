/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.waz.zclient.tracking

object TeamsEnteredVerification {
  case class Method(str: String)
  object CopyPaste extends Method("copy_paste")
  object Manual extends Method("manual")
}
object TeamAcceptedTerms {
  case class Occurrence(str: String)
  object AfterName extends Occurrence("after_name")
  object AfterPassword extends Occurrence("after_password")
}
object AvailabilityChanged {
  case class Method(str: String)
  object Settings extends Method("settings")
  object ListHeader extends Method("list_header")
}

