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
package com.waz.zclient.views.conversationlist

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.{Gravity, View, ViewGroup}
import android.widget.LinearLayout.LayoutParams
import android.widget.{FrameLayout, LinearLayout}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.call.CallInfo
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.CallPermissionsController
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.conversationlist.views.row.MenuIndicatorView
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.ui.views.properties.MoveToAnimateable
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ConversationMembersSignal, ConversationSignal, StringUtils, UiStorage, UserSetSignal, UserSignal, ViewUtils}
import com.waz.zclient.views.ConversationBadge
import com.waz.zclient.views.conversationlist.ConversationListRow._
import com.waz.zclient.{R, ViewHelper}

import scala.collection.Set

trait ConversationListRow extends FrameLayout

class NormalConversationListRow(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style)
    with ConversationListRow
    with ViewHelper
    with SwipeListView.SwipeListRow
    with MoveToAnimateable {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  implicit val executionContext = Threading.Background
  implicit val uiStorage = inject[UiStorage]

  inflate(R.layout.conv_list_item)

  val zms = inject[Signal[ZMessaging]]
  val accentColor = inject[AccentColorController].accentColor
  val callPermissionsController = inject[CallPermissionsController]
  val selfId = zms.map(_.selfUserId)

  private val conversationId = Signal[Option[ConvId]]()

  val container = ViewUtils.getView(this, R.id.conversation_row_container).asInstanceOf[LinearLayout]
  val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TypefaceTextView]
  val subtitle = ViewUtils.getView(this, R.id.conversation_subtitle).asInstanceOf[TypefaceTextView]
  val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]
  val separator = ViewUtils.getView(this, R.id.conversation_separator).asInstanceOf[View]
  val menuIndicatorView = ViewUtils.getView(this, R.id.conversation_menu_indicator).asInstanceOf[MenuIndicatorView]

  var conversationData = Option.empty[ConversationData]
  val conversation = for {
    Some(convId) <- conversationId
    conv <- ConversationSignal(convId)
  } yield conv

  val conversationName = for {
    z <- zms
    self <- selfId
    conv <- conversation
    memberCount <- ConversationMembersSignal(conv.id).map(_.count(_ != self))
  } yield {
    if (conv.convType == ConversationType.Incoming) {
      (conv.id, getInboxName(memberCount))
    } else {
      if (conv.displayName == "") {
        // This hack was in the UiModule Conversation implementation
        // XXX: this is a hack for some random errors, sometimes conv has empty name which is never updated
        zms.head.map {_.conversations.forceNameUpdate(conv.id)}
      }
      (conv.id, conv.displayName)
    }
  }

  val userTyping = for {
    z <- zms
    convId <- conversation.map(_.id)
    typing <- Signal.wrap(z.typing.onTypingChanged.filter(_._1 == convId).map(_._2.headOption)).orElse(Signal.const(None))
    typingUser <- typing.fold2(Signal(Option.empty[UserData]), tu => UserSignal(tu.id).map(Option(_)))
  } yield typingUser

  val badgeInfo = for {
    z <- zms
    conv <- conversation
    typing <- userTyping.map(_.nonEmpty)
    availableCalls <- z.calling.availableCalls
  } yield (conv.id, badgeStatusForConversation(conv, conv.unreadCount, typing, availableCalls))

  val subtitleText = for {
    z <- zms
    self <- selfId
    conv <- conversation
    _ <- if (conv.convType == ConversationType.Incoming) Signal.empty else Signal.const(())
    lastReadInstant <- z.messagesStorage.lastRead(conv.id)
    lastUnreadMessages <- Signal.future(z.messagesStorage.findMessagesFrom(conv.id, lastReadInstant.plusMillis(1)).map(_.filter(_.userId != self)))
    lastUnreadMessageUser <- lastUnreadMessages.lastOption.fold2(Signal.const(Option.empty[UserData]), message => UserSignal(message.userId).map(Some(_)))
    lastUnreadMessageMembers <- lastUnreadMessages.lastOption.fold2(Signal.const(Vector[UserData]()), message => UserSetSignal(message.members).map(_.toVector))
    typingUser <- userTyping
    members <- ConversationMembersSignal(conv.id)
    otherUser <- members.find(_ != self).fold2(Signal.const(Option.empty[UserData]), uid => UserSignal(uid).map(Some(_)))
    lastMessage <- z.messagesStorage.lastMessage(conv.id)
  } yield (conv.id, subtitleStringForLastMessages(conv, otherUser, members, lastUnreadMessages, lastMessage, lastUnreadMessageUser, lastUnreadMessageMembers, typingUser, self))

  val avatarInfo = for {
    z <- zms
    conv <- conversation
    memberIds <- ConversationMembersSignal(conv.id)
    memberSeq <- Signal.sequence(memberIds.map(uid => UserSignal(uid)).toSeq:_*)
  } yield {
    val opacity =
      if ((memberIds.isEmpty && conv.convType == ConversationType.Group) || conv.convType == ConversationType.WaitForConnection || !conv.isActive)
        getResourceFloat(R.dimen.conversation_avatar_alpha_inactive)
      else
        getResourceFloat(R.dimen.conversation_avatar_alpha_active)
    (conv.id, conv.convType, memberSeq.filter(_.id != z.selfUserId), opacity)
  }

  def setSubtitle(text: String): Unit = {
    if (text.nonEmpty) {
      showSubtitle()
      subtitle.setText(text)
      TextViewUtils.boldText(subtitle)
    } else {
      hideSubtitle()
      subtitle.setText("")
    }
  }

  conversationName.on(Threading.Ui) {
    case (convId, text) if conversationData.forall(_.id == convId) =>
      title.setText(text)
    case _ =>
      ZLog.debug("Outdated conversation name")
  }

  subtitleText.on(Threading.Ui) {
    case (convId, text) if conversationData.forall(_.id == convId) =>
      setSubtitle(text)
    case _ =>
      ZLog.debug("Outdated conversation subtitle")
    }

  badgeInfo.on(Threading.Ui) {
    case (convId, status) if conversationData.forall(_.id == convId) =>
      badge.setStatus(status)
    case _ =>
      ZLog.debug("Outdated badge status")
  }

  avatarInfo.on(Threading.Background){
    case (convId, convType, members, alpha) if conversationData.forall(_.id == convId) =>
      val cType =
      if (convType == ConversationType.Group && members.size == 1 && conversationData.exists(_.team.nonEmpty))
        ConversationType.OneToOne
      else
        convType
      avatar.setMembers(members.map(_.id), convId, cType)
      avatar.setAlpha(alpha)
    case _ =>
      ZLog.debug("Outdated avatar info")
  }
  avatarInfo.on(Threading.Ui){
    case (convId, convType, members, alpha) if conversationData.forall(_.id == convId) =>
      if (convType == ConversationType.Group && members.size == 1 && conversationData.exists(_.team.nonEmpty)) {
        avatar.setConversationType(ConversationType.OneToOne)
      }
    case _ =>
      ZLog.debug("Outdated avatar info")
  }

  badge.onClickEvent{
    case ConversationBadge.IncomingCall =>
      conversationData.map(_.id).foreach( convId => callPermissionsController.startCall(convId))
    case _=>
  }

  private var conversationCallback: ConversationCallback = null
  private var maxAlpha: Float = .0f
  private var openState: Boolean = false
  private val menuOpenOffset: Int = getDimenPx(R.dimen.list__menu_indicator__max_swipe_offset)
  private var moveTo: Float = .0f
  private var maxOffset: Float = .0f
  private var swipeable: Boolean = true
  private var moveToAnimator: ObjectAnimator = null
  private var shouldRedraw = false

  private def showSubtitle(): Unit = title.setGravity(Gravity.TOP)

  private def hideSubtitle(): Unit = title.setGravity(Gravity.CENTER_VERTICAL)

  def setConversation(conversationData: ConversationData): Unit = {
    if (this.conversationData.forall(_.id != conversationData.id)) {
      this.conversationData = Some(conversationData)
      title.setText(conversationData.displayName)
      badge.setStatus(ConversationBadge.Empty)
      subtitle.setText("")
      avatar.setConversationType(conversationData.convType)
      avatar.clearImages()
      avatar.setAlpha(getResourceFloat(R.dimen.conversation_avatar_alpha_active))
      conversationId.publish(Some(conversationData.id), Threading.Background)
      closeImmediate()
    }
  }

  private def getInboxName(convSize: Int): String = getResources.getQuantityString(R.plurals.connect_inbox__link__name, convSize, convSize.toString)

  menuIndicatorView.setClickable(false)
  menuIndicatorView.setMaxOffset(menuOpenOffset)
  menuIndicatorView.setOnClickListener(new View.OnClickListener() {
    def onClick(v: View): Unit = {
      close()
      conversationCallback.onConversationListRowSwiped(null, NormalConversationListRow.this)
    }
  })

  def setConversationCallback(conversationCallback: ConversationCallback): Unit = {
    this.conversationCallback = conversationCallback
  }

  override def open(): Unit =  {
    if (openState) return
    animateMenu(menuOpenOffset)
    menuIndicatorView.setClickable(true)
    openState = true
  }

  def close(): Unit = {
    if (openState) openState = false
    menuIndicatorView.setClickable(false)
    animateMenu(0)
  }

  private def closeImmediate(): Unit = {
    if (openState) openState = false
    menuIndicatorView.setClickable(false)
    setMoveTo(0)
  }

  override def setMaxOffset(maxOffset: Float) = this.maxOffset = maxOffset

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

  override def isSwipeable = true

  override def isOpen = openState

  override def swipeAway() = {
    close()
    conversationCallback.onConversationListRowSwiped(null, this)
  }

  override def dimOnListRowMenuSwiped(alpha: Float) = {
    val cappedAlpha = Math.max(alpha, maxAlpha)
    menuIndicatorView.setAlpha(cappedAlpha)
    setAlpha(cappedAlpha)
  }

  override def setPagerOffset(pagerOffset: Float): Unit = {

    val alpha = Math.max(Math.pow(1 - pagerOffset, 4).toFloat, maxAlpha)
    setAlpha(alpha)
  }

  override def getMoveTo = moveTo

  override def setMoveTo(value: Float) = {
    moveTo = value
    container.setTranslationX(moveTo)
    menuIndicatorView.setClipX(moveTo.toInt)
  }

  private def animateMenu(moveTo: Int): Unit = {
    val moveFrom: Float = getMoveTo
    moveToAnimator = ObjectAnimator.ofFloat(this, MoveToAnimateable.MOVE_TO, moveFrom, moveTo)
    moveToAnimator.setDuration(getResources.getInteger(R.integer.framework_animation_duration_medium))
    moveToAnimator.setInterpolator(new Expo.EaseOut)
    moveToAnimator.start()
  }

  def setMaxAlpha(maxAlpha: Float): Unit = {
    this.maxAlpha = maxAlpha
  }


}

object ConversationListRow {

  def formatSubtitle(content: String, user: String, group: Boolean): String = {
    val groupSubtitle =  "[[%s]]: %s"
    val singleSubtitle =  "%s"
    if (group) {
      String.format(groupSubtitle, user, content)
    } else {
      String.format(singleSubtitle, content)
    }
  }

  def badgeStatusForConversation(conversationData: ConversationData,
                                 unreadCount:      Int,
                                 typing:           Boolean,
                                 availableCalls:   Map[ConvId, CallInfo]): ConversationBadge.Status = {

    if (availableCalls.contains(conversationData.id)) {
      ConversationBadge.IncomingCall
    } else if (conversationData.convType == ConversationType.WaitForConnection || conversationData.convType == ConversationType.Incoming) {
      ConversationBadge.WaitingConnection
    } else if (conversationData.muted) {
      ConversationBadge.Muted
    } else if (typing) {
      ConversationBadge.Typing
    } else if (conversationData.incomingKnockMessage.nonEmpty) {
      ConversationBadge.Ping
    } else if (conversationData.missedCallMessage.nonEmpty) {
      ConversationBadge.MissedCall
    } else if (unreadCount == 0) {
      ConversationBadge.Empty
    } else if (unreadCount > 0) {
      ConversationBadge.Count(unreadCount)
    } else {
      ConversationBadge.Empty
    }
  }

  def subtitleStringForLastMessage(messageData: MessageData,
                                   user:        Option[UserData],
                                   members:     Vector[UserData],
                                   isGroup:     Boolean,
                                   selfId:      UserId)
                                  (implicit context: Context): String = {

    lazy val senderName = user.fold(getString(R.string.conversation_list__someone))(_.getDisplayName)
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
      case Message.Type.CONNECT_ACCEPTED | Message.Type.MEMBER_JOIN if !isGroup =>
        members.headOption.flatMap(_.handle).map(_.string).fold("")(StringUtils.formatHandle)
      case Message.Type.MEMBER_JOIN if members.exists(_.id == selfId) =>
        getString(R.string.conversation_list__added_you, senderName)
      case Message.Type.MEMBER_JOIN if members.length > 1=>
        getString(R.string.conversation_list__added, memberName)
      case Message.Type.MEMBER_JOIN =>
        getString(R.string.conversation_list__added, memberName)
      case Message.Type. MEMBER_LEAVE if members.exists(_.id == selfId) && user.exists(_.id == selfId) =>
        getString(R.string.conversation_list__left_you, senderName)
      case Message.Type. MEMBER_LEAVE if members.exists(_.id == selfId) =>
        getString(R.string.conversation_list__removed_you, senderName)
      case _ =>
        ""
    }
  }

  def subtitleStringForLastMessages(conv:                     ConversationData,
                                    otherMember:              Option[UserData],
                                    memberIds:                Set[UserId],
                                    unreadMessages:           Iterable[MessageData],
                                    lastMessage:              Option[MessageData],
                                    lastUnreadMessageUser:    Option[UserData],
                                    lastUnreadMessageMembers: Vector[UserData],
                                    typingUser:               Option[UserData],
                                    selfId:                   UserId)
                                   (implicit context: Context): String = {

    if (conv.convType == ConversationType.WaitForConnection || (lastMessage.exists(_.msgType == Message.Type.MEMBER_JOIN) && conv.convType == ConversationType.OneToOne)) {
      otherMember.flatMap(_.handle.map(_.string)).fold("")(StringUtils.formatHandle)
    } else if (memberIds.count(_ != selfId) == 0 && conv.convType == ConversationType.Group) {
      getString(R.string.conversation_list__empty_conv__subtitle)
    } else if (unreadMessages.isEmpty &&  !conv.isActive) {
      getString(R.string.conversation_list__left_you)
    } else if ((conv.muted || conv.incomingKnockMessage.nonEmpty || conv.missedCallMessage.nonEmpty) && typingUser.isEmpty) {
      val normalMessageCount = unreadMessages.count(m => !m.isSystemMessage && m.msgType != Message.Type.KNOCK)
      val missedCallCount = unreadMessages.count(_.msgType == Message.Type.MISSED_CALL)
      val pingCount = unreadMessages.count(_.msgType == Message.Type.KNOCK)
      val likesCount = 0//TODO: There is no good way to get this so far
      val unsentCount = unreadMessages.count(_.state == Message.Status.FAILED)

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
          context.getResources.getQuantityString(R.plurals.conversation_list__new_message_count, normalMessageCount, normalMessageCount.toString) else "",
        if (missedCallCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__missed_calls_count, missedCallCount, missedCallCount.toString) else "",
        if (pingCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__pings_count, pingCount, pingCount.toString) else "",
        if (likesCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__new_likes_count, likesCount, likesCount.toString) else ""
      ).filter(_.nonEmpty)
      Seq(unsentString, strings.mkString(", ")).filter(_.nonEmpty).mkString(" | ")
    } else {
      typingUser.fold {
        unreadMessages.lastOption.fold {
          ""
        } { msg =>
          subtitleStringForLastMessage(msg, lastUnreadMessageUser, lastUnreadMessageMembers, conv.convType == ConversationType.Group, selfId)
        }
      } { usr =>
        formatSubtitle(getString(R.string.conversation_list__typing), usr.getDisplayName, conv.convType == ConversationType.Group)
      }
    }
  }
}

class IncomingConversationListRow(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style)
  with ConversationListRow
  with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.conversation_list__row__height)))
  inflate(R.layout.conv_list_item)

  val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TypefaceTextView]
  val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]

  def setIncomingUsers(users: Seq[UserId]): Unit = {
    avatar.setAlpha(getResourceFloat(R.dimen.conversation_avatar_alpha_inactive))
    avatar.setMembers(users, ConvId(), ConversationType.Group)
    title.setText(getInboxName(users.size))
    badge.setStatus(ConversationBadge.WaitingConnection)
  }

  private def getInboxName(convSize: Int): String = getResources.getQuantityString(R.plurals.connect_inbox__link__name, convSize, convSize.toString)
}
