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
package com.waz.zclient.messages

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.widget.{ImageView, LinearLayout}
import com.waz.model.{MessageContent, MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ui.text.{LinkTextView, TypefaceTextView}
import com.waz.zclient.utils.{DateConvertUtils, ZTimeFormatter}
import com.waz.zclient.views.chathead.ChatheadView
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.{Instant, LocalDateTime, ZoneId}

class SeparatorView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Separator
  val is24HourFormat = DateFormat.is24HourFormat(context)

  private var pos = -1

  val time = Signal[Instant]()
  val text = time map { t =>
    ZTimeFormatter.getSeparatorTime(context.getResources, LocalDateTime.now, DateConvertUtils.asLocalDateTime(t), is24HourFormat, ZoneId.systemDefault, true)
  }

  lazy val tvTime: TypefaceTextView = findById(R.id.ttv__row_conversation__separator__time)
  lazy val ivUnreadDot: ImageView = findById(R.id.iv__row_conversation__unread_dot)

  text { t =>
    tvTime.setTransformedText(s"$pos: $t")
  }

  // TODO: unread dot size and visibility

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit = {
    this.pos = pos
    time ! msg.time
  }
}

class SeparatorViewLarge(context: Context, attrs: AttributeSet, style: Int) extends TypefaceTextView(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private var pos = -1

  override val tpe: MsgPart = MsgPart.SeparatorLarge
  val is24HourFormat = DateFormat.is24HourFormat(context)

  val time = Signal[Instant]()
  val text = time map { t =>
    ZTimeFormatter.getSeparatorTime(context.getResources, LocalDateTime.now, DateConvertUtils.asLocalDateTime(t), is24HourFormat, ZoneId.systemDefault, true)
  }

  text.on(Threading.Ui) { t =>
    setTransformedText(s"$pos: $t")
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit = {
    this.pos = pos
    time ! msg.time
  }
}

// TODO: replace LinearLayout with TextView, set chathead as compound drawable
class UserView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.User

  inflate(R.layout.message_user_content)

  private val chathead: ChatheadView = findById(R.id.chathead)
  private val tvName: TypefaceTextView = findById(R.id.tvName)

  private var pos = -1

  private val zms = inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
  private val userId = Signal[UserId]()

  private val user = Signal(zms, userId).flatMap {
    case (z, id) => z.usersStorage.signal(id)
  }

  userId(chathead.setUserId)

  user.map(_.getDisplayName).on(Threading.Ui) { n =>
    tvName.setTransformedText(s"$pos: $n")
  }

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit = {
    this.pos = pos
    userId ! msg.userId
  }
}

class TimestampView(context: Context, attrs: AttributeSet, style: Int) extends TypefaceTextView(context, attrs, style) with MessageViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Timestamp

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit =
    setText(s"$pos: ${msg.time.toString}") // TODO: formatting
}

class TextPartView(context: Context, attrs: AttributeSet, style: Int) extends LinkTextView(context, attrs, style) with MessageViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Text

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit =
    setTransformedText(s"$pos: ${part.fold(msg.contentString)(_.content)}")
}

class ImagePartView(context: Context, attrs: AttributeSet, style: Int) extends ImageView(context, attrs, style) with MessageViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent]): Unit = {
    // TODO image loading
  }
}
