/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.participants

import com.waz.utils.events.{SourceStream, _}
import com.waz.zclient.R
import com.waz.zclient.participants.OptionsMenuController._

trait OptionsMenuController {
  val optionItems: Signal[Seq[MenuItem]]
  val onMenuItemClicked: SourceStream[MenuItem]
}

object OptionsMenuController {
  case class MenuItem(titleId: Int, glyphId: Option[Int] = None, colorId: Option[Int] = Some(R.color.graphite))
}

case class BaseOptionsMenuController(options: Seq[MenuItem]) extends OptionsMenuController {
  override val optionItems: Signal[Seq[MenuItem]] = Signal(options)
  override val onMenuItemClicked: SourceStream[MenuItem] = EventStream()
}
