/**
  * Wire
  * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.messages.parts

import android.content.Context
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.Message.Status
import com.waz.model.{MessageData, MessageId, UserId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, _}
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.controllers.ScreenController
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.{Footer, SyncEngineSignals}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils.{getColor, getQuantityString, _}
import com.waz.zclient.utils.{RichView, ZTimeFormatter}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import org.threeten.bp.DateTimeUtils

import scala.concurrent.duration._

//TODO timestamp/like details animation
//TODO like hint
//TODO tracking
//TODO recycle chatheads in like details view - see MembersGridView for more
class FooterPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with Footer with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_content)

  val selection = inject[SelectionController].messages
  val controller = inject[FooterController]
  val signals = inject[SyncEngineSignals]

  private val likeButton: LikeButton = findById(R.id.like__button)
  private val timeStampAndStatus: TextView = findById(R.id.timestamp_and_status)
  private val likeDetails: LikeDetailsLayout = findById(R.id.like_details)

  private var hideTimestampFuture = CancellableFuture.successful[Unit](())

  private var pos = -1

  private val message = Signal[MessageData]()
  private val isLiked = Signal[Boolean](false)

  val focused = selection.focused.zip(message.map(_.id)).map {
    case (Some(selectedId), thisId) => selectedId == thisId
    case _ => false
  }

  focused.zip(isLiked).map {
    case (true, _) => true
    case _ => false
  }.on(Threading.Ui)(timeStampAndStatus.setVisible)

  focused.zip(isLiked).map {
    case (false, true) => true
    case _ => false
  }.on(Threading.Ui)(likeDetails.setVisible)

  Signal(focused, isLiked, message).on(Threading.Ui) {
    case (true, true, msg) =>
      hideTimestampFuture.cancel()
      hideTimestampFuture = CancellableFuture.delay(3.seconds).map[Unit](_ => selection.toggleFocused(msg.id))(Threading.Background)
    case _ =>
      hideTimestampFuture.cancel()
  }

  likeButton.likeClicked { _ => if (focused.currentValue.contains(true))
    message.currentValue.foreach { msg =>
      hideTimestampFuture.cancel()
      selection.toggleFocused(msg.id)
    }
  }

  val conv = signals.conv(message)

  message.zip(conv.map(_.convType)).on(Threading.Ui) {
    case (m, convType) =>
      val timestamp = ZTimeFormatter.getSingleMessageTime(context, DateTimeUtils.toDate(m.time))
      val text = if (controller.isSelfUser(m.userId)) {
        m.state match {
          case Status.PENDING => getString(R.string.message_footer__status__sending)
          case Status.SENT => getString(R.string.message_footer__status__sent, timestamp)
          case Status.DELETED => getString(R.string.message_footer__status__deleted, timestamp)
          case Status.DELIVERED =>
            //TODO AN-4474 Uncomment to show delivered state
            //if (convType == IConversation.Type.GROUP)
            context.getString(R.string.message_footer__status__sent, timestamp)
          //else getString(R.string.message_footer__status__delivered, timestamp)
          case Status.FAILED |
               Status.FAILED_READ => context.getString(R.string.message_footer__status__failed)
          case _ => timestamp
        }
      } else timestamp
      timeStampAndStatus.setText(s"$pos: $text")
      TextViewUtils.linkifyText(timeStampAndStatus, ContextCompat.getColor(context, R.color.accent_red), false, new Runnable() {
        def run() = {
          //TODO retry
        }
      })
  }

  override def set(pos: Int, msg: MessageAndLikes): Unit = {
    this.pos = pos
    message.publish(msg.message, Threading.Ui)
    likeDetails.messageId.publish(msg.message.id, Threading.Ui)
    likeButton.message.publish(msg.message, Threading.Ui)
    updateLikes(msg.likedBySelf, msg.likes)
  }

  override def updateLikes(likedBySelf: Boolean, likes: IndexedSeq[UserId]): Unit = {
    isLiked.publish(likes.nonEmpty, Threading.Ui)
    likeButton.likedBySelf.publish(likedBySelf, Threading.Ui)
    likeDetails.likedBy.publish(likes, Threading.Ui)
  }
}

object FooterPartView {
  private val TIMESTAMP_VISIBILITY: Int = 5000
}

//TODO button animation
class LikeButton(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private lazy val likeButtonColorLiked   = getColor(R.color.accent_red)
  private lazy val likeButtonColorUnliked = getColor(R.color.text__secondary_light)

  inflate(R.layout.message_footer_like_button)

  private val likeButtonConstant: TextView = findById(R.id.like__button_constant)
  private val likeButtonAnimation: TextView = findById(R.id.like__button_animation)

  val controller = inject[FooterController]
  val likedBySelf = Signal[Boolean]()
  val message = Signal[MessageData]()

  val likeClicked = EventStream[Unit]()
  new EventStreamWithAuxSignal[Unit, Boolean](likeClicked, likedBySelf).map { case (_, likedBySelf) => likedBySelf.map(!_) }.collect { case Some(l) => l } { like =>
    verbose(s"setting message to liked:  $like")
    controller.setLiked(like, message)
  }

  likeButtonConstant onClick {
    verbose("Like button clicked")
    likeClicked ! (())
  }

  likedBySelf.on(Threading.Ui)(l => verbose(s"setting liked?: $l"))
  likedBySelf.map { case true => R.string.glyph__liked; case false => R.string.glyph__like }.map(getString).on(Threading.Ui)(likeButtonConstant.setText)
  likedBySelf.map { case true => likeButtonColorLiked; case false => likeButtonColorUnliked }.on(Threading.Ui)(likeButtonConstant.setTextColor)
}

class LikeDetailsLayout(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {

  import FooterController._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_like_details)
  setOrientation(LinearLayout.HORIZONTAL)

  private val description: TextView = findById(R.id.like__description)
  private val chatheadContainer: ViewGroup = findById(R.id.like_chathead_container)
  private val hintArrow: TextView = findById(R.id.like__arrow)
  private val chatheads = Seq.tabulate(chatheadContainer.getChildCount)(chatheadContainer.getChildAt).collect { case v: ChatheadView => v }

  val controller = inject[FooterController]
  val messageId = Signal[MessageId]()
  val likedBy = Signal[IndexedSeq[UserId]]()

  likedBy.map(ids => showAvatars(ids)).on(Threading.Ui)(chatheadContainer.setVisible)

  likedBy.collect { case ids if showAvatars(ids) => ids }.on(Threading.Ui)(_.zip(chatheads).foreach { case (id, view) => view.setUserId(id) })

  val displayText = likedBy flatMap controller.getDisplayNameString

  displayText.collect { case Some(t) => t }.on(Threading.Ui)(description.setText)
  displayText.map { case Some(_) => true; case _ => false }.on(Threading.Ui)(description.setVisible)

  likedBy.on(Threading.Ui)(_ => hintArrow.setVisible(false))

  chatheadContainer.onClick(messageId.currentValue.foreach(inject[ScreenController].showUsersWhoLike))
}


class FooterController(implicit inj: Injector, context: Context) extends Injectable {

  import FooterController._

  private val zms = inject[Signal[ZMessaging]]
  private val reactions = zms.map(_.reactions)

  def isSelfUser(userId: UserId) = zms.currentValue.exists(_.selfUserId == userId)

  reactions.disableAutowiring()

  def getDisplayNameString(ids: Seq[UserId]): Signal[Option[String]] = ids match {
    case ids if showAvatars(ids) => Signal const Some(getQuantityString(R.plurals.message_footer__number_of_likes, ids.size, ids.size.toString))
    case ids if ids.nonEmpty => zms.flatMap(z => Signal.sequence(ids.map(id => z.users.userSignal(id).map(_.getDisplayName)): _*)).map(names => Some(names.mkString(", ")))
    case _ => Signal const None
  }

  def setLiked(like: Boolean, message: Signal[MessageData]) = reactions.currentValue.zip(message.currentValue).foreach {
    case (reacts, msg) => if (like) reacts.like(msg.convId, msg.id) else reacts.unlike(msg.convId, msg.id)
  }
}

object FooterController {
  def showAvatars(ids: Seq[UserId]) = ids.size > 2
}
