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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState.{OtherCalling, SelfCalling, SelfConnected, SelfJoining}
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.CallingMiddleLayout.CallDisplay
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.utils.ContextUtils.getDimenPx
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class CallingMiddleLayout(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) =  this(context, null)

  private lazy val chathead = findById[ChatheadView](R.id.call_chathead)
  private lazy val participants = findById[CallParticipantsView](R.id.call_participants)

  lazy val onShowAllClicked = participants.onShowAllClicked

  override def onMeasure(widthSpec: Int, heightSpec: Int): Unit = {
    super.onMeasure(widthSpec, heightSpec)

    participants.setMaxRows( this.getMeasuredHeight / getDimenPx(R.dimen.user_row_height) )
  }

  LayoutInflater.from(context).inflate(R.layout.calling_middle_layout, this, true)

  private val controller = inject[CallController]

  private val callState: Signal[CallInfo.CallState] = Signal(controller.callState, controller.prevCallStateOpt).collect {
    case (OtherCalling, _)                 => OtherCalling
    case (SelfCalling, _)                  => SelfCalling
    case (SelfConnected, _)                => SelfConnected
    case (SelfJoining, Some(OtherCalling)) => OtherCalling
    case (SelfJoining, Some(SelfCalling))  => SelfCalling
  }

  Signal(callState,  controller.showVideoView, controller.isGroupCall).map {
    case (_, false, false)           => CallDisplay.Chathead
    case (OtherCalling, false, true) => CallDisplay.Chathead
    case (SelfConnected, _, true)    => CallDisplay.Participants
    case _                           => CallDisplay.Empty
  }.onUi { display =>
    chathead.setVisible(display == CallDisplay.Chathead)
    participants.setVisible(display == CallDisplay.Participants)
  }

  Signal(callState, controller.callerId, controller.otherUser.map(_.map(_.id))).onUi {
    case (OtherCalling, callerId, _) => chathead.setUserId(callerId)
    case (_, _, Some(userId))        => chathead.setUserId(userId)
    case _ =>
  }

}

object CallingMiddleLayout {
  object CallDisplay {
    sealed trait CallDisplay
    case object Chathead extends CallDisplay
    case object Participants extends CallDisplay
    case object Empty extends CallDisplay
  }
}
