/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.{Canvas, Color, Paint}
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, RelativeLayout}
import com.waz.api.Message
import com.waz.content.MessagesStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.wire.signals.{EventStream, Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.paintcode.ManageServicesIcon
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils.ContextUtils.{getColor, getDimenPx}
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._

import scala.concurrent.Future

trait MessageViewPart extends ViewHelper {
  def tpe: MsgPart

  // `messageAndLikes` keeps a copy of the original message - no further updates to the message data
  protected val messageAndLikes: SourceSignal[MessageAndLikes] = Signal[MessageAndLikes]()

  // `message` reacts to changes to the message data
  protected val message: Signal[MessageData] = for {
    storage <- inject[Signal[MessagesStorage]]
    mAL     <- messageAndLikes
    msg     <- storage.signal(mAL.message.id)
  } yield msg
  message.disableAutowiring()

  final def set(msg: MessageAndLikes, part: Option[MessageContent], opts: MsgBindOptions): Unit =
    set(msg, part, Some(opts))

  //super must be called!!
  def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions] = None): Unit =
    messageAndLikes.publish(msg, Threading.Ui)

  //By default disable clicks for all view types. There are fewer that need click functionality than those that don't
  this.onClick {}
  this.onLongClick(false)
}

/**
  * Marker for views that should pass up the click event either when clicked/double clicked OR when long clicked
  * This prevents some of the more distant parts of a single message view (like the timestamp or chathead view) from
  * passing up the click event, which can feel a bit confusing.
  *
  * Check the message view as well - it has further filtering on which views
  */
trait ClickableViewPart extends MessageViewPart with ViewHelper with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Ui

  val onClicked = EventStream[Unit]()

  def onSingleClick(): Unit = {
    onClicked ! ({})
    Option(getParent.asInstanceOf[View]).foreach(_.performClick())
  }

  def onDoubleClick(): Future[Unit] = messageAndLikes.head.map { mAndL =>
    if (MessageView.clickableTypes.contains(mAndL.message.msgType)) {
      inject[LikesController].onViewDoubleClicked ! mAndL
      Option(getParent.asInstanceOf[View]).foreach(_.performClick()) //perform click to change focus
    }
  }

  this.onClick(onSingleClick, onDoubleClick)

  this.onLongClick(getParent.asInstanceOf[View].performLongClick())
}

trait HighlightViewPart extends MessageViewPart with ViewHelper {
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val collectionController = inject[CollectionController]
  private val animAlpha = Signal(0f)

  private val animator = ValueAnimator.ofFloat(1, 0).setDuration(1500)

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit =
      animAlpha ! Math.min(animation.getAnimatedValue.asInstanceOf[Float], 0.5f)
  })

  private val bgColor = for {
    accent <- accentColorController.accentColor
    alpha  <- animAlpha
  } yield
    if (alpha <= 0) Color.TRANSPARENT
    else ColorUtils.injectAlpha(alpha, accent.color)

  private val isHighlighted = for {
    msg           <- message
    Some(focused) <- collectionController.focusedItem
  } yield focused.id == msg.id

  bgColor.on(Threading.Ui) { setBackgroundColor }

  isHighlighted.on(Threading.Ui) {
    case true  => animator.start()
    case false => animator.end()
  }

  def stopHighlight(): Unit = animator.end()
}

// Marker for view parts that should be laid out as in FrameLayout (instead of LinearLayout)
trait FrameLayoutPart extends MessageViewPart

trait TimeSeparator extends MessageViewPart with ViewHelper {

  lazy val timeText: TypefaceTextView = findById(R.id.separator__time)
  lazy val unreadDot: UnreadDot = findById(R.id.unread_dot)

  val time = Signal[RemoteInstant]()
  val text = time.map(_.instant).map(TimeStamp(_).string)

  text.on(Threading.Ui)(timeText.setTransformedText)

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    this.time ! msg.message.time
    opts.foreach(unreadDot.show ! _.isFirstUnread)
  }
}

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

class UnreadDot(context: Context, attrs: AttributeSet, style: Int)
  extends View(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val accent = inject[AccentColorController].accentColor
  val show = Signal[Boolean](false)

  val dotRadius = getDimenPx(R.dimen.conversation__unread_dot__radius)
  val dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  accent.onUi { color =>
    dotPaint.setColor(color.color)
    postInvalidate()
  }

  show.onChanged.on(Threading.Ui)(_ => invalidate())

  override def onDraw(canvas: Canvas): Unit = if (show.currentValue.getOrElse(false)) canvas.drawCircle(getWidth / 2, getHeight / 2, dotRadius, dotPaint)
}

class UserPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.User

  inflate(R.layout.message_user_content)

  private val chathead: ChatHeadView = findById(R.id.chathead)
  private val tvName: TypefaceTextView = findById(R.id.tvName)
  private val isBot: View = returning(findById[View](R.id.is_bot))(_.setBackground(ManageServicesIcon(ResColor.fromId(R.color.light_graphite))))
  private val tvStateGlyph: GlyphTextView = findById(R.id.gtvStateGlyph)

  private val zms = inject[Signal[ZMessaging]]
  private val userId = Signal[UserId]()

  private val user = Signal.zip(zms, userId).flatMap {
    case (z, id) => z.usersStorage.signal(id)
  }

  private val stateGlyph = message map {
    case m if m.msgType == Message.Type.RECALLED => Some(R.string.glyph__trash)
    case m if m.isEdited => Some(R.string.glyph__edit)
    case _ => None
  }

  userId.onUi(chathead.loadUser)

  user.map(_.name).onUi(tvName.setTransformedText(_))
  user.map(_.isWireBot).on(Threading.Ui) { isBot.setVisible }

  user.map(_.accent).on(Threading.Ui) { a =>
    tvName.setTextColor(getNameColor(a))
  }

  stateGlyph.map(_.isDefined).onUi { tvStateGlyph.setVisible }

  stateGlyph.collect { case Some(glyph) => glyph }.onUi { tvStateGlyph.setText }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
    userId ! msg.message.userId
  }

  def getNameColor(accent: Int): Int = {
    val alpha = if (ThemeUtils.isDarkTheme(context)) accent match {
      case 1 => 0.8f
      case 2 => 0.72f
      case 4 => 0.72f
      case 5 => 0.8f
      case 6 => 0.8f
      case 7 => 1f
      case _ => 1f
    } else accent match {
      case 1 => 0.8f
      case 2 => 0.72f
      case 4 => 0.56f
      case 5 => 0.80f
      case 6 => 0.80f
      case 7 => 0.64f
      case _ => 1f
    }
    ColorUtils.injectAlpha(alpha, AccentColor(accent).color)
  }
}

class EmptyPartView(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with MessageViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Empty
}

class EphemeralDotsView(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with ViewHelper with FrameLayoutPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.EphemeralDots

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions]): Unit = {
    super.set(msg, part, opts)
  }
}
