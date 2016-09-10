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
import android.widget.{GridLayout, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.model.{MessageContent, MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.SyncEngineSignals.DisplayName.{Me, Other}
import com.waz.zclient.messages._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class MemberChangePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.MemberChange

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_member_change_content)

  val zMessaging = inject[Signal[ZMessaging]]
  val signals = inject[SyncEngineSignals]

  val messageView: SystemMessageView  = findById(R.id.smv_header)
  val gridView: MembersGridView       = findById(R.id.rv__row_conversation__people_changed__grid)

  private val message = Signal[MessageData]()

  val iconGlyph = message map { msg =>
    msg.msgType match {
      case Message.Type.MEMBER_JOIN => R.string.glyph__plus
      case _ =>                        R.string.glyph__minus
    }
  }

  val memberNames = signals.memberDisplayNames(message)

  val userName = signals.userDisplayName(message)

  val linkText = for {
    zms <- zMessaging
    msg <- message
    displayName <- userName
    members <- memberNames
  } yield {
    import Message.Type._
    val me = zms.selfUserId
    val userId = msg.userId

    (msg.msgType, displayName, msg.members) match {
      case (MEMBER_JOIN, Me, _)                   if msg.firstMessage => context.getString(R.string.content__system__you_started_participant, "", members)
      case (MEMBER_JOIN, Other(name), Seq(`me`))  if msg.firstMessage => context.getString(R.string.content__system__other_started_you, name)
      case (MEMBER_JOIN, Other(name), _)          if msg.firstMessage => context.getString(R.string.content__system__other_started_participant, name, members)
      case (MEMBER_JOIN, `me`, _)                                     => context.getString(R.string.content__system__you_added_participant, "", members)
      case (MEMBER_JOIN, Other(name), Seq(`me`))                      => context.getString(R.string.content__system__other_added_you, name)
      case (MEMBER_JOIN, Other(name), _)                              => context.getString(R.string.content__system__other_added_participant, name, members)
      case (MEMBER_LEAVE, Me, Seq(`me`))                              => context.getString(R.string.content__system__you_left)
      case (MEMBER_LEAVE, Me, _)                                      => context.getString(R.string.content__system__you_removed_other, "", members)
      case (MEMBER_LEAVE, Other(name), Seq(`me`))                     => context.getString(R.string.content__system__other_removed_you, name)
      case (MEMBER_LEAVE, Other(name), Seq(`userId`))                 => context.getString(R.string.content__system__other_left, name)
      case (MEMBER_LEAVE, Other(name), _)                             => context.getString(R.string.content__system__other_removed_other, name, members)
    }
  }

  iconGlyph { messageView.setIconGlyph }

  linkText.on(Threading.Ui) { messageView.setText }

  message.map(_.members.toSeq.sortBy(_.str)) { gridView.members ! _ }

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
