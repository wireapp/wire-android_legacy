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
import android.view.{View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.model.{MessageContent, MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.utils.ContextUtils.{getColor, getQuantityString}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class FooterPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Footer

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = message ! msg

  inflate(R.layout.message_footer_content)

  val zms = inject[Signal[ZMessaging]]

  private val message = Signal[MessageData]()

  val messageAndLikes = zms.zip(message).flatMap { case (zms, m) => Signal.future(zms.msgAndLikes.getMessageAndLikes(m.id)).collect { case Some(mL) => mL } }

  private lazy val likeButtonColorLiked   = getColor(R.color.accent_red)
  private lazy val likeButtonColorUnliked = getColor(R.color.text__secondary_light)

  private val likeButton:             TextView          = findById(R.id.gtv__footer__like__button)
  private val likeButtonAnimation:    TextView          = findById(R.id.gtv__footer__like__button_animation)
  private val messageStatusTextView:  TextView          = findById(R.id.tv__footer__message_status)
  private val likeDetails:            LikeDetailsLayout = findById(R.id.fldl_like_details)

  likeButtonAnimation.setVisibility(View.GONE)

  messageAndLikes.map(_.likes).on(Threading.Ui) (likeDetails.setUserIds)

}

protected class LikeDetailsLayout(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper {
  import LikeDetailsLayout._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val zms = inject[Signal[ZMessaging]]

  inflate(R.layout.message_footer_like_details)
  setOrientation(LinearLayout.HORIZONTAL)

  private val description:        TextView    = findById(R.id.tv__footer__like__description)
  private val chatheadContainer:  ViewGroup   = findById(R.id.ll__like_chathead_container)
  private val hintArrow:          TextView    = findById(R.id.gtv__footer__like__arrow)
  private val chatheads = Seq.tabulate(chatheadContainer.getChildCount)(getChildAt).collect { case v: ChatheadView => v }

  val userIds = Signal[Seq[UserId]](Seq.empty)

  userIds.map(ids => ids.size >= NUM_LIKE_USERS_TO_SHOW_AVATARS).on(Threading.Ui)(vis => Seq(chatheadContainer).foreach(_.setVisible(vis)))

  userIds.collect { case ids if ids.size >= NUM_LIKE_USERS_TO_SHOW_AVATARS => ids }.on(Threading.Ui)(_.zip(chatheads).foreach { case (id, view) => view.setUserId(id) })

  val displayText: Signal[Option[String]] = zms.zip(userIds).flatMap {
    case (_, ids)   if ids.size > NUM_LIKE_USERS_TO_SHOW_AVATARS  => Signal const Some(getQuantityString(R.plurals.message_footer__number_of_likes, ids.size, ids.size.toString))
    case (zms, ids) if ids.nonEmpty                               => (Signal future zms.usersStorage.getAll(ids)).map(_.collect { case Some(data) => data.displayName }).map(names => Some(names.mkString(", ")))
    case _                                                        => Signal const None
  }

  displayText.collect { case Some(t) => t }.on(Threading.Ui)(description.setText)
  displayText.map { case Some(_) => true; case _ => false }.on(Threading.Ui)(description.setVisible)


  def setUserIds(userIds: Seq[UserId]): Unit = {
    hintArrow.setVisible(false)
    this.userIds ! userIds
  }
}

object LikeDetailsLayout {
  private val NUM_LIKE_USERS_TO_SHOW_AVATARS: Int = 3
}
