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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout.LayoutParams
import android.widget.{FrameLayout, ImageView}
import com.waz.model.AssetId
import com.waz.model.ConversationData.ConversationType
import com.waz.utils.events.{Signal, SourceSignal}
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

  private val transparentDrawable = new ColorDrawable(Color.TRANSPARENT)
  private val groupBackgroundDrawable = getDrawable(R.drawable.conversation_group_avatar_background)

  private val avatarStartTop = ViewUtils.getView(this, R.id.conversation_avatar_start_top).asInstanceOf[ImageView]
  private val avatarEndTop = ViewUtils.getView(this, R.id.conversation_avatar_end_top).asInstanceOf[ImageView]
  private val avatarStartBottom = ViewUtils.getView(this, R.id.conversation_avatar_start_bottom).asInstanceOf[ImageView]
  private val avatarEndBottom = ViewUtils.getView(this, R.id.conversation_avatar_end_bottom).asInstanceOf[ImageView]

  private val avatarSingle = ViewUtils.getView(this, R.id.avatar_single).asInstanceOf[ImageView]
  private val avatarGroup = ViewUtils.getView(this, R.id.avatar_group).asInstanceOf[View]

  private val imageSources = Seq.fill(4)(Signal[ImageSource]())

  Seq(avatarStartTop, avatarEndTop, avatarStartBottom, avatarEndBottom).zip(imageSources).foreach{ images =>
    images._1.setImageDrawable(new ImageAssetDrawable(images._2, scaleType = ScaleType.CenterCrop, request = RequestBuilder.Single, background = Some(new ColorDrawable(getColor(R.color.black_8)))))
  }

  avatarSingle.setImageDrawable(new ImageAssetDrawable(imageSources.head, scaleType = ScaleType.CenterCrop, request = RequestBuilder.Round))

  def setMembers(membersPictures: Seq[AssetId], conversationType: ConversationType): Unit = {
    conversationType match {
      case ConversationType.Group =>
        avatarGroup.setVisibility(View.VISIBLE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(groupBackgroundDrawable)
        imageSources.zipAll(membersPictures.take(4).map(Some(_)), Signal.empty[ImageSource], None).foreach {
          case (imageSource: SourceSignal[ImageSource], Some(assetId)) => imageSource ! WireImage(assetId)
          case (imageSource: SourceSignal[ImageSource], None) => imageSource ! NoImage()
        }
      case ConversationType.OneToOne if membersPictures.nonEmpty =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.VISIBLE)
        setBackground(transparentDrawable)
        membersPictures.headOption.foreach(imageSources.head ! WireImage(_))
      case _ =>
        imageSources.foreach(_ ! NoImage())
    }
  }
}

object ConversationAvatarView {
}
