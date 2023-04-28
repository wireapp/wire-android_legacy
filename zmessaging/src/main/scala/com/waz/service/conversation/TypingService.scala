/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.conversation

import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.{ConversationStorage, GlobalPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.AccountsService.InForeground
import com.waz.service.ZMessaging.clock
import com.waz.service._
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.wire.signals.{AggregatingSignal, CancellableFuture, EventStream, Signal}
import com.waz.utils.RichFuture.traverseSequential

import scala.concurrent.Future
import scala.concurrent.duration._
import org.threeten.bp

class TypingService(userId:        UserId,
                    conversations: ConversationStorage,
                    timeouts:      Timeouts,
                    accounts:      AccountsService,
                    sync:          SyncServiceHandle,
                    prefs:         GlobalPreferences) extends DerivedLogTag {

  import timeouts.typing._

  import Threading.Implicits.Background
  private val beDriftPref = prefs.preference(BackendDrift)

  private var typing: ConvId Map IndexedSeq[TypingUser] = Map().withDefaultValue(Vector.empty)

  private var selfIsTyping: Option[(ConvId, Long)] = None

  private var stopTypingTimeout: CancellableFuture[Unit] = CancellableFuture.successful(())
  private var refreshIsTyping: CancellableFuture[Unit] = CancellableFuture.successful(())

  val onTypingChanged = EventStream[(ConvId, IndexedSeq[TypingUser])]()

  val typingEventStage = EventScheduler.Stage[TypingEvent]((c, es, tag) => {
    verbose(l"SSSTAGES<TAG:$tag> TypingService stage 1")
    traverseSequential(es)(handleTypingEvent)}
  )

  accounts.accountState(userId).on(Threading.Background) {
    case InForeground => // fine
    case _            => stopTyping()
  }

  def typingUsers(conv: ConvId): Signal[IndexedSeq[UserId]] = new AggregatingSignal[IndexedSeq[TypingUser], IndexedSeq[UserId]] (
    loader        = () => Future { typing(conv).map(_.id) },
    sourceStream  = onTypingChanged.filter(_._1 == conv).map(_._2),
    updater       = (_, updated) => updated.map(_.id)
  )

  def handleTypingEvent(e: TypingEvent): Future[Unit] = beDriftPref.apply().map { beDrift =>
    if (isRecent(e, beDrift)) {
      conversations.getByRemoteId(e.convId) map {
        case Some(conv) => setUserTyping(conv.id, e.from, e.time.toLocal(beDrift), e.isTyping)
        case None => warn(l"Conversation ${e.convId} not found, ignoring.")
      }
    } else {
      Future.successful(())
    }
  }

  def selfChangedInput(conv: ConvId): Future[Unit] = Future {
    stopTypingTimeout.cancel()
    selfIsTyping = Some((conv, clock.millis))
    if (refreshIsTyping.isCompleted) postIsTyping(conv)
    stopTypingTimeout = CancellableFuture.delayed(stopTimeout) { stopTyping(conv) }
  }

  def selfClearedInput(conv: ConvId): Future[Unit] = Future {
    stopTypingTimeout.cancel()
    stopTyping(conv)
  }

  private def stopTyping(conv: ConvId): Unit = selfIsTyping match {
    case Some((`conv`, _)) =>
      refreshIsTyping.cancel()
      selfIsTyping = None
      sync.postTypingState(conv, typing = false)
    case _ => ()
  }

  private def stopTyping(): Unit = selfIsTyping foreach {
    case (conv, _) =>
      stopTypingTimeout.cancel()
      refreshIsTyping.cancel()
      selfIsTyping = None
      sync.postTypingState(conv, typing = false)
  }

  private def postIsTyping(conv: ConvId): Unit = {
    sync.postTypingState(conv, typing = true)
    refreshIsTyping = CancellableFuture.delayed(refreshDelay) { postIsTyping(conv) }
  }

  def getTypingUsers(conv: ConvId): CancellableFuture[IndexedSeq[UserId]] = Threading.Background { typing(conv) map (_.id) }

  def isSelfTyping(conv: ConvId): CancellableFuture[Boolean] = Threading.Background { selfIsTyping.exists(_._1 == conv) }

  private def setUserTyping(conv: ConvId, user: UserId, time: LocalInstant, isTyping: Boolean): Unit = {
    val current = typing(conv)
    current.find(_.id == user).foreach(_.cleanUp.cancel())

    if (!isTyping && current.exists(_.id == user)) {
      typing += conv -> current.filterNot(user == _.id)
      onTypingChanged ! (conv -> typing(conv))
    } else if (isTyping) {
      val cleanUp = CancellableFuture.delayed(receiverTimeout - (clock.millis - time.toEpochMilli).millis) {
        setUserTyping(conv, user, LocalInstant.Now, isTyping = false)
      }
      val idx = current.indexWhere(_.id == user)
      if (idx == -1) {
        typing += conv -> (current :+ TypingUser(user, time, cleanUp))
        onTypingChanged ! (conv -> typing(conv))
      } else {
        typing += conv -> current.updated(idx, TypingUser(user, time, cleanUp))
      }
    }
  }

  def isRecent(event: TypingEvent, beDrift: bp.Duration): Boolean = {
    val now = LocalInstant.Now.toRemote(beDrift).toEpochMilli
    now - event.time.toEpochMilli < receiverTimeout.toMillis
  }
}

case class TypingUser(id: UserId, time: LocalInstant, cleanUp: CancellableFuture[Unit])
