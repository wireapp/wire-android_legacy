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
package com.waz.content
import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.model.ReadReceipt.ReadReceiptDao
import com.waz.model.{MessageId, ReadReceipt}
import com.waz.service.messages.MessagesService
import com.wire.signals.SerialDispatchQueue
import com.waz.utils.TrimmingLruCache.Fixed
import com.wire.signals.{RefreshingSignal, Signal}
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.concurrent.{ExecutionContext, Future}

trait ReadReceiptsStorage extends CachedStorage[ReadReceipt.Id, ReadReceipt] {
  def getReceipts(message: MessageId): Future[Seq[ReadReceipt]]
  def receipts(message: MessageId): Signal[Seq[ReadReceipt]]
  def removeAllForMessages(message: Set[MessageId]): Future[Unit]
}

class ReadReceiptsStorageImpl(context: Context, storage: Database, msgStorage: MessagesStorage, msgService: MessagesService)
  extends CachedStorageImpl[ReadReceipt.Id, ReadReceipt](new TrimmingLruCache(context, Fixed(ReadReceiptsStorage.cacheSize)), storage)(ReadReceiptDao, LogTag("ReadReceiptsStorage"))
  with ReadReceiptsStorage {
  import com.wire.signals.EventContext.Implicits.global
  private implicit val dispatcher = SerialDispatchQueue(name = "ReadReceiptsStorage")

  msgStorage.onDeleted { ids => removeAllForMessages(ids.toSet) }

  msgService.msgEdited { case (prev, cur) =>
    // `updateAll2` is not going to work here, because we're updating the messageId of the receipts which is a part of the receipt's id.
    // `update*` methods assume that the ids are not going to be updated. Instead, we need to remove the old and insert the new receipts.
      for {
        receipts    <- getReceipts(prev)
        _           <- removeAll(receipts.map(_.id))
        newReceipts =  receipts.map(r => (cur, r.user) -> r.copy(message = cur)).toMap
        _           <- updateOrCreateAll2(newReceipts.keys, { (k, _) => newReceipts(k) })
      } yield ()
  }

  override def getReceipts(message: MessageId): Future[Seq[ReadReceipt]] =
    find(_.message == message, ReadReceiptDao.findForMessage(message)(_), identity)

  override def receipts(message: MessageId): Signal[Seq[ReadReceipt]] = {
    val changed = onChanged.map(_.filter(_.message == message).map(_.id)).zip(onDeleted.map(_.filter(_._1 == message)))
    RefreshingSignal[Seq[ReadReceipt]](getReceipts(message), changed)
  }

  override def removeAllForMessages(messages: Set[MessageId]): Future[Unit] =
    find(rr => messages.contains(rr.message), ReadReceiptDao.findForMessages(messages)(_), identity)
      .map(_.map(_.id))
      .flatMap(removeAll)
}

object ReadReceiptsStorage {
  val cacheSize = 12048
}
