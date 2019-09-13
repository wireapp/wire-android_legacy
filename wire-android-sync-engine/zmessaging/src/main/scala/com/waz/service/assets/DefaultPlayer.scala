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

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaPlayer.OnSeekCompleteListener
import com.waz.service.assets.GlobalRecordAndPlayService.{MediaPointer, UnauthenticatedContent}
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.utils.returning
import org.threeten.bp

import scala.concurrent.{Future, Promise}

class DefaultPlayer private (delegate: MediaPlayer, initialContent: UnauthenticatedContent) extends Player {
  import Threading.Implicits.BlockingIO

  override def start(): Future[Unit] = serialized(Future(delegate.start()))
  override def pause(): Future[MediaPointer] = serialized(Future {
    delegate.pause()
    MediaPointer(initialContent, bp.Duration.ofMillis(delegate.getCurrentPosition))
  })
  override def resume(): Future[Unit] = start()
  override def release(): Future[Unit] = serialized(Future(delegate.release()))
  override def playhead: Future[bp.Duration] = serialized(Future(bp.Duration.ofMillis(delegate.getCurrentPosition)))

  override def repositionPlayhead(nextPlayheadPosition: bp.Duration): Future[Unit] = serialized {
    val promisedSeek = Promise[Unit]
    delegate.setOnSeekCompleteListener(new OnSeekCompleteListener {
      override def onSeekComplete(mp: MediaPlayer): Unit = {
        delegate.setOnSeekCompleteListener(null)
        promisedSeek.success(())
      }
    })
    delegate.seekTo(nextPlayheadPosition.toMillis.toInt)
    promisedSeek.future
  }

  override def finalize: Unit = delegate.release()
}

object DefaultPlayer {
  def apply(content: UnauthenticatedContent, observer: Player.Observer)(implicit context: Context): DefaultPlayer = {
    val delegate = MediaPlayer.create(context, URI.unwrap(content.uri))

    delegate.setOnCompletionListener(new MediaPlayer.OnCompletionListener {
      override def onCompletion(mp: MediaPlayer): Unit = {
        observer.onCompletion()
      }
    })

    delegate.setOnErrorListener(new MediaPlayer.OnErrorListener {
      override def onError(mp: MediaPlayer, what: Int, extra: Int): Boolean =
        returning(true)(_ => observer.onError(s"MediaPlayer signaled error; what: $what, extra: $extra"))
    })

    new DefaultPlayer(delegate, content)
  }
}
