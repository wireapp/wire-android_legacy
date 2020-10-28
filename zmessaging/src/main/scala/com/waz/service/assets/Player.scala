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
package com.waz.service.assets

import java.util.UUID

import android.content.Context
import com.waz.service.assets.GlobalRecordAndPlayService.{MediaPointer, PCMContent, UnauthenticatedContent, Content => GRAPContent}
import com.wire.signals.Serialized
import org.threeten.bp

import scala.concurrent.Future
import scala.concurrent.Future._

abstract class Player {
  def start(): Future[Unit]
  def pause(): Future[MediaPointer]
  def resume(): Future[Unit]
  def release(): Future[Unit]
  def playhead: Future[bp.Duration]
  def repositionPlayhead(pos: bp.Duration): Future[Unit]

  private lazy val token = UUID.randomUUID
  protected def serialized[A](f: => Future[A]): Future[A] = Serialized.future(token.toString)(f)
}

object Player {
  trait Observer {
    def onCompletion(): Unit
    def onError(msg: String): Unit
  }

  def apply(content: GRAPContent, observer: Observer)(implicit context: Context): Future[Player] = content match {
    case c @ UnauthenticatedContent(_) => successful(DefaultPlayer(c, observer))
    case c @ PCMContent(_) => PCMPlayer(c, observer)
  }
}
