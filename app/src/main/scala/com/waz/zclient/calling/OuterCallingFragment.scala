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
package com.waz.zclient.calling

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}

class OuterCallingFragment extends FragmentHelper {

  lazy val testButton = returning(view[View](R.id.test_button)) { vh =>
    vh.onClick { _ =>
      getChildFragmentManager.beginTransaction
        .setCustomAnimations(
          R.anim.fragment_animation_second_page_slide_in_from_right,
          R.anim.fragment_animation_second_page_slide_out_to_left,
          R.anim.fragment_animation_second_page_slide_in_from_left,
          R.anim.fragment_animation_second_page_slide_out_to_right)
        .replace(R.id.controls_layout, CallParticipantsFragment(), CallParticipantsFragment.Tag)
        .addToBackStack(CallParticipantsFragment.Tag)
        .commit
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_calling_outer, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)

    testButton

    getChildFragmentManager.beginTransaction
      .replace(R.id.controls_layout, CallingFragment.newInstance, CallingFragment.Tag)
      .addToBackStack(CallingFragment.Tag)
      .commit

  }
}

object OuterCallingFragment {
  val Tag = implicitLogTag
  def apply(): OuterCallingFragment = new OuterCallingFragment()
}
