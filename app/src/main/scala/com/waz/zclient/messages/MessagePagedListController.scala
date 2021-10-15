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
package com.waz.zclient.messages

import java.util.concurrent.Executor

import android.content.Context
import androidx.paging.PagedList
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageData.MessageDataDao
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.threading.Threading.Implicits.Background
import com.wire.signals._
import com.waz.utils.returning
import com.waz.utils.wrappers.DBCursor
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessagePagedListController._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MessagePagedListController()(implicit inj: Injector, ec: EventContext, cxt: Context)
  extends Injectable with DerivedLogTag {

  private val zms = inject[Signal[ZMessaging]]
  private val convController = inject[ConversationController]
  private val storage = zms.map(_.storage.db)
  private val messageActionsController = inject[MessageActionsController]

  private def loadCursor(convId: ConvId): Future[Option[DBCursor]] = {
    storage.head.flatMap(_.read(implicit db => MessageDataDao.msgCursor(convId))).map { Option(_) }
  }

  @volatile private var _pagedList = Option.empty[PagedList[MessageAndLikes]]
  private def getPagedList(cursor: Option[DBCursor]): PagedList[MessageAndLikes] = {
    def createPagedList(config: PagedListConfig) =
      new PagedList.Builder[Integer, MessageAndLikes](new MessageDataSource(cursor), config.config)
        .setFetchExecutor(ExecutorWrapper(Threading.Background))
        .setNotifyExecutor(ExecutorWrapper(Threading.Ui))
        .build()

    _pagedList.foreach(_.getDataSource.invalidate())

    returning(
      Try(createPagedList(NormalPagedListConfig)).getOrElse(createPagedList(MinPagedListConfig))
    ) { pl =>
      _pagedList = Option(pl)
    }
  }

  private def cursorRefreshEvent(zms: ZMessaging, convId: ConvId): EventStream[_] = {
    EventStream.zip(
      zms.messagesStorage.onMessagesDeletedInConversation.map(_.contains(convId)),
      zms.messagesStorage.onAdded.map(_.exists(_.convId == convId)),
      zms.messagesStorage.onUpdated.map(_.exists { case (prev, updated) =>
        updated.convId == convId &&  !MessagesPagedListAdapter.areMessageContentsTheSame(prev, updated)
      }),
      zms.reactionsStorage.onChanged.map(_.map(_.message)).mapSync { msgs: Seq[MessageId] =>
        zms.messagesStorage.getMessages(msgs: _*).map(_.flatten.exists(_.convId == convId))
      }
    ).filter(identity)
  }

  lazy val pagedListData: Signal[(MessageAdapterData, PagedListWrapper[MessageAndLikes], Option[MessageId])] = for {
    z                       <- zms
    (cId, cTeam, teamOnly)  <- convController.currentConv.map(c => (c.id, c.team, c.isTeamOnly))
    isGroup                 <- Signal.from(z.conversations.isGroupConversation(cId))
    canHaveLink             =  isGroup && cTeam.exists(z.teamId.contains(_)) && !teamOnly
    cursor                  <- RefreshingSignal.from(loadCursor(cId), cursorRefreshEvent(z, cId))
    _                       =  verbose(l"cursor changed")
    list                    =  PagedListWrapper(getPagedList(cursor))
    messageToReveal         <- messageActionsController.messageToReveal
  } yield (MessageAdapterData(cId, isGroup, canHaveLink, z.selfUserId, z.teamId), list, messageToReveal)
}

object MessagePagedListController {
  case class PagedListConfig(pageSize: Int, initialLoadSizeHint: Int, prefetchDistance: Int) {
    lazy val config = new PagedList.Config.Builder()
      .setPageSize(pageSize)
      .setInitialLoadSizeHint(initialLoadSizeHint)
      .setEnablePlaceholders(true)
      .setPrefetchDistance(prefetchDistance)
      .build()
  }

  val NormalPagedListConfig = PagedListConfig(10, 20, 30)
  val MinPagedListConfig    = PagedListConfig( 5, 10, 10)
}

final case class PagedListWrapper[T](pagedList: PagedList[T]) {
  override def equals(obj: scala.Any): Boolean = false
}

final case class ExecutorWrapper(ec: ExecutionContext) extends Executor {
  override def execute(command: Runnable): Unit = ec.execute(command)
}

final case class MessageAdapterData(convId: ConvId, isGroup: Boolean, canHaveLink: Boolean, selfId: UserId, teamId: Option[TeamId])
object MessageAdapterData {
  val Empty = MessageAdapterData(ConvId(), isGroup = false, canHaveLink = false, UserId(), None)
}
