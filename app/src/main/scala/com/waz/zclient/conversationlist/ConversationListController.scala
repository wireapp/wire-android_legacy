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
package com.waz.zclient.conversationlist

import com.waz.api.Message
import com.waz.content.ConversationStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.ConversationData.ConversationType.{Self, Unknown}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService, FoldersService}
import com.waz.service.teams.TeamsService
import com.waz.threading.{SerialDispatchQueue, Threading}
import com.waz.utils._
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversationlist.ConversationListManagerFragment.ConvListUpdateThrottling
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter.Folder
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, R}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class ConversationListController(implicit inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag {

  import ConversationListController._
  import Threading.Implicits.Background

  val zms = inject[Signal[ZMessaging]]
  val membersCache = zms map { new MembersCache(_) }
  val lastMessageCache = zms map { new LastMessageCache(_) }

  lazy val folderStateController = inject[FolderStateController]

  private lazy val foldersService = inject[Signal[FoldersService]]
  private lazy val convService = inject[Signal[ConversationsService]]

  def members(conv: ConvId) = membersCache.flatMap(_.apply(conv))

  def lastMessage(conv: ConvId) = lastMessageCache.flatMap(_.apply(conv))

  lazy val userAccountsController = inject[UserAccountsController]
  implicit val uiStorage = inject[UiStorage]

  // availability will be other than None only when it's a one-to-one conversation
  // (and the other user's availability is set to something else than None)
  def availability(conv: ConvId): Signal[Availability] = for {
    currentUser <- userAccountsController.currentUser
    isInTeam = currentUser.exists(_.teamId.nonEmpty)
    memberIds <- if (isInTeam) members(conv) else Signal.const(Seq.empty)
    otherUser <- if (memberIds.size == 1) userData(memberIds.headOption) else Signal.const(Option.empty[UserData])
  } yield {
    otherUser.fold[Availability](Availability.None)(_.availability)
  }

  private def userData(id: Option[UserId]) = id.fold2(Signal.const(Option.empty[UserData]), uid => UserSignal(uid).map(Option(_)))

  lazy val establishedConversations = for {
    z          <- zms
    convs      <- z.convsStorage.contents.throttle(ConvListUpdateThrottling)
  } yield convs.values.filter(EstablishedListFilter)

  lazy val regularConversationListData = conversationData(Normal)
  lazy val archiveConversationListData = conversationData(Archive)

  lazy val hasConversationsAndArchive = for {
    convsStorage <- inject[Signal[ConversationStorage]]
    convs        <- convsStorage.contents.map(_.values.filterNot(c => c.hidden || ignoredConvTypes.contains(c.convType)))
  } yield (convs.exists(!_.archived), convs.exists(_.archived))

  private def conversationData(listMode: ListMode) =
    for {
      convsStorage  <- inject[Signal[ConversationStorage]]
      conversations <- convsStorage.contents
    } yield
      conversations.values.filter(listMode.filter).toSeq.sorted(listMode.sort)

  lazy val incomingConversationListData: Signal[Seq[ConvId]] =
    for {
      convsStorage   <- inject[Signal[ConversationStorage]]
      conversations  <- convsStorage.contents
      incomingConvs  =  conversations.values.filter(Incoming.filter).map(_.id).toSeq
    } yield incomingConvs

  lazy val foldersWithConvs: Signal[Map[FolderId, Set[ConvId]]] = foldersService.flatMap(_.foldersWithConvs)

  private lazy val customFoldersWithConvs: Signal[Map[FolderId, Set[ConvId]]] = {
    for {
      favoritesFolderId <- favoritesFolderId
      foldersWithConvs  <- foldersWithConvs
    } yield {
      favoritesFolderId.fold(foldersWithConvs)(foldersWithConvs - _)
    }
  }

  def folder(folderId: FolderId): Signal[Option[FolderData]] = foldersService.flatMap(_.folder(folderId))

  lazy val favoritesFolderId: Signal[Option[FolderId]] = foldersService.flatMap(_.favoritesFolderId)

  lazy val favoritesFolder: Signal[Option[FolderData]] = favoritesFolderId.flatMap {
    case Some(folderId) => folder(folderId)
    case None           => Signal.const(None)
  }

  lazy val favoriteConversations: Signal[Seq[ConversationData]] = for {
    favId <- favoritesFolderId
    convs <- favId.fold(Signal.const(Seq.empty[ConversationData]))(folderConversations)
  } yield convs

  def folderConversations(folderId: FolderId): Signal[Seq[ConversationData]] = for {
    fwc     <- foldersWithConvs
    convIds =  fwc.getOrElse(folderId, Set.empty)
    convs   <- regularConversationListData
  } yield convs.filter(c => convIds.contains(c.id))

  private lazy val conversationsWithoutFolder: Signal[Seq[(ConversationData, Boolean)]] = for {
    customFolders      <- customFoldersWithConvs
    folderConvIds      =  customFolders.values.flatten.toSet
    convs              <- regularConversationListData
    convsWithoutFolder =  convs.filter(c => !folderConvIds.contains(c.id))
    convService        <- convService
    results            <- Signal.sequence(convsWithoutFolder.map(c => convService.groupConversation(c.id).map(b => c -> b)): _*)
  } yield results

  lazy val groupConvsWithoutFolder: Signal[Seq[ConversationData]] = conversationsWithoutFolder.map(_.filter(_._2).map(_._1))

  lazy val oneToOneConvsWithoutFolder: Signal[Seq[ConversationData]] = conversationsWithoutFolder.map(_.filter(!_._2).map(_._1))

  lazy val customFolderConversations: Signal[Seq[(FolderData, Seq[ConversationData])]] = {
    for {
      customFolderIds  <- customFolderIds
      customFoldersOpt <- Signal.sequence(customFolderIds.toSeq.map(folder): _*)
      customFolders     = customFoldersOpt.flatten
      conversations    <- Signal.sequence(customFolders.map(f => folderConversations(f.id)): _*)
      result            = customFolders.zip(conversations)
    } yield result
  }

  lazy val allFolderIds: Signal[Set[FolderId]] = foldersWithConvs.map(_.keySet)

  lazy val customFolderIds: Signal[Set[FolderId]] = for {
    favId  <- favoritesFolderId
    allIds <- allFolderIds
  } yield favId.fold(allIds)(allIds - _)

  def getCustomFolders: Future[Seq[FolderData]] = (for {
    service    <- foldersService.head
    allFolders <- service.folders
    favId      <- favoritesFolderId.head
  } yield favId.fold(allFolders)(x => allFolders.filter(f => f.id != x))
    ).recoverWith {
    case ex: Exception => error(l"exception while retrieving custom folders", ex)
    Future.failed(ex)
  }

  def addToFavorites(convId: ConvId): Future[Unit] = (for {
    service  <- foldersService.head
    favId    <- service.ensureFavoritesFolder()
    _        <- folderStateController.update(Folder.FavoritesId, isExpanded = true)
    _        <- service.addConversationTo(convId, favId, uploadAllChanges = true)
  } yield ()).recoverWith { case e: Exception =>
      error(l"exception while adding conv $convId to favorites", e)
      Future.successful({})
  }

  def removeFromFavorites(convId: ConvId): Future[Unit] = for {
    Some(favId) <- favoritesFolderId.head
    _           <- removeFromFolder(convId, favId)
  } yield ()

  def removeFromFolder(convId: ConvId, folderId: FolderId): Future[Unit] = for {
    service <- foldersService.head
    _       <- service.removeConversationFrom(convId, folderId, true)
    convs   <- service.convsInFolder(folderId)
    _       <- if (convs.isEmpty) service.removeFolder(folderId, true) else Future.successful(())
  } yield ()

  def getCustomFolderId(convId: ConvId) : Future[Option[FolderId]] = (for {
    service          <- foldersService.head
    folders          <- service.foldersForConv(convId)
    allCustomFolders <- customFolderIds.head
  } yield allCustomFolders.intersect(folders).headOption)
    .recoverWith { case ex: Exception =>
      error(l"error while retrieving custom folder id for conv $convId", ex)
      Future.failed(ex)
    }

  def moveToCustomFolder(convId: ConvId, folderId: FolderId): Future[Unit] = for {
    service      <- foldersService.head
    customFolder <- getCustomFolderId(convId)
    _            <- customFolder.fold(Future.successful(()))(removeFromFolder(convId, _))
    _            <- folderStateController.update(folderId, isExpanded = true)
    _            <- service.addConversationTo(convId, folderId, uploadAllChanges = true)
  } yield ()

  def createNewFolderWithConversation(folderName: String, convId: ConvId): Future[Unit] = (for {
    service  <- foldersService.head
    folderId <- service.addFolder(Name(folderName), uploadAllChanges = false)
    _        <- moveToCustomFolder(convId, folderId)
  } yield ()).recoverWith {
    case ex: Exception => error(l"error while creating custom folder $folderName for conv $convId", ex)
      Future.failed(ex)
  }

  def deleteConversation(teamId: TeamId, convId: ConvId): Future[Unit] = {
    val result = for {
      contUpdater <- inject[Signal[ConversationsContentUpdater]].head
      convOpt     <- contUpdater.convById(convId)
      service     <- inject[Signal[TeamsService]].head
    } yield convOpt match {
      case Some(conv) => service.deleteGroupConversation(teamId, conv.remoteId).map(_ => ())
      case None       => Future.successful(())
    }

    result.flatten.recoverWith { case e: Exception =>
        error(l"Error while deleting group conversation", e)
        Future.successful(())
    }
  }
}

object ConversationListController {

  type Filter = ConversationData => Boolean

  val ignoredConvTypes = Set(Self, Unknown)

  trait ListMode {
    val nameId: Int
    val filter: Filter
    val sort: Ordering[ConversationData] = ConversationData.ConversationDataOrdering
  }

  case object Normal extends ListMode {
    override lazy val nameId: Int = R.string.conversation_list__header__title
    override val filter: Filter = ConversationListController.RegularListFilter
  }

  case object Archive extends ListMode {
    override lazy val nameId: Int = R.string.conversation_list__header__archive_title
    override val filter: Filter = ConversationListController.ArchivedListFilter
  }

  case object Incoming extends ListMode {
    override lazy val nameId: Int = R.string.conversation_list__header__archive_title
    override val filter: Filter = ConversationListController.IncomingListFilter
  }

  case object Folders extends ListMode {
    override lazy val nameId: Int = R.string.conversation_list__header__folders_title
    override val filter: Filter = ConversationListController.RegularListFilter
  }

  lazy val RegularListFilter: Filter = { c =>
    import ConversationType._
    Set(OneToOne, Group, WaitForConnection).contains(c.convType) && !c.hidden && !c.archived
  }

  lazy val IncomingListFilter: Filter = { c =>
    !c.hidden && !c.archived && c.convType == ConversationType.Incoming
  }

  lazy val ArchivedListFilter: Filter = { c =>
    import ConversationType._
    val validConversationTypes = Set(OneToOne, Group, ConversationType.Incoming, WaitForConnection)
    validConversationTypes.contains(c.convType) && !c.hidden && c.archived && !c.completelyCleared
  }

  lazy val EstablishedListFilter: Filter = { c =>
    RegularListFilter(c) && c.convType != ConversationType.WaitForConnection
  }

  lazy val EstablishedArchivedListFilter: Filter = { c =>
    ArchivedListFilter(c) && c.convType != ConversationType.WaitForConnection
  }

  // Maintains a short list of members for each conversation.
  // Only keeps up to 4 users other than self user, this list is to be used for avatar in conv list.
  // We keep this always in memory to avoid reloading members list for every list row view (caused performance issues)
  class MembersCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext) extends Injectable {
    private implicit val dispatcher = new SerialDispatchQueue(name = "MembersCache")

    private def entries(convMembers: Seq[ConversationMemberData]) =
      convMembers.groupBy(_.convId).map { case (convId, ms) =>
        val otherUsers = ms.collect { case member if member.userId != zms.selfUserId => member.userId }
        convId -> otherUsers.sortBy(_.str).take(4)
      }

    val updatedEntries = EventStream.union(
      zms.membersStorage.onAdded.map(_.map(_.convId).toSet),
      zms.membersStorage.onDeleted.map(_.map(_._2).toSet)
    ) mapAsync { convs =>
      zms.membersStorage.getByConvs(convs) map entries
    }

    val members = new AggregatingSignal[Map[ConvId, Seq[UserId]], Map[ConvId, Seq[UserId]]](updatedEntries, zms.membersStorage.list() map entries, _ ++ _)

    def apply(conv: ConvId) : Signal[Seq[UserId]] = members.map(_.getOrElse(conv, Seq.empty[UserId]))
  }

  case class LastMsgs(lastMsg: Option[MessageData], lastMissedCall: Option[MessageData])

  // Keeps last message and missed call for each conversation, this is needed because MessagesStorage is not
  // supposed to be used for multiple conversations at the same time, as it loads an index of all conv messages.
  // Using MessagesStorage with multiple/all conversations forces it to reload full msgs index on every conv switch.
  class LastMessageCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext)
    extends Injectable with DerivedLogTag {

    private implicit val executionContext: ExecutionContext = Threading.Background

    private val cache = new mutable.HashMap[ConvId, Signal[Option[MessageData]]]

    private val lastReadCache = new mutable.HashMap[ConvId, Signal[Option[RemoteInstant]]]

    private val changeEvents = zms.messagesStorage.onChanged.map(_.groupBy(_.convId).mapValues(_.maxBy(_.time)))

    private val convLastReadChangeEvents = zms.convsStorage.onChanged.map(_.groupBy(_.id).mapValues(_.map(_.lastRead).head))

    private val missedCallEvents = zms.messagesStorage.onChanged.map(_.filter(_.msgType == Message.Type.MISSED_CALL).groupBy(_.convId).mapValues(_.maxBy(_.time)))

    private def messageUpdateEvents(conv: ConvId) = changeEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def lastReadUpdateEvents(conv: ConvId) = convLastReadChangeEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def missedCallUpdateEvents(conv: ConvId) = missedCallEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def lastMessage(conv: ConvId) = zms.storage.db.read(MessageData.MessageDataDao.last(conv)(_))

    private def lastRead(conv: ConvId) = zms.storage.db.read(ConversationData.ConversationDataDao.getById(conv)(_).map(_.lastRead))

    private def lastUnreadMissedCall(conv: ConvId): Future[Option[MessageData]] =
      for {
        lastRead <- lastReadSignal(conv).head
        missed <-
          zms.storage.db.read { MessageData.MessageDataDao.findByType(conv, Message.Type.MISSED_CALL)(_).acquire { msgs =>
              lastRead.flatMap(i => msgs.toSeq.find(_.time.isAfter(i)))
            }
          }
      } yield missed

    def apply(conv: ConvId): Signal[LastMsgs] =
      Signal(lastMessageSignal(conv), lastMissedCallSignal(conv)).map(LastMsgs.tupled)

    private def lastMessageSignal(conv: ConvId): Signal[Option[MessageData]] = cache.getOrElseUpdate(conv,
      new AggregatingSignal[MessageData, Option[MessageData]](messageUpdateEvents(conv), lastMessage(conv), {
        case (res @ Some(last), update) if last.time.isAfter(update.time) => res
        case (_, update) => Some(update)
      }))

    private def lastReadSignal(conv: ConvId): Signal[Option[RemoteInstant]] = lastReadCache.getOrElseUpdate(conv,
      new AggregatingSignal[RemoteInstant, Option[RemoteInstant]](lastReadUpdateEvents(conv), lastRead(conv), {
        case (res @ Some(last), update) if last.isAfter(update) => res
        case (_, update) => Some(update)
      }))

    private def lastMissedCallSignal(conv: ConvId): Signal[Option[MessageData]] =
      new AggregatingSignal[MessageData, Option[MessageData]](missedCallUpdateEvents(conv), lastUnreadMissedCall(conv), {
        case (res @ Some(last), update) if last.time.isAfter(update.time) => res
        case (_, update) => Some(update)
      })
  }

}
