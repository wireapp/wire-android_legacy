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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.MarginLayoutParams
import android.widget.{GridLayout, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.model.{MessageContent, UserId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.RichView

class MemberChangePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.MemberChange

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_member_change_content)

  val zMessaging = inject[Signal[ZMessaging]]
  val users      = inject[UsersController]

  val messageView: SystemMessageView  = findById(R.id.smv_header)
  val gridView: MembersGridView       = findById(R.id.people_changed_grid)
  val position = Signal[Int]()

  val iconGlyph = message map { msg =>
    msg.msgType match {
      case Message.Type.MEMBER_JOIN if msg.firstMessage => R.string.glyph__conversation
      case Message.Type.MEMBER_JOIN =>                     R.string.glyph__plus
      case _ =>                                            R.string.glyph__minus
    }
  }


  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]) = {
    super.set(msg, part, opts)
    opts.foreach(position ! _.position)

  }

  val memberNames = users.memberDisplayNames(message, boldNames = true)

  val senderName = message.map(_.userId).flatMap(users.displayName)

  val linkText = for {
    zms         <- zMessaging
    msg         <- message
    displayName <- senderName
    members     <- memberNames
  } yield {
    import Message.Type._
    val me = zms.selfUserId
    val userId = msg.userId

    (msg.msgType, displayName, msg.members.toSeq) match {
      case (MEMBER_JOIN, Me|Other(_), _)         if msg.firstMessage && msg.name.isDefined => getString(R.string.content__system__with_participant, members)
      case (MEMBER_JOIN, Me, _)                  if msg.firstMessage                       => getString(R.string.content__system__you_started_participant, "", members)
      case (MEMBER_JOIN, Other(name), Seq(`me`)) if msg.firstMessage                       => getString(R.string.content__system__other_started_you, name)
      case (MEMBER_JOIN, Other(name), _)         if msg.firstMessage                       => getString(R.string.content__system__other_started_participant, name, members)
      case (MEMBER_JOIN, Me, Seq(`me`)) if userId == me                                    => getString(R.string.content__system__you_joined).toUpperCase
      case (MEMBER_JOIN, Me, _)                                                            => getString(R.string.content__system__you_added_participant, "", members).toUpperCase
      case (MEMBER_JOIN, Other(name), Seq(`me`))                                           => getString(R.string.content__system__other_added_you, name).toUpperCase
      case (MEMBER_JOIN, Other(name), Seq(`userId`))                                       => getString(R.string.content__system__other_joined, name).toUpperCase
      case (MEMBER_JOIN, Other(name), _)                                                   => getString(R.string.content__system__other_added_participant, name, members).toUpperCase
      case (MEMBER_LEAVE, Me, Seq(`me`))                                                   => getString(R.string.content__system__you_left).toUpperCase
      case (MEMBER_LEAVE, Me, _)                                                           => getString(R.string.content__system__you_removed_other, "", members).toUpperCase
      case (MEMBER_LEAVE, Other(name), Seq(`me`))                                          => getString(R.string.content__system__other_removed_you, name).toUpperCase
      case (MEMBER_LEAVE, Other(name), Seq(`userId`))                                      => getString(R.string.content__system__other_left, name).toUpperCase
      case (MEMBER_LEAVE, Other(name), _)                                                  => getString(R.string.content__system__other_removed_other, name, members).toUpperCase
    }
  }

  message.map(m => if (m.firstMessage && m.name.nonEmpty) Some(16) else None)
    .map(_.map(toPx))
    .onUi(_.foreach(this.setMarginTop))

  iconGlyph { messageView.setIconGlyph }

  linkText.on(Threading.Ui) { messageView.setText }

  message.map(_.members.toSeq.sortBy(_.str)) { gridView.users ! _ }

}

class MembersGridView(context: Context, attrs: AttributeSet, style: Int) extends GridLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val cache = inject[MessageViewFactory]
  val chatHeadResId = R.layout.message_member_chathead

  val users = Signal[Seq[UserId]]()

  val columnSpacing = getDimenPx(R.dimen.wire__padding__small)
  val columnWidth = getDimenPx(R.dimen.content__separator__chathead__size)

  val columns = Signal[Int]() //once set, we expect this won't change, even across recycling

  (for {
    cols <- columns
    ids <- users
  } yield (ids, cols)).on(Threading.Ui) {
    case (ids, cols) =>
      val rows = math.ceil(ids.size.toFloat / cols.toFloat).toInt
      verbose(s"Cols or Users changed: users: ${ids.length}, cols: $cols, rows: $rows")

      //recycle and remove all the views - there might be more than the current number of users
      (0 until getChildCount).map(getChildAt).foreach(cache.recycle(_, chatHeadResId))
      removeAllViews()

      setColumnCount(cols)
      setRowCount(rows)

      ids.foreach { id =>
        returning(cache.get[ChatheadView](chatHeadResId, this)) { v =>

          /**
            * We need to reset the GridLayout#LayoutParams, as the GridLayout assigns each view a specific row x column
            * coordinate. If the number of rows/columns shrinks, then any view with coordinates that lie outside the
            * new bounds will cause the view to crash. However, we need to maintain the size and margin info specified
            * in the xml, which should always be instances of MarginLayoutParams, and this does the trick!
            */
          addView(v, new GridLayout.LayoutParams(v.getLayoutParams.asInstanceOf[MarginLayoutParams]))
          v.setUserId(id)
        }
      }
  }


  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    val width = getMeasuredWidth + columnSpacing
    val itemWidth = columnWidth + columnSpacing

    val res = math.max(1, width / itemWidth)
    columns ! res
  }
}
