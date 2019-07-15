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
package com.waz.zclient.conversationlist.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, TeamId, UserData}
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}

class ConversationAvatarView (context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.view_conversation_avatar)
  setLayoutParams(new LayoutParams(getDimenPx(R.dimen.conversation_list__row__avatar_size), getDimenPx(R.dimen.conversation_list__row__avatar_size)))

  private val groupBackgroundDrawable = getDrawable(R.drawable.conversation_group_avatar_background)

  private val avatarStartTop = ViewUtils.getView(this, R.id.conversation_avatar_start_top).asInstanceOf[ChatHeadView]
  private val avatarEndTop = ViewUtils.getView(this, R.id.conversation_avatar_end_top).asInstanceOf[ChatHeadView]
  private val avatarStartBottom = ViewUtils.getView(this, R.id.conversation_avatar_start_bottom).asInstanceOf[ChatHeadView]
  private val avatarEndBottom = ViewUtils.getView(this, R.id.conversation_avatar_end_bottom).asInstanceOf[ChatHeadView]

  private val avatarSingle = ViewUtils.getView(this, R.id.avatar_single).asInstanceOf[ChatHeadView]
  private val avatarGroup = ViewUtils.getView(this, R.id.avatar_group).asInstanceOf[View]
  private val avatarGroupSingle = ViewUtils.getView(this, R.id.conversation_avatar_single_group).asInstanceOf[ChatHeadView]

  private val chatheads = Seq(avatarStartTop, avatarEndTop, avatarStartBottom, avatarEndBottom)

  def setMembers(members: Seq[UserData], convId: ConvId, isGroup: Boolean, selfTeam: Option[TeamId]): Unit = {
    isGroup match {
      case true if members.size == 1 =>
        chatheads.foreach(_.clearImage())
        avatarGroupSingle.setUserData(members.head, belongsToSelfTeam = members.head.teamId.exists(selfTeam.contains))
        showGroupSingle()
      case true =>
        avatarGroupSingle.clearImage()
        chatheads.map(Some(_)).zipAll(members.sortBy(_.id.str).take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(ud)) =>
            view.setUserData(ud, belongsToSelfTeam = ud.teamId.exists(selfTeam.contains))
          case (Some(view), None) =>
            view.clearImage()
          case _ =>
        }
        showGrid()
      case false if members.nonEmpty =>
        members.headOption.fold(avatarSingle.clearImage())(ud => avatarSingle.setUserData(ud, belongsToSelfTeam = ud.teamId.exists(selfTeam.contains)))
        showSingle()
      case _ =>
        clearImages()
    }
  }

  private def hideAll(): Unit = {
    avatarGroup.setVisibility(View.GONE)
    avatarSingle.setVisibility(View.GONE)
    avatarGroupSingle.setVisibility(View.GONE)
    setBackground(null)
  }

  private def showGrid(): Unit = {
    avatarGroup.setVisibility(View.VISIBLE)
    avatarSingle.setVisibility(View.GONE)
    avatarGroupSingle.setVisibility(View.GONE)
    setBackground(groupBackgroundDrawable)
  }

  private def showSingle(): Unit = {
    avatarGroup.setVisibility(View.GONE)
    avatarSingle.setVisibility(View.VISIBLE)
    avatarGroupSingle.setVisibility(View.GONE)
    setBackground(null)
  }

  private def showGroupSingle(): Unit = {
    avatarGroup.setVisibility(View.VISIBLE)
    avatarSingle.setVisibility(View.GONE)
    avatarGroupSingle.setVisibility(View.VISIBLE)
    setBackground(groupBackgroundDrawable)
  }

  def setConversationType(conversationType: ConversationType): Unit ={
    conversationType match {
      case ConversationType.Group => showGrid()
      case ConversationType.OneToOne | ConversationType.WaitForConnection => showSingle()
      case _ => hideAll()
    }
  }

  def clearImages(): Unit = {
    chatheads.foreach(_.clearImage())
    avatarSingle.clearImage()
    avatarGroupSingle.clearImage()
  }
}
