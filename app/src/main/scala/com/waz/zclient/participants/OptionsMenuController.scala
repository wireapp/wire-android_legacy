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

import androidx.annotation.StringRes
import com.waz.log.LogShow.SafeToLog
import com.waz.utils.events.{SourceStream, _}
import com.waz.zclient.participants.OptionsMenuController._
import com.waz.zclient.{R, WireApplication}

trait OptionsMenuController {
  val title: Signal[Option[String]]
  val optionItems: Signal[Seq[MenuItem]]
  val onMenuItemClicked: SourceStream[MenuItem]
  val selectedItems: Signal[Set[MenuItem]]
}

object OptionsMenuController {
  trait MenuItem extends SafeToLog {
    val title: String
    val iconId: Option[Int]
    val colorId: Option[Int]
  }

  class BaseMenuItem(override val title: String,
                     override val iconId: Option[Int] = None,
                     override val colorId: Option[Int] = Some(R.color.graphite)) extends MenuItem {

    def this(@StringRes titleId: Int, iconId: Option[Int]) = this(
      WireApplication.APP_INSTANCE.getString(titleId),
      iconId
    )

    def this(@StringRes titleId: Int, iconId: Option[Int], colorId: Option[Int]) = this(
      WireApplication.APP_INSTANCE.getString(titleId),
      iconId,
      colorId
    )

    override def toString: String = this.getClass.getSimpleName
  }
}

class BaseOptionsMenuController(options: Seq[MenuItem], titleString: Option[String]) extends OptionsMenuController {
  override val title: Signal[Option[String]] = Signal.const(titleString)
  override val optionItems: Signal[Seq[MenuItem]] = Signal(options)
  override val onMenuItemClicked: SourceStream[MenuItem] = EventStream()
  override val selectedItems: Signal[Set[MenuItem]] = Signal.const(Set())
}
