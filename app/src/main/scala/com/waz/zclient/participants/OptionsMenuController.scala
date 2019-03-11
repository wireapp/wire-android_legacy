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

import com.waz.log.LogShow.SafeToLog
import com.waz.utils.events.{SourceStream, _}
import com.waz.zclient.R
import com.waz.zclient.participants.OptionsMenuController._

trait OptionsMenuController {
  val title: Signal[Option[String]]
  val optionItems: Signal[Seq[MenuItem]]
  val onMenuItemClicked: SourceStream[MenuItem]
  val selectedItems: Signal[Set[MenuItem]]
}

object OptionsMenuController {
  trait MenuItem extends SafeToLog {
    val titleId: Int
    val iconId: Option[Int]
    val colorId: Option[Int]
  }

  case class BaseMenuItem(titleId: Int,
                          iconId: Option[Int] = None,
                          colorId: Option[Int] = Some(R.color.graphite)) extends MenuItem {

    override def toString: String = this.getClass.getSimpleName
  }
}

class BaseOptionsMenuController(options: Seq[MenuItem], titleString: Option[String]) extends OptionsMenuController {
  override val title: Signal[Option[String]] = Signal.const(titleString)
  override val optionItems: Signal[Seq[MenuItem]] = Signal(options)
  override val onMenuItemClicked: SourceStream[MenuItem] = EventStream()
  override val selectedItems: Signal[Set[MenuItem]] = Signal.const(Set())
}
