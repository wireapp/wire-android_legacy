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
package com.waz.zclient.calling.views

import android.content.Context
import android.support.v7.widget.LinearLayoutManager.VERTICAL
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import com.waz.utils.events.EventStream
import com.waz.zclient.calling.CallParticipantsAdapter
import com.waz.zclient.{R, ViewHelper}

class CallParticipantsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends RecyclerView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val adapter = new CallParticipantsAdapter()

  val onShowAllClicked: EventStream[Unit] = adapter.onShowAllClicked

  private val isScrollable =
    context
      .getTheme
      .obtainStyledAttributes(attrs, R.styleable.CallParticipantsView, 0, 0)
      .getBoolean(R.styleable.CallParticipantsView_isScrollable, true)

  private val layoutManager =
    if (isScrollable)
      new LinearLayoutManager(context, VERTICAL, false)
    else
      new LinearLayoutManager(context, VERTICAL, false) {
        override def canScrollVertically: Boolean = false
      }

  setLayoutManager(layoutManager)
  setAdapter(adapter)

  def setMaxRows(maxRows: Int): Unit = adapter.setMaxRows(maxRows)
}
