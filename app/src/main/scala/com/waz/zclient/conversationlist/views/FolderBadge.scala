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
package com.waz.zclient.conversationlist.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._
import com.waz.zclient.log.LogUI._

class FolderBadge(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper with DerivedLogTag { self =>
  import FolderBadge._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.folder_badge)

  private lazy val textView = findById[TypefaceTextView](R.id.folder_badge_text)

  def setUnreadCount(unreadCount: Int): Unit = {
    verbose(l"badge count: $unreadCount")
    this.setVisible(unreadCount > 0)
    textView.setVisible(unreadCount > 0)
    if (unreadCount > MaxBadgeCount) textView.setText(OverMaxBadge)
    else if (unreadCount > 0) textView.setText(unreadCount.toString)
  }
}

object FolderBadge {
  val MaxBadgeCount = 99
  val OverMaxBadge = "99+"
}
