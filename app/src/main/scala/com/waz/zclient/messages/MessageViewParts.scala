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
import android.graphics.{Canvas, Paint}
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, RelativeLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.utils.ContextUtils.{getColor, getDimenPx}
import com.waz.zclient.{R, ViewHelper}

class SeparatorView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with TimeSeparator {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Separator
}

class SeparatorViewLarge(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with TimeSeparator {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.SeparatorLarge

  if (ThemeUtils.isDarkTheme(context)) setBackgroundColor(getColor(R.color.white_8))
  else setBackgroundColor(getColor(R.color.black_4))

}

class UnreadDot(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val accent = inject[AccentColorController].accentColor
  val show = Signal[Boolean](false)

  val dotRadius = getDimenPx(R.dimen.conversation__unread_dot__radius)
  val dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  accent { color =>
    dotPaint.setColor(color.getColor())
    invalidate()
  }

  show.onChanged.on(Threading.Ui)(_ => invalidate())

  override def onDraw(canvas: Canvas): Unit = if (show.currentValue.getOrElse(false)) canvas.drawCircle(getWidth / 2, getHeight / 2, dotRadius, dotPaint)
}

// TODO: replace LinearLayout with TextView, set chathead as compound drawable
class UserView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.User

  inflate(R.layout.message_user_content)

  private val chathead: ChatheadView = findById(R.id.chathead)
  private val tvName: TypefaceTextView = findById(R.id.tvName)

  private val zms = inject[Signal[ZMessaging]]
  private val userId = Signal[UserId]()

  private val user = Signal(zms, userId).flatMap {
    case (z, id) => z.usersStorage.signal(id)
  }

  userId(chathead.setUserId)

  user.map(_.getDisplayName).on(Threading.Ui)(tvName.setTransformedText)

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = userId ! msg.userId
}

class EmptyPartView(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with MessageViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Empty

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = ()
}
