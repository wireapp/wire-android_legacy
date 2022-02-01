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
import com.waz.zclient.calling.AllParticipantsAdapter.MAX_PARTICIPANTS_PER_PAGE

class AllParticipantsAdapter(implicit context: Context, eventContext: EventContext, inj: Injector, fragment: Fragment)
  extends FragmentStateAdapter(fragment) with Injectable with DerivedLogTag {

  private val callController = inject[CallController]

  var numberOfParticipants: Int = 0
  var previousPagesCount: Int = 0

  callController.allParticipants.map(_.size).foreach { size =>
    numberOfParticipants = size
    getPagesCount match {
      case n if n != previousPagesCount =>
        notifyDataSetChanged()
        previousPagesCount = n
      case _ =>
    }
  }

  override def getItemCount(): Int = getPagesCount()

  override def createFragment(position: Int): Fragment = CallingGridFragment.newInstance(position)

  private def getPagesCount(): Int =
    if (numberOfParticipants == 0) 1
    else if (numberOfParticipants % MAX_PARTICIPANTS_PER_PAGE == 0) numberOfParticipants / MAX_PARTICIPANTS_PER_PAGE
    else (numberOfParticipants / MAX_PARTICIPANTS_PER_PAGE) + 1

}

object AllParticipantsAdapter {
  val MAX_PARTICIPANTS_PER_PAGE = 8
}
