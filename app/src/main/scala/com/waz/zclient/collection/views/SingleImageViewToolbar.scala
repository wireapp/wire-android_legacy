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
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.model.{Liking, UserId}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import MessageAction._
import android.view.View
import com.waz.content.ReactionsStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag

class SingleImageViewToolbar(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  inflate(R.layout.single_image_view_toolbar_layout)

  private lazy val selfUserId = inject[Signal[UserId]]
  private lazy val reactionsStorage = inject[Signal[ReactionsStorage]]
  private lazy val messageActionsController = inject[MessageActionsController]
  private lazy val collectionController = inject[CollectionController]

  private val likeButton:     GlyphButton = findById(R.id.toolbar_like)
  private val downloadButton: GlyphButton = findById(R.id.toolbar_download)
  private val shareButton:    GlyphButton = findById(R.id.toolbar_share)
  private val deleteButton:   GlyphButton = findById(R.id.toolbar_delete)
  private val viewButton:     GlyphButton = findById(R.id.toolbar_view)

  val message = collectionController.focusedItem collect { case Some(msg) => msg }

  message.map(_.ephemeral.isEmpty).onUi { visible =>
    Seq(likeButton, shareButton, viewButton).map(_.getParent.asInstanceOf[View]).foreach(_.setVisible(visible))
  }

  (for {
    self <- selfUserId
    msg <- message
  } yield msg.ephemeral.isEmpty || msg.userId == self).onUi { visible =>
    downloadButton.getParent.asInstanceOf[View].setVisible(visible)
  }

  val likedBySelf = Signal(collectionController.focusedItem, selfUserId, reactionsStorage) flatMap {
    case (Some(m), self, reactions) =>
      reactions.signal((m.id, self)).map(_.action == Liking.like).orElse(Signal const false)
    case _ => Signal.const(false)
  }

  likedBySelf.map(if (_) R.string.glyph__liked else R.string.glyph__like).on(Threading.Ui)(likeButton.setText)

  messageActionsController.onDeleteConfirmed.on(Threading.Background){
    _ => collectionController.focusedItem ! None
  }

  Seq(likeButton, downloadButton, shareButton, deleteButton, viewButton)
    .foreach(_.setPressedBackgroundColor(getColor(R.color.light_graphite)))

  likeButton.onClick( Signal(message, likedBySelf).head.foreach{
    case (msg, true) => messageActionsController.onMessageAction ! (Unlike, msg)
    case (msg, false) => messageActionsController.onMessageAction ! (Like, msg)
  })
  downloadButton.onClick( message.head.foreach(msg => messageActionsController.onMessageAction ! (Save, msg)))
  shareButton.onClick( message.head.foreach(msg => messageActionsController.onMessageAction ! (Forward, msg)))

  deleteButton.onClick( message.head.foreach { msg =>
    messageActionsController.onMessageAction ! (Delete, msg)
  })

  viewButton.onClick(message.head.foreach { msg => messageActionsController.onMessageAction ! (Reveal, msg)})
}
