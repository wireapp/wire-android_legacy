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
package com.waz.zclient.collection.views

import android.content.Context
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.model.Liking
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.utils.RichView
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.{R, ViewHelper}
import com.waz.ZLog.ImplicitTag._

class SingleImageViewToolbar(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  inflate(R.layout.single_image_view_toolbar_layout)

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val messageActionsController = inject[MessageActionsController]
  private lazy val collectionController = inject[CollectionController]

  private val likeButton: GlyphButton = findById(R.id.toolbar_like)
  private val downloadButton: GlyphButton = findById(R.id.toolbar_download)
  private val shareButton: GlyphButton = findById(R.id.toolbar_share)
  private val deleteButton: GlyphButton = findById(R.id.toolbar_delete)
  private val viewButton: GlyphButton = findById(R.id.toolbar_view)

  val message = collectionController.focusedItem
    .collect { case Some(msg) => msg }
    .disableAutowiring()


  val likedBySelf = collectionController.focusedItem flatMap {
    case Some(m) => zms.flatMap { z =>
      z.reactionsStorage.signal((m.id, z.selfUserId)).map(_.action == Liking.like).orElse(Signal const false)
    }
    case None => Signal.const(false)
  }

  likedBySelf.on(Threading.Ui){ updateLikeButton }

  messageActionsController.onDeleteConfirmed.on(Threading.Background){
    _ => collectionController.focusedItem ! None
  }

  private def updateLikeButton(liked: Boolean): Unit = {
    if (liked) {
      likeButton.setText(R.string.glyph__liked)
    } else {
      likeButton.setText(R.string.glyph__like)
    }
  }

  Seq(likeButton, downloadButton, shareButton, deleteButton, viewButton)
    .foreach(_.setPressedBackgroundColor(ContextCompat.getColor(getContext, R.color.light_graphite)))

  likeButton.onClick( Signal(message, likedBySelf).head.foreach{
    case (msg, true) =>
      messageActionsController.onMessageAction ! (MessageAction.Unlike, msg)
    case (msg, false) =>
      messageActionsController.onMessageAction ! (MessageAction.Like, msg)
  })

  downloadButton.onClick( message.head.foreach(msg => messageActionsController.onMessageAction ! (MessageAction.Save, msg)))
  shareButton.onClick( message.head.foreach(msg => messageActionsController.onMessageAction ! (MessageAction.Forward, msg)))

  deleteButton.onClick( message.head.foreach { msg =>
    messageActionsController.onMessageAction ! (MessageAction.Delete, msg)
  })

  viewButton.onClick( message.head.foreach { msg => messageActionsController.onMessageAction ! (MessageAction.Reveal, msg)})
}
