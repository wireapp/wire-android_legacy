/**
 * Wire
 * Copyright (C) 2021 Wire Swiss GmbH
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
package com.waz.zclient.calling

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.calling.controllers.CallController
import com.wire.signals.EventContext
import com.waz.zclient.calling.CallingGridAdapter.MAX_PARTICIPANTS_PER_PAGE

class CallingGridAdapter(implicit context: Context, eventContext: EventContext, inj: Injector, fragment: Fragment)
  extends FragmentStateAdapter(fragment) with Injectable with DerivedLogTag {

  private lazy val callController = inject[CallController]

  var numberOfParticipants : Int = callController.allParticipants.map(_.size).currentValue.getOrElse(0)

  callController.allParticipants.map(_.size).onChanged.foreach { it =>
    numberOfParticipants = it
  }

  override def getItemCount(): Int = if (numberOfParticipants == 0) 0
  else if (numberOfParticipants % MAX_PARTICIPANTS_PER_PAGE == 0) numberOfParticipants / MAX_PARTICIPANTS_PER_PAGE
  else (numberOfParticipants / MAX_PARTICIPANTS_PER_PAGE) + 1

  override def createFragment(position: Int): Fragment = CallingGridFragment.newInstance(position)

}

object CallingGridAdapter {
  val MAX_PARTICIPANTS_PER_PAGE = 8
}
