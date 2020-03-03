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

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.CallParticipantsView
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{FragmentHelper, R}

class CallParticipantsFragment extends FragmentHelper {
  private lazy val themeController = inject[ThemeController]

  private lazy val toolbar = returning(view[Toolbar](R.id.toolbar)) { vh =>
    controller.theme.map(themeController.getTheme).onUi { theme =>
      vh.foreach(v => getStyledDrawable(R.attr.backNavigationIcon, theme).foreach(v.setNavigationIcon))
    }
  }
  private lazy val participantsView = view[CallParticipantsView](R.id.full_call_participants)

  private lazy val controller = inject[CallController]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    controller.isVideoCall.onUi {
      case true => getView.setBackgroundColor(getColor(R.color.calling_video_overlay))
      case false => getView.setBackgroundColor(Color.TRANSPARENT)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_calling_participants, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    toolbar
    participantsView
  }

  override def onResume(): Unit = {
    super.onResume()
    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit =
        getFragmentManager.popBackStack()
    }))
  }

  override def onPause(): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(null))
    super.onPause()
  }
}

object CallParticipantsFragment {

  def apply(): CallParticipantsFragment = new CallParticipantsFragment()

  val Tag: String = getClass.getSimpleName
}
