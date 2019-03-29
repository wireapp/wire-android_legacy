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
package com.waz.zclient

import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.content.{ClipData, ClipboardManager, Context}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.EventStream
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._

class ClipboardUtils(context: Context) extends DerivedLogTag {

  private lazy val clipboardManager: ClipboardManager =
    context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]

  def primaryClipChanged: EventStream[Unit] = new EventStream[Unit] {
    private val listener = new OnPrimaryClipChangedListener {
      override def onPrimaryClipChanged(): Unit = publish(())
    }
    override protected def onWire(): Unit = {
      clipboardManager.addPrimaryClipChangedListener(listener)
    }
    override protected def onUnwire(): Unit = {
      clipboardManager.removePrimaryClipChangedListener(listener)
    }
  }

  def setPrimaryClip(data: ClipData): Unit = clipboardManager.setPrimaryClip(data)

  def getPrimaryClip: Option[ClipData] =
    returning(Option(clipboardManager.getPrimaryClip)) { primaryClip =>
      verbose(l"Primary clip is empty: ${primaryClip.isEmpty}")
    }

  @inline
  def getItems(data: ClipData): Stream[ClipData.Item] =
    (0 until data.getItemCount).toStream.map(data.getItemAt)

  @inline
  def getPrimaryClipItems: Stream[ClipData.Item] =
    getPrimaryClip.fold(Stream.empty[ClipData.Item])(getItems)

  def getPrimaryClipItemsAsText: Stream[CharSequence] =
    getPrimaryClipItems.map(_.coerceToText(context))

  def getPrimaryClipItemsAsStyledText: Stream[CharSequence] =
    getPrimaryClipItems.map(_.coerceToStyledText(context))

  def getPrimaryClipItemsAsHtmlText: Stream[CharSequence] =
    getPrimaryClipItems.map(_.coerceToHtmlText(context))

}


