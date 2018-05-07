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
import android.view._
import com.waz.ZLog.ImplicitTag._
import com.waz.model._
import com.waz.utils.events.Signal
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.CallParticipantsView.CallParticipantInfo
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.common.views.SingleUserRowView.Theme
import com.waz.zclient.utils.{UiStorage, UserSignal}

class CallParticipantsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends RecyclerView(context, attrs, defStyleAttr) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  private val themeController = inject[ThemeController]
  private val callController = inject[CallController]
  private implicit val uiStorage: UiStorage = inject[UiStorage]

  val layoutManager = new LinearLayoutManager(context, VERTICAL, false)
  setLayoutManager(layoutManager)
  setAdapter(new CallParticipantsAdapter(context))

  class CallParticipantsAdapter(context: Context) extends RecyclerView.Adapter[CallParticipantViewHolder] {

    var participantsToDisplay = Seq.empty[CallParticipantInfo]
    var selfTeamId = Option.empty[TeamId]
    var theme: Theme = Theme.TransparentDark

    callController.callingZms.map(_.teamId).onUi { teamId =>
      selfTeamId = teamId
      notifyDataSetChanged()
    }

    callController.participantIdsToDisplay.flatMap(users => Signal.sequence(users.map(UserSignal(_)):_*)).onUi { data =>
      participantsToDisplay = data.map(u => CallParticipantInfo(u, videoEnabled = false))//TODO: get video info
      notifyDataSetChanged()
    }

    Signal(callController.isVideoCall, themeController.darkThemeSet).map{
      case (true, _)      => Theme.TransparentDark
      case (false, true)  => Theme.TransparentDark
      case (false, false) => Theme.TransparentLight
    }.onUi { theme =>
      this.theme = theme
      notifyDataSetChanged()
    }

    override def getItemCount: Int = participantsToDisplay.size

    override def onBindViewHolder(holder: CallParticipantViewHolder, position: Int): Unit ={
      participantsToDisplay.lift(position).foreach(info => holder.bind(info, selfTeamId))
      holder.itemView.asInstanceOf[SingleUserRowView].setTheme(theme)
    }

    override def onCreateViewHolder(parent: ViewGroup, viewType: Int): CallParticipantViewHolder = {
      new CallParticipantViewHolder(ViewHelper.inflate[SingleUserRowView](R.layout.single_user_row, parent, addToParent = false))
    }
  }
}

object CallParticipantsView {
  sealed case class CallParticipantInfo(userData: UserData, videoEnabled: Boolean)
}

class CallParticipantViewHolder(view: SingleUserRowView) extends RecyclerView.ViewHolder(view) {
  def bind(callParticipantInfo: CallParticipantInfo, teamId: Option[TeamId]): Unit = {
    view.setUserData(callParticipantInfo.userData, teamId)
    view.setVideoEnabled(callParticipantInfo.videoEnabled)
  }
}
