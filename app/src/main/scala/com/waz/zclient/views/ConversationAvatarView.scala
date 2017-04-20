/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.views

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout.LayoutParams
import android.widget.{FrameLayout, ImageView}
import com.waz.model.{AssetId, UserId}
import com.waz.model.ConversationData.ConversationType
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.{ImageSource, NoImage, WireImage}
import com.waz.zclient.{R, ViewHelper}

class ConversationAvatarView (context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.view_conversation_avatar)
  setLayoutParams(new LayoutParams(getDimenPx(R.dimen.conversation_list__row__avatar_size), getDimenPx(R.dimen.conversation_list__row__avatar_size)))

  private val groupBackgroundDrawable = getDrawable(R.drawable.conversation_group_avatar_background)

  private val avatarStartTop = ViewUtils.getView(this, R.id.conversation_avatar_start_top).asInstanceOf[ChatheadView]
  private val avatarEndTop = ViewUtils.getView(this, R.id.conversation_avatar_end_top).asInstanceOf[ChatheadView]
  private val avatarStartBottom = ViewUtils.getView(this, R.id.conversation_avatar_start_bottom).asInstanceOf[ChatheadView]
  private val avatarEndBottom = ViewUtils.getView(this, R.id.conversation_avatar_end_bottom).asInstanceOf[ChatheadView]

  private val avatarSingle = ViewUtils.getView(this, R.id.avatar_single).asInstanceOf[ChatheadView]
  private val avatarGroup = ViewUtils.getView(this, R.id.avatar_group).asInstanceOf[View]

  private val imageSources = Seq.fill(4)(Signal[ImageSource]())

  private val chatheads = Seq(avatarStartTop, avatarEndTop, avatarStartBottom, avatarEndBottom)

  def setMembers(members: Seq[UserId], conversationType: ConversationType): Unit = {
    conversationType match {
      case ConversationType.Group =>
        chatheads.map(Some(_)).zipAll(members.take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(uid)) =>
            view.setUserId(uid)
          case (Some(view), None) =>
            view.clearUser()
          case _ =>
        }
      case ConversationType.OneToOne if members.nonEmpty =>
        members.headOption.fold(avatarSingle.clearUser())(avatarSingle.setUserId)
      case _ =>
        imageSources.foreach(_ ! NoImage())
    }
  }

  def setConversationType(conversationType: ConversationType): Unit ={
    conversationType match {
      case ConversationType.Group =>
        avatarGroup.setVisibility(View.VISIBLE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(groupBackgroundDrawable)
      case ConversationType.OneToOne | ConversationType.WaitForConnection =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.VISIBLE)
        setBackground(null)
      case _ =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(null)
    }
  }
}

object ConversationAvatarView {
}
