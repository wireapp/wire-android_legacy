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
package com.waz.zclient.views

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, Mention}
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.cursor.CursorText

import scala.concurrent.{ExecutionContext, Future}

class DraftMap(implicit injector: Injector) extends Injectable with DerivedLogTag {
  
  private val drafts = Signal(Map.empty[ConvId, CursorText])
  private lazy val conversationController = inject[ConversationController]

  def setCurrent(text: String, mentions: Seq[Mention])(implicit ec: ExecutionContext): Future[Unit] =
    setCurrent(CursorText(text, mentions))
  def setCurrent(cursorText: CursorText)(implicit ec: ExecutionContext): Future[Unit] =
    conversationController.currentConvId.head.map { id => set(id, cursorText) }

  def set(id: ConvId, text: String, mentions: Seq[Mention]): Unit = set(id, CursorText(text, mentions))
  def set(id: ConvId, cursorText: CursorText): Unit = drafts.mutate { _ + (id -> cursorText) }

  def resetCurrent()(implicit ec: ExecutionContext): Future[Unit] =
    conversationController.currentConvId.head.map { id => drafts.mutate(_ - id) }

  def get(id: ConvId)(implicit ec: ExecutionContext): Future[CursorText] = drafts.head.map { _.getOrElse(id, CursorText.Empty) }

  val currentDraft: Signal[CursorText] = for {
    convId <- conversationController.currentConvId
    d <- drafts
  } yield d.getOrElse(convId, CursorText.Empty)

  def withCurrentDraft(f: (CursorText) => Unit)(implicit ec: ExecutionContext): Future[Unit] = currentDraft.head.map( f )

  def tearDown(): Unit = drafts ! Map.empty[ConvId, CursorText]
}
