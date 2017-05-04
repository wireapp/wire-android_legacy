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
import android.widget.LinearLayout.LayoutParams
import android.widget.{FrameLayout, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.{IConversation, Message}
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.core.stores.connect.InboxLinkConversation
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.conversationlist.views.row.MenuIndicatorView
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.ui.views.properties.MoveToAnimateable
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{StringUtils, ViewUtils}
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Future

class NewConversationListRow(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style)
    with ViewHelper
    with SwipeListView.SwipeListRow
    with MoveToAnimateable { self =>
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  implicit val executionContext = Threading.Background

  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.conversation_list__row__height)))
  inflate(R.layout.new_conv_list_item)

  val zms = inject[Signal[ZMessaging]]
  val accentColor = inject[AccentColorController].accentColor
  val selfId = zms.map(_.selfUserId)

  private val conversationId = Signal[Option[ConvId]]()

  val container = ViewUtils.getView(this, R.id.conversation_row_container).asInstanceOf[LinearLayout]
  val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TypefaceTextView]
  val subtitle = ViewUtils.getView(this, R.id.conversation_subtitle).asInstanceOf[TypefaceTextView]
  val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]
  val separator = ViewUtils.getView(this, R.id.conversation_separator).asInstanceOf[View]
  val menuIndicatorView = ViewUtils.getView(this, R.id.conversation_menu_indicator).asInstanceOf[MenuIndicatorView]

  var iConversation: IConversation = null

  val conversation = for {
    z <- zms
    Some(convId) <- conversationId
    conv <- z.convsStorage.signal(convId)
  } yield conv

  val conversationName = for {
    z <- zms
    conv <- conversation
    memberCount <- z.membersStorage.activeMembers(conv.id).map(_.size)
  } yield {
    if (conv.convType == ConversationType.Incoming) {
      getInboxName(memberCount)
    } else {
      conv.displayName
    }
  }

  val userTyping = for {
    z <- zms
    Some(convId) <- conversationId
    typing <- Signal.wrap(z.typing.onTypingChanged.filter(_._1 == convId).map(_._2.headOption)).orElse(Signal.const(None))
    typingUser <- Signal.future(typing.fold(Future.successful(Option.empty[UserData]))(tu => z.usersStorage.get(tu.id)))
  } yield typingUser

  val badgeInfo = for {
    z <- zms
    conv <- conversation
    unreadCount <- z.messagesStorage.unreadCount(conv.id)
    typing <- userTyping.map(_.nonEmpty)
  } yield {
    if (conv.unjoinedCall) {
      ConversationBadge.OngoingCall
    } else if (conv.convType == ConversationType.WaitForConnection || conv.convType == ConversationType.Incoming) {
      ConversationBadge.WaitingConnection
    } else if (conv.muted) {
      ConversationBadge.Muted
    } else if (typing) {
      ConversationBadge.Typing
    } else if (conv.missedCallMessage.nonEmpty) {
      ConversationBadge.MissedCall
    } else if (conv.incomingKnockMessage.nonEmpty) {
      ConversationBadge.Ping
    } else if (unreadCount == 0) {
      ConversationBadge.Empty
    } else if (unreadCount > 0) {
      ConversationBadge.Count(unreadCount)
    } else {
      ConversationBadge.Empty
    }
  }

  val subtitleText = for {
    z <- zms
    self <- selfId
    conv <- conversation
    lastReadInstant <- z.messagesStorage.lastRead(conv.id)
    unread <- z.messagesStorage.unreadCount(conv.id)
    lastMessages <- Signal.future(z.messagesStorage.findMessagesFrom(conv.id, lastReadInstant.plusMillis(1)).map(_.filter(_.userId != self)))
    lastMessageUser <- lastMessages.lastOption.fold2(Signal.const(Option.empty[UserData]), message => z.usersStorage.optSignal(message.userId))
    lastMessageMembers <- lastMessages.lastOption.fold2(Signal.const(Vector[UserData]()), message => z.usersStorage.listSignal(message.members))
    typingUser <- userTyping
  } yield {
    if (conv.muted || conv.incomingKnockMessage.nonEmpty || conv.missedCallMessage.nonEmpty) {
      subtitleStringForMessages(lastMessages)
    } else {
      typingUser.fold {
        lastMessages.headOption.fold {
          ""
        } { msg =>
          subtitleStringForMessage(msg, lastMessageUser, lastMessageMembers, conv.convType == ConversationType.Group)
        }
      } { usr =>
        formatSubtitle(getString(R.string.conversation_list__typing), usr.getDisplayName, conv.convType == ConversationType.Group)
      }
    }
  }

  val avatarInfo = for {
    z <- zms
    self <- selfId
    conv <- conversation
    memberIds <- z.membersStorage.activeMembers(conv.id)
    memberSeq <- Signal.future(z.usersStorage.getAll(memberIds))
  } yield (conv.convType, memberSeq.flatten.filter(_.id != self))

  def subtitleStringForMessage(messageData: MessageData, user: Option[UserData], members: Vector[UserData], isGroup: Boolean): String = {
    lazy val senderName = user.fold("")(_.getDisplayName)
    lazy val memberName = members.headOption.fold2(getString(R.string.conversation_list__someone), _.getDisplayName)

    if (messageData.isEphemeral) {
      return formatSubtitle(getString(R.string.conversation_list__ephemeral), senderName, isGroup)
    }
    messageData.msgType match {
      case Message.Type.TEXT | Message.Type.TEXT_EMOJI_ONLY | Message.Type.RICH_MEDIA =>
        formatSubtitle(messageData.contentString, senderName, isGroup)
      case Message.Type.ASSET =>
        formatSubtitle(getString(R.string.conversation_list__shared__image), senderName, isGroup)
      case Message.Type.ANY_ASSET =>
        formatSubtitle(getString(R.string.conversation_list__shared__file), senderName, isGroup)
      case Message.Type.VIDEO_ASSET =>
        formatSubtitle(getString(R.string.conversation_list__shared__video), senderName, isGroup)
      case Message.Type.AUDIO_ASSET =>
        formatSubtitle(getString(R.string.conversation_list__shared__audio), senderName, isGroup)
      case Message.Type.LOCATION =>
        formatSubtitle(getString(R.string.conversation_list__shared__location), senderName, isGroup)
      case Message.Type.MISSED_CALL =>
        formatSubtitle(getString(R.string.conversation_list__missed_call), senderName, isGroup)
      case Message.Type.KNOCK =>
        formatSubtitle(getString(R.string.conversation_list__pinged), senderName, isGroup)
      case Message.Type.MEMBER_JOIN =>
        getString(R.string.conversation_list__added, memberName)
      case Message.Type. MEMBER_LEAVE if user.forall(u => members.contains(u)) =>
        getString(R.string.conversation_list__left, memberName)
      case Message.Type. MEMBER_LEAVE =>
        getString(R.string.conversation_list__removed, memberName)
      case Message.Type.CONNECT_ACCEPTED =>
        members.headOption.flatMap(_.handle).map(_.string).getOrElse("")
      case _ =>
        ""
    }
  }

  def formatSubtitle(content: String, user: String, group: Boolean): String = {
    val groupSubtitle =  "[[%s]]: %s"
    val singleSubtitle =  "%s"
    if (group) {
      String.format(groupSubtitle, user, content)
    } else {
      String.format(singleSubtitle, content)
    }
  }

  def subtitleStringForMessages(messages: Iterable[MessageData]): String = {
    val normalMessageCount = messages.count(m => !m.isSystemMessage && m.msgType != Message.Type.KNOCK)
    val missedCallCount = messages.count(_.msgType == Message.Type.MISSED_CALL)
    val pingCount = messages.count(_.msgType == Message.Type.KNOCK)
    val likesCount = 0//TODO: how to get this?
    val unsentCount = messages.count(_.state == Message.Status.FAILED)

    val unsentString =
      if (unsentCount > 0)
        if (normalMessageCount + missedCallCount + pingCount + likesCount == 0)
          getString(R.string.conversation_list__unsent_message_long)
        else
          getString(R.string.conversation_list__unsent_message_short)
      else
        ""
    val strings = Seq(
      if (normalMessageCount > 0)
        getResources.getQuantityString(R.plurals.conversation_list__new_message_count, normalMessageCount, normalMessageCount.toString) else "",
      if (missedCallCount > 0)
        getResources.getQuantityString(R.plurals.conversation_list__missed_calls_count, missedCallCount, missedCallCount.toString) else "",
      if (pingCount > 0)
        getResources.getQuantityString(R.plurals.conversation_list__pings_count, pingCount, pingCount.toString) else "",
      if (likesCount > 0)
        getResources.getQuantityString(R.plurals.conversation_list__new_likes_count, likesCount, likesCount.toString) else ""
    ).filter(_.nonEmpty)
    Seq(unsentString, strings.mkString(", ")).filter(_.nonEmpty).mkString(" | ")
  }

  conversationName.on(Threading.Ui) { title.setText }

  subtitleText.on(Threading.Ui) {
    case text if text.nonEmpty =>
      showSubtitle()
      subtitle.setText(text)
      TextViewUtils.boldText(subtitle)
    case _ =>
      hideSubtitle()
      subtitle.setText("")
  }

  badgeInfo.on(Threading.Ui) { badge.setStatus }

  avatarInfo.on(Threading.Background) { convInfo  =>
    avatar.setMembers(convInfo._2.map(_.id), convInfo._1)
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
    if (!conversationId.currentValue.contains(Some(ConvId(iConversation.getId)))) {
      iConversation match {
        case conv: InboxLinkConversation =>
          title.setText(getInboxName(conv.getSize))
          badge.setStatus(ConversationBadge.WaitingConnection)
          conversationId.publish(None, Threading.Background)
        case _ =>
          title.setText(iConversation.getName)
          badge.setStatus(ConversationBadge.Empty)
          conversationId.publish(Some(ConvId(iConversation.getId)), Threading.Background)
      }
      subtitle.setText("")
      avatar.setConversationType(iConversation.getType)
      closeImmediate()
    }
  }

  private def getInboxName(convSize: Int): String = getResources.getQuantityString(R.plurals.connect_inbox__link__name, convSize)

  menuIndicatorView.setClickable(false)
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

  def setConversationCallback(conversationCallback: ConversationCallback) {
    this.conversationCallback = conversationCallback
  }

  override def open(): Unit =  {
    if (openState) return
    animateMenu(menuOpenOffset)
    menuIndicatorView.setClickable(true)
    openState = true
  }

  def close() {
    if (openState) openState = false
    menuIndicatorView.setClickable(false)
    animateMenu(0)
  }

  private def closeImmediate() {
    if (openState) openState = false
    menuIndicatorView.setClickable(false)
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
