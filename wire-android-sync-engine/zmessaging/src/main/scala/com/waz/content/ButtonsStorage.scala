package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ButtonData.{ButtonDataDao, ButtonDataDaoId}
import com.waz.model.{ButtonData, ButtonId, MessageId}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.concurrent.Future

trait ButtonsStorage extends CachedStorage[(MessageId, ButtonId), ButtonData] {
  def findByMessage(messageId: MessageId): Future[Seq[ButtonData]]
}

class ButtonsStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[ButtonDataDaoId, ButtonData](new TrimmingLruCache(context, Fixed(MessagesStorage.cacheSize)), storage)(ButtonDataDao, LogTag("ButtonsStorage"))
    with ButtonsStorage with DerivedLogTag {
  override def findByMessage(messageId: MessageId): Future[Seq[ButtonData]] =
    find(_.messageId == messageId, ButtonDataDao.findForMessage(messageId)(_), identity)
}
