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
import android.widget.{FrameLayout, GridLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.model.{MessageContent, MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.{MessageViewFactory, MessageViewPart, MsgPart}
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class MemberChangePartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.MemberChange

  inflate(R.layout.message_member_change_content)

  val zMessaging = inject[Signal[ZMessaging]]

  val messageTextView: TextView = findById(R.id.ttv__row_conversation__people_changed__text)
  val gridView: MembersGridView = findById(R.id.rv__row_conversation__people_changed__grid)
  val iconView: GlyphTextView   = findById(R.id.gtv__row_conversation__people_changed__icon)

  lazy val locale = context.getResources.getConfiguration.locale
  lazy val stringYou = context.getString(R.string.content__system__you)
  lazy val itemSeparator = context.getString(R.string.content__system__item_separator)
  lazy val lastSeparator = context.getString(R.string.content__system__last_item_separator)

  private val message = Signal[MessageData]()

  val iconGlyph = message map { msg =>
    msg.msgType match {
      case Message.Type.MEMBER_JOIN => R.string.glyph__plus
      case _ =>                        R.string.glyph__minus
    }
  }

  val memberIds = message.map(_.members.toSeq.sorted) // we should probably sort members by name instead

  def memberName(id: UserId, zms: ZMessaging) =
    if (zms.selfUserId == id) Signal const stringYou
    else zms.users.userSignal(id).map(_.getDisplayName)

  val membersString = for {
    zms <- zMessaging
    ids <- memberIds
    names <- Signal.sequence(ids.map(memberName(_, zms)): _*)
  } yield
    names match {
      case Seq() => ""
      case Seq(name) => name
      case _ =>
        val n = names.length
        s"${names.take(n - 1).mkString(itemSeparator + " ")} $lastSeparator  ${names.last}"
    }

  val linkText = for {
    zms <- zMessaging
    msg <- message
    ids <- memberIds
    user <- zms.users.userSignal(msg.userId)
    members <- membersString
  } yield {
    import Message.Type._
    val me = zms.selfUserId
    val userId = msg.userId

    (msg.msgType, msg.userId, ids) match {
      case (MEMBER_JOIN, `me`, _)       if msg.firstMessage => context.getString(R.string.content__system__you_started_participant, "", members)
      case (MEMBER_JOIN, _, Seq(`me`))  if msg.firstMessage => context.getString(R.string.content__system__other_started_you, user.getDisplayName)
      case (MEMBER_JOIN, _, _)          if msg.firstMessage => context.getString(R.string.content__system__other_started_participant, user.getDisplayName, members)
      case (MEMBER_JOIN, `me`, _)                           => context.getString(R.string.content__system__you_added_participant, "", members)
      case (MEMBER_JOIN, _, Seq(`me`))                      => context.getString(R.string.content__system__other_added_you, user.getDisplayName)
      case (MEMBER_JOIN, _, _)                              => context.getString(R.string.content__system__other_added_participant, user.getDisplayName, members)
      case (MEMBER_LEAVE, `me`, Seq(`me`))                  => context.getString(R.string.content__system__you_left)
      case (MEMBER_LEAVE, `me`, _)                          => context.getString(R.string.content__system__you_removed_other, "", members)
      case (MEMBER_LEAVE, _, Seq(`me`))                     => context.getString(R.string.content__system__other_removed_you, user.getDisplayName)
      case (MEMBER_LEAVE, _, Seq(`userId`))                 => context.getString(R.string.content__system__other_left, user.getDisplayName)
      case (MEMBER_LEAVE, _, _)                             => context.getString(R.string.content__system__other_removed_other, user.getDisplayName, members)
    }
  }

  iconGlyph { iconView.setText }

  linkText.on(Threading.Ui) { txt =>
    messageTextView.setText(txt.toUpperCase(locale))
    TextViewUtils.boldText(messageTextView)
  }

  memberIds { gridView.members ! _ }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message ! msg
  }
}


class MembersGridView(context: Context, attrs: AttributeSet, style: Int) extends GridLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val cache = inject[MessageViewFactory]

  val columnSpacing = getDimenPx(R.dimen.wire__padding__small)
  val columnWidth = getDimenPx(R.dimen.content__separator__chathead__size)

  val members = Signal[Seq[UserId]]()

  members { ids =>
    verbose(s"user id: $ids")
    if (getChildCount > ids.length) {
      for (i <- ids.length until getChildCount) cache.recycle(getChildAt(i), R.layout.message_member_chathead)
      removeViewsInLayout(ids.length, getChildCount - ids.length)
    }

    ids.zipWithIndex foreach { case (id, index) =>
      val view =
        if (index < getChildCount) getChildAt(index).asInstanceOf[ChatheadView]
        else returning(cache.get[ChatheadView](R.layout.message_member_chathead, this)) { addView }

      view.setUserId(id)
    }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    val width = getMeasuredWidth + columnSpacing
    val itemWidth = columnWidth + columnSpacing
    setColumnCount(math.max(1, width / itemWidth))
  }
}
