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
package com.waz.zclient.participants.fragments

import android.os.Bundle
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.returning
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.participants.{ParticipantsAdapter, ParticipantsController}
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}
import com.waz.zclient.{FragmentHelper, R}

class AllGroupParticipantsFragment extends FragmentHelper {

  private lazy val participantsController = inject[ParticipantsController]

  private lazy val participantsAdapter = returning(new ParticipantsAdapter(showPeopleOnly = true)) {
    _.onClick(participantsController.onShowUser ! Some(_))
  }

  private lazy val searchBox = view[SearchEditText](R.id.search_box)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.all_participants_fragment, container, false)


  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val recyclerView = findById[RecyclerView](R.id.recycler_view)
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
    recyclerView.setAdapter(participantsAdapter)
    searchBox.foreach {
      _.setCallback(new PickerSpannableEditText.Callback{
        override def onRemovedTokenSpan(element: PickableElement): Unit = {}
        override def afterTextChanged(s: String): Unit = {
          participantsAdapter.filter ! s
        }
      })
    }
  }
}

object AllGroupParticipantsFragment {
  val Tag = implicitLogTag
}

