/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.views

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.{Gravity, View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout}
import android.widget.LinearLayout.LayoutParams
import com.waz.api.IConversation
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.{ColorUtils, TextViewUtils}
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.ContextUtils._
import com.waz.utils._
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.conversationlist.views.row.MenuIndicatorView
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.views.properties.MoveToAnimateable

class NewConversationListRow(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style)
    with ViewHelper
    with SwipeListView.SwipeListRow
    with MoveToAnimateable { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.conversation_list__row__height)))
  inflate(R.layout.new_conv_list_item)

  val zms = inject[Signal[ZMessaging]]
  val accentColor = inject[AccentColorController].accentColor
  val selfId = zms.map(_.selfUserId)

  private val conversationId = Signal[ConvId]()

  val container = ViewUtils.getView(this, R.id.conversation_row_container).asInstanceOf[LinearLayout]
  val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TypefaceTextView]
  val subtitle = ViewUtils.getView(this, R.id.conversation_subtitle).asInstanceOf[TypefaceTextView]
  val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  val statusPill = ViewUtils.getView(this, R.id.conversation_status_pill).asInstanceOf[TypefaceTextView]
  val separator = ViewUtils.getView(this, R.id.conversation_separator).asInstanceOf[View]
  val menuIndicatorView = ViewUtils.getView(this, R.id.conversation_menu_indicator).asInstanceOf[MenuIndicatorView]

  var iConversation: IConversation = null

  val lastMessageInfo = for {
    z <- zms
    self <- selfId
    convId <- conversationId
    lastMessage <- z.messagesStorage.lastMessage(convId)
    user <- lastMessage.fold2[Signal[Option[UserData]]](Signal.const(Option.empty[UserData]), message => z.usersStorage.optSignal(message.userId))
  } yield (lastMessage, user, lastMessage.exists(_.userId == self))

  val conversation = for {
    z <- zms
    convId <- conversationId
    conv <- Signal.future(z.convsStorage.get(convId))
  } yield conv

  val conversationName = conversation.map(_.fold("")(_.displayName))

  val conversationInfo = for {
    z <- zms
    self <- selfId
    Some(conv) <- conversation
    memberIds <- z.membersStorage.activeMembers(conv.id)
    memberSeq <- Signal.future(z.usersStorage.getAll(memberIds))
  } yield (conv.convType, memberSeq.flatten.filter(_.id != self))

  val unreadCount = for {
    z <- zms
    convId <- conversationId
    unreadCount <- z.messagesStorage.unreadCount(convId)
  } yield unreadCount

  val isCurrentConversation = for {
    z <- zms
    convId <- conversationId
    currentConv <- z.convsStats.selectedConversationId
  } yield currentConv.contains(convId)

  lastMessageInfo.on(Threading.Ui) {
    case (Some(message), Some(user), false) if message.contentString.nonEmpty =>
      showSubtitle()
      subtitle.setText(s"[[${user.getDisplayName}:]] ${message.contentString}")
      TextViewUtils.boldText(subtitle)
    case (Some(message), _, _) if message.contentString.nonEmpty =>
      showSubtitle()
      subtitle.setText(s"${message.contentString}")
    case _ =>
      hideSubtitle()
      subtitle.setText("")
  }

  conversationInfo.on(Threading.Background) { convInfo  =>
    avatar.setMembers(convInfo._2.flatMap(_.picture), convInfo._1)
  }

  unreadCount.on(Threading.Ui) { count =>
    statusPill.setText(count.toString)
    if (count > 0) {
      statusPill.setVisibility(View.VISIBLE)
    } else {
      statusPill.setVisibility(View.INVISIBLE)
    }
  }

  conversationName.on(Threading.Ui) { title.setText }

  isCurrentConversation.zip(accentColor).on(Threading.Ui){
    case (true, color) =>
      separator.setBackgroundColor(ColorUtils.injectAlpha(0.5f, color.getColor()))
    case (false, _) =>
      separator.setBackgroundColor(getColor(R.color.white_8))
  }

  private var conversationCallback: ConversationCallback = null
  private var maxAlpha: Float = .0f
  private var openState: Boolean = false
  private val menuOpenOffset: Int = getDimenPx(R.dimen.list__menu_indicator__max_swipe_offset)
  private var moveTo: Float = .0f
  private var maxOffset: Float = .0f
  private var swipeable: Boolean = false
  private var moveToAnimator: ObjectAnimator = null

  private def showSubtitle(): Unit = title.setGravity(Gravity.TOP)

  private def hideSubtitle(): Unit = title.setGravity(Gravity.CENTER_VERTICAL)

  def getConversation: IConversation = iConversation

  def setConversation(iConversation: IConversation): Unit = {
    self.iConversation = iConversation
    if (!conversationId.currentValue.contains(ConvId(iConversation.getId))) {
      title.setText(iConversation.getName)
      subtitle.setText("")
      statusPill.setVisibility(View.INVISIBLE)
      avatar.setConversationType(iConversation.getType)
    }
    conversationId.publish(ConvId(iConversation.getId), Threading.Background)
  }

  def setConversationCallback(conversationCallback: ConversationCallback) {
    this.conversationCallback = conversationCallback
  }

  //TODO: Copied from java version...
  menuIndicatorView.setMaxOffset(menuOpenOffset)
  menuIndicatorView.setOnClickListener(new View.OnClickListener() {
    def onClick(v: View) {
      close()
      conversationCallback.onConversationListRowSwiped(iConversation, self)
    }
  })

  def isArchiveTarget: Boolean = false
  def needsRedraw: Boolean = false
  def redraw(): Unit = {}

  override def open(): Unit =  {
    if (openState) return
    animateMenu(menuOpenOffset)
    openState = true
  }

  def close() {
    if (openState) openState = false
    animateMenu(0)
  }

  private def closeImmediate() {
    if (openState) openState = false
    setMoveTo(0)
  }

  override def setMaxOffset(maxOffset: Float) = self.maxOffset = maxOffset

  override def setOffset(offset: Float) = {
    val openOffset: Int = if (openState) menuOpenOffset
    else 0
    var moveTo: Float = openOffset + offset

    if (moveTo < 0) moveTo = 0

    if (moveTo > maxOffset) {
      val overshoot: Float = moveTo - maxOffset
      moveTo = maxOffset + overshoot / 2
    }

    setMoveTo(moveTo)
  }

  override def isSwipeable = swipeable

  def setSwipeable(swipeable: Boolean) {
    self.swipeable = swipeable
  }

  override def isOpen = openState

  override def swipeAway() = {
    close()
    conversationCallback.onConversationListRowSwiped(iConversation, this)
  }

  override def dimOnListRowMenuSwiped(alpha: Float) = {
    val cappedAlpha = Math.max(alpha, maxAlpha)
    menuIndicatorView.setAlpha(cappedAlpha)
    setAlpha(cappedAlpha)
  }

  override def setPagerOffset(pagerOffset: Float): Unit = {
    if (iConversation == null || iConversation.isSelected)
      return

    val alpha = Math.max(Math.pow(1 - pagerOffset, 4).toFloat, maxAlpha)
    setAlpha(alpha)
  }

  override def getMoveTo = moveTo

  override def setMoveTo(value: Float) = {
    moveTo = value
    container.setPadding(getPaddingLeft, getPaddingTop, moveTo.toInt, getPaddingBottom)
    container.setTranslationX(moveTo)
    menuIndicatorView.setClipX(moveTo.toInt)
  }

  private def animateMenu(moveTo: Int) {
    val moveFrom: Float = getMoveTo
    moveToAnimator = ObjectAnimator.ofFloat(this, MoveToAnimateable.MOVE_TO, moveFrom, moveTo)
    moveToAnimator.setDuration(getResources.getInteger(R.integer.framework_animation_duration_medium))
    moveToAnimator.setInterpolator(new Expo.EaseOut)
    moveToAnimator.start()
  }

  def setMaxAlpha(maxAlpha: Float) {
    this.maxAlpha = maxAlpha
  }
}
