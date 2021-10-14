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
package com.waz.zclient.conversationlist.views

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.LinearLayout.LayoutParams
import android.widget.{FrameLayout, ImageView, LinearLayout}
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState.SelfCalling
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals.Signal
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.conversationlist.views.ConversationBadge.OngoingCall
import com.waz.zclient.conversationlist.views.ConversationListRow._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.conversationlist.views.row.MenuIndicatorView
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.ui.views.properties.MoveToAnimateable
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ConversationSignal, UiStorage, UserSetSignal, UserSignal, ViewUtils}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}

import scala.collection.Set
import com.waz.threading.Threading._

trait ConversationListRow extends View

class NormalConversationListRow(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ConversationListRow
    with ViewHelper
    with SwipeListView.SwipeListRow
    with MoveToAnimateable
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private implicit val executionContext = Threading.Background
  private implicit val uiStorage = inject[UiStorage]

  inflate(R.layout.conv_list_item)

  private val controller = inject[ConversationListController]
  private val zms = inject[Signal[ZMessaging]]

  private val conversationId = Signal[Option[ConvId]](None)

  private val conversation = for {
    Some(convId) <- conversationId
    conv         <- ConversationSignal(convId)
  } yield conv

  var conversationData = Option.empty[ConversationData]

  private lazy val members = conversationId.flatMap {
    case Some(cId) => controller.members(cId)
    case None      => Signal.const(Seq.empty[UserId])
  }

  private val container = ViewUtils.getView(this, R.id.conversation_row_container).asInstanceOf[ConstraintLayout]
  private val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TypefaceTextView]
  private val subtitle = ViewUtils.getView(this, R.id.conversation_subtitle).asInstanceOf[TypefaceTextView]
  private val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  private val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]
  private val menuIndicatorView = ViewUtils.getView(this, R.id.conversation_menu_indicator).asInstanceOf[MenuIndicatorView]

  private val userTyping = for {
    z          <- zms
    convId     <- conversation.map(_.id)
    typing     <- Signal.from(z.typing.onTypingChanged.filter(_._1 == convId).map(_._2.headOption)).orElse(Signal.const(None))
    typingUser <- userData(typing.map(_.id))
  } yield typingUser

  private val badgeInfo = for {
    z              <- zms
    conv           <- conversation
    typing         <- userTyping.map(_.nonEmpty)
    availableCalls <- z.calling.joinableCalls
    call           <- z.calling.currentCall
    callDuration   <- call.filter(_.convId == conv.id).fold(Signal.const(""))(_.durationFormatted)
    isGroupConv    <- z.conversations.groupConversation(conv.id)
  } yield (conv.id, badgeStatusForConversation(conv, conv.unreadCount, typing, availableCalls, callDuration, isGroupConv))

  badgeInfo.onUi {
    case (convId, status) if conversationData.forall(_.id == convId) =>
      badge.setStatus(status)
    case _ =>
      verbose(l"Outdated badge status")
  }

  private lazy val currentDomain = inject[Domain]

  private val subtitleText = for {
    z <- zms
    conv <- conversation
    lastMessage <- controller.lastMessage(conv.id).map(_.lastMsg)
    lastUnreadMessage = lastMessage.filter(_.userId != z.selfUserId).filter(_ => conv.unreadCount.total > 0)
    lastUnreadMessageUser <- lastUnreadMessage.fold2(Signal.const(Option.empty[UserData]), message => UserSignal(message.userId).map(Some(_)))
    lastUnreadMessageMembers <- lastUnreadMessage.fold2(Signal.const(Vector[UserData]()), message => UserSetSignal(message.members).map(_.toVector))
    typingUser <- userTyping
    ms <- members
    otherUser <- userData(ms.headOption)
    isGroupConv <- z.conversations.groupConversation(conv.id)
    missedCallerId <- controller.lastMessage(conv.id).map(_.lastMissedCall.map(_.userId))
    userName <- missedCallerId.fold2(Signal.const(Option.empty[Name]), u => z.usersStorage.signal(u).map(d => Some(d.name)))
  } yield (
    conv.id,
    subtitleStringForLastMessages(
      conv,
      otherUser,
      ms.toSet,
      lastMessage,
      lastUnreadMessage,
      lastUnreadMessageUser,
      lastUnreadMessageMembers,
      typingUser,
      z.selfUserId,
      isGroupConv,
      userName,
      currentDomain
    )
  )

  subtitleText.onUi {
    case (convId, text) if conversationData.forall(_.id == convId) =>
      setSubtitle(text)
    case _ =>
      verbose(l"Outdated conversation subtitle")
  }

  private def userData(id: Option[UserId]) = id.fold2(Signal.const(Option.empty[UserData]), uid => UserSignal(uid).map(Option(_)))

  private lazy val avatarInfo = for {
    z <- zms
    conv <- conversation
    memberIds <- members
    memberSeq <- Signal.sequence(memberIds.map(uid => UserSignal(uid)):_*)
    isGroup <- Signal.from(z.conversations.isGroupConversation(conv.id))
  } yield {
    val opacity =
      if ((memberIds.isEmpty && isGroup) || conv.convType == ConversationType.WaitForConnection || !conv.isActive)
        getResourceFloat(R.dimen.conversation_avatar_alpha_inactive)
      else
        getResourceFloat(R.dimen.conversation_avatar_alpha_active)
    (conv.id, isGroup, memberSeq.filter(_.id != z.selfUserId), opacity, z.teamId)
  }

  def setSubtitle(text: String): Unit = {
    if (text.nonEmpty) {
      subtitle.setVisibility(View.VISIBLE)
      subtitle.setText(text)
      TextViewUtils.boldText(subtitle)
    } else {
      subtitle.setVisibility(View.GONE)
      subtitle.setText("")
    }
  }

  // User availability (for 1:1)
  (for {
    Some(cId)    <- conversationId
    availability <- controller.availability(cId)
  } yield availability).onUi {
    case Availability.None => AvailabilityView.hideAvailabilityIcon(title)
    case availability      => AvailabilityView.displayStartOfText(title, availability, title.getCurrentTextColor, pushDown = true)
  }

  // conversation name
  (for {
    Some(cId) <- conversationId
    name      <- controller.conversationName(cId)
  } yield name).onUi(name => title.setText(name.str))

  avatarInfo.onUi {
    case (convId, isGroup, members, _, selfTeam) if conversationData.forall(_.id == convId) =>
      avatar.setMembers(members, convId, isGroup, selfTeam)
    case _ =>
      verbose(l"Outdated avatar info")
  }
  avatarInfo.onUi {
    case (convId, isGroup, _, alpha, _) if conversationData.forall(_.id == convId) =>
      if (!isGroup) {
        avatar.setConversationType(ConversationType.OneToOne)
      }
      avatar.setAlpha(alpha)
    case _ =>
      verbose(l"Outdated avatar info")
  }

  badge.onClickEvent.onUi {
    case ConversationBadge.IncomingCall =>
      (zms.map(_.selfUserId).currentValue, conversationData.map(_.id)) match {
        case (Some(acc), Some(cId)) => inject[CallStartController].startCall(acc, cId, forceOption = true)
        case _ => //
      }
    case OngoingCall(_) =>
      CallingActivity.startIfCallIsActive(getContext)
    case _=>
  }

  private var conversationCallback: ConversationCallback = _
  private var maxAlpha: Float = .0f
  private var openState: Boolean = false
  private val menuOpenOffset: Int = getDimenPx(R.dimen.list__menu_indicator__max_swipe_offset)
  private var moveTo: Float = .0f
  private var maxOffset: Float = .0f
  private var moveToAnimator: ObjectAnimator = _

  def setConversation(conversationData: ConversationData, conversationName: Name): Unit =
    if (this.conversationData.forall(_.id != conversationData.id)) {
      this.conversationData = Some(conversationData)
      title.setText(conversationName.str)

      badge.setStatus(ConversationBadge.Empty)
      subtitle.setText("")
      avatar.clearImages()
      avatar.setAlpha(getResourceFloat(R.dimen.conversation_avatar_alpha_active))
      conversationId.publish(Option(conversationData.id), Threading.Ui)
      closeImmediate()
    }

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

  override def open(): Unit =
    if (!openState) {
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

  def formatSubtitle(content: String, user: String, group: Boolean, isEphemeral: Boolean = false, replyPrefix: Boolean = false, quotePrefix: Boolean = false)(implicit context: Context): String = {
    val groupSubtitle = if(quotePrefix) R.string.conversation_list__group_with_quote else R.string.conversation_list__group_without_quote
    val singleSubtitle = if(quotePrefix) R.string.conversation_list__single_with_quote else R.string.conversation_list__single_without_quote
    if (group && !isEphemeral) {
      getString(groupSubtitle, user, content)
    } else {
      getString(singleSubtitle, content)
    }
  }

  def badgeStatusForConversation(conversationData:        ConversationData,
                                 unreadCount:             ConversationData.UnreadCount,
                                 typing:                  Boolean,
                                 availableCalls:          Map[ConvId, CallInfo],
                                 callDuration:            String,
                                 isGroupConv:             Boolean
                                ): ConversationBadge.Status = {
    if (callDuration.nonEmpty) {
      ConversationBadge.OngoingCall(Some(callDuration))
    } else if (availableCalls.contains(conversationData.id)) {
      availableCalls(conversationData.id).state match {
        case SelfCalling => OngoingCall(None)
        case _           => ConversationBadge.IncomingCall
      }
    } else if (conversationData.convType == ConversationType.WaitForConnection || conversationData.convType == ConversationType.Incoming) {
      ConversationBadge.WaitingConnection
    } else if (unreadCount.mentions > 0 && !conversationData.muted.isAllMuted) {
      ConversationBadge.Mention
    } else if (unreadCount.quotes > 0 && !conversationData.muted.isAllMuted) {
      ConversationBadge.Quote
    } else if (!conversationData.muted.isAllAllowed) {
      ConversationBadge.Muted
    } else if (typing) {
      ConversationBadge.Typing
    } else if (conversationData.missedCallMessage.nonEmpty) {
      ConversationBadge.MissedCall
    } else if (conversationData.incomingKnockMessage.nonEmpty) {
      ConversationBadge.Ping
    } else if (unreadCount.messages > 0) {
      ConversationBadge.Count(unreadCount.messages)
    } else {
      ConversationBadge.Empty
    }
  }

  private def subtitleStringForLastMessage(messageData:   MessageData,
                                           user:          Option[UserData],
                                           members:       Vector[UserData],
                                           isGroup:       Boolean,
                                           selfId:        UserId,
                                           isQuote:       Boolean,
                                           currentDomain: Domain
                                  )(implicit context: Context): String = {
    lazy val senderName = user.map(_.name).getOrElse(Name(getString(R.string.conversation_list__someone)))
    lazy val memberName = members.headOption.map(_.name).getOrElse(Name(getString(R.string.conversation_list__someone)))

    if (messageData.isEphemeral) {
      if (messageData.hasMentionOf(selfId)) {
        if (isGroup) formatSubtitle(getString(R.string.conversation_list__group_eph_and_mention), senderName, isGroup, isEphemeral = true)
        else formatSubtitle(getString(R.string.conversation_list__single_eph_and_mention), senderName, isGroup, isEphemeral = true)
      } else if (isQuote) {
        if (isGroup) formatSubtitle(getString(R.string.conversation_list__group_eph_and_quote), senderName, isGroup, isEphemeral = true)
        else formatSubtitle(getString(R.string.conversation_list__single_eph_and_quote), senderName, isGroup, isEphemeral = true)
      } else
        formatSubtitle(getString(R.string.conversation_list__ephemeral), senderName, isGroup, isEphemeral = true)
    } else {
      messageData.msgType match {
        case Message.Type.TEXT | Message.Type.TEXT_EMOJI_ONLY | Message.Type.RICH_MEDIA | Message.Type.COMPOSITE =>
          formatSubtitle(messageData.contentString, senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.IMAGE_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__image), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.ANY_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__file), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.VIDEO_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__video), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.AUDIO_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__audio), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.LOCATION =>
          formatSubtitle(getString(R.string.conversation_list__shared__location), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.MISSED_CALL =>
          formatSubtitle(getString(R.string.conversation_list__missed_call), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.KNOCK =>
          formatSubtitle(getString(R.string.conversation_list__pinged), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.CONNECT_ACCEPTED | Message.Type.MEMBER_JOIN if !isGroup =>
          members.headOption.map(_.displayHandle(currentDomain)).getOrElse("")
        case Message.Type.MEMBER_JOIN if members.exists(_.id == selfId) =>
          getString(R.string.conversation_list__added_you, senderName)
        case Message.Type.MEMBER_JOIN if members.length > 1 =>
          getString(R.string.conversation_list__added, memberName)
        case Message.Type.MEMBER_JOIN =>
          getString(R.string.conversation_list__added, memberName)
        case Message.Type.MEMBER_LEAVE |
             Message.Type.MEMBER_LEAVE_DUE_TO_LEGAL_HOLD if members.exists(_.id == selfId) && user.exists(_.id == selfId) =>
          getString(R.string.conversation_list__left_you, senderName)
        case Message.Type.MEMBER_LEAVE |
             Message.Type.MEMBER_LEAVE_DUE_TO_LEGAL_HOLD if members.exists(_.id == selfId) =>
          getString(R.string.conversation_list__removed_you, senderName)
        case _ =>
          ""
      }
    }
  }

  def subtitleStringForLastMessages(conv:                     ConversationData,
                                    otherMember:              Option[UserData],
                                    memberIds:                Set[UserId],
                                    lastMessage:              Option[MessageData],
                                    lastUnreadMessage:        Option[MessageData],
                                    lastUnreadMessageUser:    Option[UserData],
                                    lastUnreadMessageMembers: Vector[UserData],
                                    typingUser:               Option[UserData],
                                    selfId:                   UserId,
                                    isGroupConv:              Boolean,
                                    userName:                 Option[Name],
                                    currentDomain:            Domain)
                                   (implicit context: Context): String = {
    if (conv.convType == ConversationType.WaitForConnection || (lastMessage.exists(_.msgType == Message.Type.MEMBER_JOIN) && !isGroupConv)) {
      otherMember.map(_.displayHandle(currentDomain)).getOrElse("")
    } else if (memberIds.count(_ != selfId) == 0 && conv.convType == ConversationType.Group) {
      ""
    } else if (conv.unreadCount.total == 0 && !conv.isActive) {
      getString(R.string.conversation_list__left_you)
    } else if (
      ( conv.muted.isAllMuted ||
        conv.incomingKnockMessage.nonEmpty ||
        conv.missedCallMessage.nonEmpty ||
        conv.unreadCount.mentions > 1 ||
        conv.unreadCount.quotes > 1 ||
        (conv.unreadCount.mentions == 1 && (conv.unreadCount.messages > 0 || conv.unreadCount.quotes > 0)) ||
        (conv.unreadCount.quotes == 1 && conv.unreadCount.messages > 0) ||
        (conv.muted.onlyMentionsAllowed && (conv.unreadCount.mentions > 1 || conv.unreadCount.quotes > 1 || conv.unreadCount.total - conv.unreadCount.mentions - conv.unreadCount.quotes > 0))
      )
      && typingUser.isEmpty) {

      val normalMessageCount = conv.unreadCount.normal
      val missedCallCount = conv.unreadCount.call
      val pingCount = conv.unreadCount.ping
      val likesCount = 0 //TODO: There is no good way to get this so far
      val unsentCount = conv.failedCount
      val mentionsCount = conv.unreadCount.mentions
      val quotesCount = conv.unreadCount.quotes

      val unsentString =
        if (unsentCount > 0)
          if (normalMessageCount + missedCallCount + pingCount + likesCount == 0)
            getString(R.string.conversation_list__unsent_message_long)
          else
            getString(R.string.conversation_list__unsent_message_short)
        else
          ""
      val strings = Seq(
        if (mentionsCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__mentions_count, mentionsCount, mentionsCount.toString) else "",
        if (quotesCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__quotes_count, quotesCount, quotesCount.toString) else "",
        if (missedCallCount > 0) {
          if (isGroupConv) {
            if (conv.unreadCount.total > 1 || conv.isAllMuted || conv.onlyMentionsAllowed)
              getQuantityString(R.plurals.conversation_list__missed_calls_plural, missedCallCount, missedCallCount.toString)
            else
              getString(R.string.conversation_list__missed_calls_count_group, userName.getOrElse(Name.Empty).str)
          } else {
            if (conv.unreadCount.total > 1 || conv.isAllMuted || conv.onlyMentionsAllowed)
              getQuantityString(R.plurals.conversation_list__missed_calls_plural, missedCallCount, missedCallCount.toString)
            else
              getString(R.string.conversation_list__missed_calls_count)
          }
        } else "",
        if (pingCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__pings_count, pingCount, pingCount.toString) else "",
        if (normalMessageCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__new_message_count, normalMessageCount, normalMessageCount.toString) else "",
        if (likesCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__new_likes_count, likesCount, likesCount.toString) else ""
      ).filter(_.nonEmpty)
      Seq(unsentString, strings.mkString(", ")).filter(_.nonEmpty).mkString(" | ")
    } else {
      typingUser.fold {
        lastUnreadMessage.fold {
          ""
        } { msg =>
          // if we are here, it means there is only one unread message, so if the number of quotes > 0, it means this unread message is a quote
          subtitleStringForLastMessage(
            msg,
            lastUnreadMessageUser,
            lastUnreadMessageMembers,
            isGroupConv,
            selfId,
            conv.unreadCount.quotes > 0,
            currentDomain
          )
        }
      } { usr =>
        formatSubtitle(getString(R.string.conversation_list__typing), usr.name, isGroupConv)
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

  var firstIncomingConversation = Option.empty[ConvId]

  val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TypefaceTextView]
  val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]
  val separator = ViewUtils.getView(this, R.id.conversation_separator).asInstanceOf[View]

  def setIncoming(first: ConvId, numberOfRequests: Int): Unit = {
    firstIncomingConversation = Some(first)
    avatar.setAlpha(getResourceFloat(R.dimen.conversation_avatar_alpha_inactive))
    title.setText(getInboxName(numberOfRequests))
    badge.setStatus(ConversationBadge.WaitingConnection)
  }

  def setSeparatorVisibility(isVisible: Boolean): Unit = {
    separator.setVisibility(if (isVisible) View.VISIBLE else View.GONE)
  }

  private def getInboxName(convSize: Int): String = getResources.getQuantityString(R.plurals.connect_inbox__link__name, convSize, convSize.toString)
}

class ConversationFolderListRow(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
  with ConversationListRow
  with ViewHelper
  with DerivedLogTag {

  import ConversationFolderListRow._
  import com.waz.zclient.log.LogUI._
  import com.waz.zclient.utils._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.conv_list_section_header)
  setLayoutParameters()

  private val expandIcon = findById[ImageView](R.id.conv_list_section_imageview_expand)
  private val title = findById[TypefaceTextView](R.id.conv_list_section_textview_title)
  private val badge = findById[ConversationBadge](R.id.folder_badge_text)

  def setTitle(title: String): Unit = this.title.setText(title)

  def setUnreadCount(unreadCount: Int): Unit = {
    verbose(l"badge count: $unreadCount")
    badge.setVisible(unreadCount > 0)
    if (unreadCount > MaxBadgeCount) badge.setText(OverMaxBadge)
    else if (unreadCount > 0) badge.setText(unreadCount.toString)
  }

  def setIsFirstHeader(isFirstHeader: Boolean): Unit = {
    val params = getLayoutParams.asInstanceOf[RecyclerView.LayoutParams]
    params.topMargin = if (isFirstHeader) 0 else getDimenPx(R.dimen.wire__padding__10)
    setLayoutParams(params)
  }

  def setIsExpanded(isExpanded: Boolean): Unit = {
    val resId = if (isExpanded) R.drawable.icon_arrow_down_white else R.drawable.icon_arrow_right_white
    expandIcon.setImageDrawable(getDrawable(resId))
  }

  private def setLayoutParameters(): Unit = {
    val params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.conversation_list__folder_row__height))
    setLayoutParams(params)
    setOrientation(LinearLayout.HORIZONTAL)
    setPadding(getDimenPx(R.dimen.wire__padding__24), getDimenPx(R.dimen.wire__padding__10), 0, getDimenPx(R.dimen.wire__padding__10))
  }
}

object ConversationFolderListRow {
  val MaxBadgeCount = 99
  val OverMaxBadge = "99+"
}
