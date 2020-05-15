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
package com.waz.zclient.messages.parts.footer

import android.content.Context
import com.waz.api.Message.Status
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{LocalInstant, MessageData, ReadReceipt, UserId}
import com.waz.service.messages.{MessageAndLikes, MessagesService}
import com.waz.service.{NetworkModeService, ZMessaging}
import com.waz.threading.CancellableFuture
import com.waz.utils._
import com.waz.utils.events.{ClockSignal, EventContext, Signal}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{LikesController, UsersController}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.Time.SameDayTimeStamp
import com.waz.zclient.{Injectable, Injector, R}
import org.threeten.bp.Instant

import scala.concurrent.duration._

/**
  * Be warned - the timestamp/footer logic and when to display what has more edges than a tetrahedron.
  */
class FooterViewController(implicit inj: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {
  
  import com.waz.threading.Threading.Implicits.Ui

  val accents                = inject[AccentColorController]
  val selection              = inject[ConversationController].messages
  val signals                = inject[UsersController]
  val likesController        = inject[LikesController]

  val readReceiptsStorage = inject[Signal[ZMessaging]].map(_.readReceiptsStorage)
  val conversationController = inject[ConversationController]

  private lazy val zms = inject[Signal[ZMessaging]]

  val opts            = Signal[MsgBindOptions]()
  val messageAndLikes = Signal[MessageAndLikes]()
  val isSelfMessage   = opts.map(_.isSelf)

  val message =
    for {
      z   <- zms
      id  <- messageAndLikes.map(_.message.id)
      msg <- z.messagesStorage.signal(id)
    } yield msg

  val isLiked     = messageAndLikes.map(_.likes.nonEmpty)
  val likedBySelf = messageAndLikes.map(_.likedBySelf)
  val expiring    = message.map { msg => msg.isEphemeral && !msg.expired && msg.expiryTime.isDefined }

  //if the user likes OR dislikes something, we want to allow the timestamp/footer to disappear immediately
  val likedBySelfTime = Signal(Instant.EPOCH)
  likedBySelf.onChanged(_ => likedBySelfTime ! Instant.now)

  val active =
    for {
      (activeId, time) <- selection.lastActive
      msgId            <- message.map(_.id)
      lTime            <- likedBySelfTime
      untilTimeout = Instant.now.until(time.plus(selection.ActivityTimeout)).asScala
      active <-
        if (msgId != activeId || lTime.isAfter(time) || untilTimeout <= Duration.Zero) Signal.const(false)
        else Signal.future(CancellableFuture.delayed(untilTimeout)(false)).orElse(Signal const true) // signal `true` switching to `false` on timeout
    } yield active

  val showTimestamp: Signal[Boolean] = for {
    liked     <- isLiked
    selfMsg   <- isSelfMessage
    expiring  <- expiring
    timeAct   <- active
  } yield
    timeAct || expiring || (selfMsg && !liked)

  val ephemeralTimeout: Signal[Option[FiniteDuration]] = message.map(_.expiryTime) flatMap {
    case None => Signal const None
    case Some(expiry) if expiry <= LocalInstant.Now => Signal const None
    case Some(expiry) =>
      ClockSignal(1.second) map { now =>
        Some(now.until(expiry.instant).asScala).filterNot(_.isNegative)
      }
  }

  val conv = message.flatMap(signals.conv)

  val timestampText = for {
    selfUserId  <- inject[Signal[UserId]]
    convId      <- conv.map(_.id)
    isGroup     <- conversationController.groupConversation(convId)
    isTeamConv  <- conv.map(_.team.nonEmpty)
    msg         <- message
    timeout     <- ephemeralTimeout
    reads       <- readReceiptsStorage.flatMap(_.receipts(msg.id))
    isOffline   <- inject[NetworkModeService].isOnline.map(!_)
  } yield
    if (msg.isFailed) getString(R.string.message_footer__status__failed)
    else {
      lazy val timestamp = SameDayTimeStamp(msg.time.instant).string
      lazy val finalTimestamp =
        if (!msg.isEdited) timestamp
        else getString(R.string.message_footer__status__edited, SameDayTimeStamp(msg.editTime.instant).string)
      timeout match {
        case Some(t)                          => ephemeralTimeoutString(timestamp, t, isGroup, reads, isTeamConv)
        case None if selfUserId == msg.userId => statusString(finalTimestamp, msg, isGroup, isOffline, reads, isTeamConv)
        case None                             => timestamp
      }
    }

  val linkColor = expiring flatMap {
    case true => accents.accentColor.map(_.color)
    case false => Signal const getColor(R.color.accent_red);
  }

  val linkCallback = new Runnable() {
    def run() = for {
      msgs <- inject[Signal[MessagesService]].head
      m    <- message.head
    } yield
      if (m.isFailed) msgs.retryMessageSending(m.convId, m.id)
  }

  def onLikeClicked() = messageAndLikes.head.map { likesController.onLikeButtonClicked ! _ }

  private def timestampAndReads(timestamp: String, isGroup: Boolean, reads: Seq[ReadReceipt], isTeamConv: Boolean): Option[String] =
    if (reads.nonEmpty && isGroup && isTeamConv)
      Some(getString(R.string.message_footer__status__read_group, timestamp, reads.size.toString))
    else if (reads.nonEmpty && !isGroup)
      Some(getString(R.string.message_footer__status__read, timestamp, SameDayTimeStamp(reads.head.timestamp.instant).string))
    else
      None

  private def statusString(timestamp: String, m: MessageData, isGroup: Boolean, isOffline: Boolean, reads: Seq[ReadReceipt], isTeamConv: Boolean) =
    timestampAndReads(timestamp, isGroup, reads, isTeamConv).getOrElse(
      m.state match {
        case Status.PENDING if isOffline => getString(R.string.message_footer__status__waiting_for_connection)
        case Status.PENDING              => getString(R.string.message_footer__status__sending)
        case Status.SENT                 => getString(R.string.message_footer__status__sent, timestamp)
        case Status.DELIVERED if isGroup => getString(R.string.message_footer__status__sent, timestamp)
        case Status.DELIVERED            => getString(R.string.message_footer__status__delivered, timestamp)
        case Status.DELETED              => getString(R.string.message_footer__status__deleted, timestamp)
        case _                           => timestamp
      }
    )

  private def ephemeralTimeoutString(timestamp: String, remaining: FiniteDuration, isGroup: Boolean, reads: Seq[ReadReceipt], isTeamConv: Boolean) = {

    def unitString(resId: Int, quantity: Long) =
      getQuantityString(resId, quantity.toInt, quantity.toString)

    lazy val years    = unitString(R.plurals.unit_years, remaining.toDays / 365)
    lazy val weeks    = unitString(R.plurals.unit_weeks,  (remaining.toDays % 365) / 7)
    lazy val days     = unitString(R.plurals.unit_days,  remaining.toDays % 7)
    lazy val hours    = f"${remaining.toHours % 24}:${remaining.toMinutes % 60}%02d"
    lazy val minutes  = unitString(R.plurals.unit_minutes, remaining.toMinutes % 60)
    lazy val seconds  = unitString(R.plurals.unit_seconds, remaining.toSeconds % 60)

    lazy val weeksNotZero    = Option((remaining.toDays % 365) / 7).filter(_ > 0).map(unitString(R.plurals.unit_weeks,  _))
    lazy val daysNotZero     = Option(remaining.toDays % 7).filter(_ > 0).map(unitString(R.plurals.unit_days,  _))

    val remainingTimeStamp =
      if (remaining > 365.days)      weeksNotZero.fold(getString(R.string.ephemeral_message_footer_single_unit, years))(getString(R.string.ephemeral_message_footer_multiple_units, years, _))
      else if (remaining > 7.days)   daysNotZero.fold(getString(R.string.ephemeral_message_footer_single_unit, weeks))(getString(R.string.ephemeral_message_footer_multiple_units, weeks, _))
      else if (remaining > 1.day)    getString(R.string.ephemeral_message_footer_multiple_units, days, hours)
      else if (remaining > 1.hour)   getString(R.string.ephemeral_message_footer_single_unit, hours)
      else if (remaining > 1.minute) getString(R.string.ephemeral_message_footer_single_unit, minutes)
      else                           getString(R.string.ephemeral_message_footer_single_unit, seconds)

    s"${timestampAndReads(timestamp, isGroup, reads, isTeamConv).getOrElse(timestamp)} \u30FB $remainingTimeStamp"
  }
}
