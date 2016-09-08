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
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model.{MessageContent, MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, _}
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.parts.FooterController.showAvatars
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.utils.ContextUtils.{getColor, getQuantityString, _}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

//TODO rules for keeping the like button open
//TODO showing the message status and click handling on it
//TODO click handling on the 'users who liked' list and going to fragment
//TODO recycle chatheads in like details view - see MembersGridView for more
//TODO tracking
class FooterPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_content)

  private val likeButton: LikeButton = findById(R.id.like__button)
  private val messageStatusTextView: TextView = findById(R.id.timestamp_and_status)
  private val likeDetails: LikeDetailsLayout = findById(R.id.like_details)

  override val tpe: MsgPart = MsgPart.Footer

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    likeButton.message.publish(msg, Threading.Ui)
    likeDetails.message.publish(msg, Threading.Ui)
  }

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
  val message = Signal[MessageData]()
  val messagesAndLikes = controller.messagesAndLikes(message)
  val likedBySelf = messagesAndLikes.map(_.likedBySelf)

  val likeClicked = EventStream[Unit]()
  new EventStreamWithAuxSignal[Unit, Boolean](likeClicked, likedBySelf).map { case (_, likedBySelf) => likedBySelf.map(!_) }.collect { case Some(l) => l } { like =>
    verbose(s"setting message to liked: $message.id, $like")
    controller.setLiked(like, message)
  }

  likeButtonConstant onClick {
    verbose("Like button clicked")
    likeClicked ! (())
  }

  likedBySelf.map { case true => R.string.glyph__liked; case false => R.string.glyph__like }.map(getString).on(Threading.Ui)(likeButtonConstant.setText)
  likedBySelf.map { case true => likeButtonColorLiked; case false => likeButtonColorUnliked }.on(Threading.Ui)(likeButtonConstant.setTextColor)
}

class LikeDetailsLayout(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_like_details)
  setOrientation(LinearLayout.HORIZONTAL)

  private val description: TextView = findById(R.id.like__description)
  private val chatheadContainer: ViewGroup = findById(R.id.like_chathead_container)
  private val hintArrow: TextView = findById(R.id.like__arrow)
  private val chatheads = Seq.tabulate(chatheadContainer.getChildCount)(chatheadContainer.getChildAt).collect { case v: ChatheadView => v }

  val controller = inject[FooterController]
  val message = Signal[MessageData]
  val userIds = controller.messagesAndLikes(message).map(_.likes)

  userIds.map(ids => showAvatars(ids)).on(Threading.Ui)(chatheadContainer.setVisible)

  userIds.collect { case ids if showAvatars(ids) => ids }.on(Threading.Ui)(_.zip(chatheads).foreach { case (id, view) => view.setUserId(id) })

  val displayText = userIds flatMap controller.getDisplayNameString

  displayText.collect { case Some(t) => t }.on(Threading.Ui)(description.setText)
  displayText.map { case Some(_) => true; case _ => false }.on(Threading.Ui)(description.setVisible)

  userIds.on(Threading.Ui)(_ => hintArrow.setVisible(false))
}


class FooterController(implicit inj: Injector, context: Context) extends Injectable {
  private val zms = inject[Signal[ZMessaging]]
  private val reactions = zms.map(_.reactions)

  reactions.disableAutowiring()

  def messagesAndLikes(message: Signal[MessageData]) = zms.zip(message).flatMap { case (zms, m) => Signal.future(zms.msgAndLikes.getMessageAndLikes(m.id)).collect { case Some(mL) => mL } }

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
