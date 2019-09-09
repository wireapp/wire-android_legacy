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
package com.waz.service

import com.waz.content.UserPreferences.SelfPermissions
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.SearchQuery.{Recommended, RecommendedHandle}
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.model.UserPermissions.{PartnerPermissions, decodeBitmask}
import com.waz.model.{SearchQuery, _}
import com.waz.service.ZMessaging.clock
import com.waz.service.conversation.{ConversationsService, ConversationsUiService}
import com.waz.service.teams.TeamsService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.UserSearchClient.UserSearchResponse
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils._
import com.waz.utils.events._

import scala.collection.breakOut
import scala.collection.immutable.Set
import scala.concurrent.Future
import scala.concurrent.duration._

case class SearchResults(top:   IndexedSeq[UserData]         = IndexedSeq.empty,
                         local: IndexedSeq[UserData]         = IndexedSeq.empty,
                         convs: IndexedSeq[ConversationData] = IndexedSeq.empty,
                         dir:   IndexedSeq[UserData]         = IndexedSeq.empty) { //directory (backend search)

  def isEmpty: Boolean = top.isEmpty && local.isEmpty && convs.isEmpty && dir.isEmpty
}

class UserSearchService(selfUserId:           UserId,
                        queryCache:           SearchQueryCacheStorage,
                        teamId:               Option[TeamId],
                        userService:          UserService,
                        usersStorage:         UsersStorage,
                        teamsService:         TeamsService,
                        membersStorage:       MembersStorage,
                        timeouts:             Timeouts,
                        sync:                 SyncServiceHandle,
                        messages:             MessagesStorage,
                        convsStorage:         ConversationStorage,
                        convsUi:              ConversationsUiService,
                        conversationsService: ConversationsService,
                        userPrefs:            UserPreferences) extends DerivedLogTag {

  import Threading.Implicits.Background
  import com.waz.service.UserSearchService._
  import timeouts.search._

  ClockSignal(1.day)(i => queryCache.deleteBefore(i - cacheExpiryTime))(EventContext.Global)

  private val exactMatchUser = new SourceSignal[Option[UserData]]()

  private lazy val isPartner = userPrefs(SelfPermissions).apply()
    .map(decodeBitmask)
    .map(_ == PartnerPermissions)

  private def filterForPartner(query: Filter, searchResults: IndexedSeq[UserData]): Future[IndexedSeq[UserData]] = {
    lazy val knownUsers = membersStorage.getByUsers(searchResults.map(_.id).toSet).map(_.map(_.userId).toSet)
    isPartner.flatMap {
      case true if teamId.isDefined =>
        verbose(l"filterForPartner1 Q: ${redactedString(query)}, RES: ${searchResults.map(_.getDisplayName)}) with partner = true and teamId")
        for {
          Some(self)    <- userService.getSelfUser
          filteredUsers <- knownUsers.map(knownUsersIds =>
                             searchResults.filter(u => self.createdBy.contains(u.id) || knownUsersIds.contains(u.id))
                           )
        } yield filteredUsers
      case false if teamId.isDefined =>
        verbose(l"filterForPartner2 Q: ${redactedString(query)}, RES: ${searchResults.map(_.getDisplayName)}) with partner = false and teamId")
        knownUsers.map { knownUsersIds =>
          searchResults.filter { u =>
            u.createdBy.contains(selfUserId) ||
            knownUsersIds.contains(u.id) ||
              u.teamId != teamId ||
              (u.teamId == teamId && !u.isPartner(teamId)) ||
              u.handle.exists(_.exactMatchQuery(query))
          }
        }
      case _ => Future.successful(searchResults)
    }
  }

  // a utility method for using `filterForPartner` with signals more easily
  private def filterForPartner(query: Filter, searchResults: Signal[IndexedSeq[UserData]]): Signal[IndexedSeq[UserData]] =
    searchResults.flatMap(res => Signal.future(filterForPartner(query, res)))

  def usersForNewConversation(filter: Filter = "", teamOnly: Boolean): Signal[IndexedSeq[UserData]] =
    filterForPartner(
      filter,
      searchLocal(filter, isHandle = Handle.isHandle(filter)).map(_.filter(u => !(u.isGuest(teamId) && teamOnly)))
    )

  def usersToAddToConversation(filter: Filter = "", toConv: ConvId): Signal[IndexedSeq[UserData]] =
    for {
      curr         <- membersStorage.activeMembers(toConv)
      conv         <- convsStorage.signal(toConv)
      localResults =  searchLocal(filter, curr, isHandle = Handle.isHandle(filter)).map(_.filter(conv.isUserAllowed))
      res          <- filterForPartner(filter, localResults)
    } yield res

  def mentionsSearchUsersInConversation(convId: ConvId, filter: Filter, includeSelf: Boolean = false): Signal[IndexedSeq[UserData]] =
    for {
      curr     <- membersStorage.activeMembers(convId)
      currData <- usersStorage.listSignal(curr)
    } yield {
      val included = currData.filter { user =>
        (includeSelf || selfUserId != user.id) &&
          !user.isWireBot &&
          user.expiresAt.isEmpty &&
          user.connection != ConnectionStatus.Blocked
      }

      def cmpHandle(u: UserData, fn: String => Boolean) = u.handle match {
        case None => false
        case Some(h) => fn(h.string)
      }

      val rules: Seq[UserData => Boolean] = Seq(
        _.getDisplayName.toLowerCase.startsWith(filter),
        _.searchKey.asciiRepresentation.split(" ").exists(_.startsWith(filter)),
        cmpHandle(_, _.startsWith(filter)),
        _.getDisplayName.toLowerCase.contains(filter),
        cmpHandle(_, _.contains(filter))
      )

      rules.foldLeft[(Set[UserId],IndexedSeq[UserData])]((Set.empty, IndexedSeq())){ case ((found, results), rule) =>
        val matches = included.filter(rule).filter(u => !found.contains(u.id)).sortBy(_.getDisplayName.toLowerCase)
        (found ++ matches.map(_.id).toSet, results ++: matches)
      }._2
    }

  private def searchLocal(filter: Filter, excluded: Set[UserId] = Set.empty, showBlockedUsers: Boolean = false, isHandle: Boolean = false): Signal[IndexedSeq[UserData]] = {
    val symbolStripped = if (isHandle) Handle.stripSymbol(filter) else filter
    for {
      connected <- userService.acceptedOrBlockedUsers.map(_.values)
      members   <- teamId.fold(Signal.const(Set.empty[UserData])) { _ =>
        teamsService.searchTeamMembers(if (filter.isEmpty) None else Some(SearchKey(filter)), handleOnly = isHandle)
      }
    } yield {
      val included = (connected.toSet ++ members).filter { user =>
        !excluded.contains(user.id) &&
          selfUserId != user.id &&
          !user.isWireBot &&
          !user.deleted &&
          user.expiresAt.isEmpty &&
          user.matchesFilter(filter) &&
          (showBlockedUsers || (user.connection != ConnectionStatus.Blocked))
      }.toIndexedSeq

      sortUsers(included, filter, isHandle, symbolStripped)
    }
  }

  private def sortUsers(results: IndexedSeq[UserData], filter: Filter, isHandle: Boolean, symbolStripped: Filter): IndexedSeq[UserData] = {
    def toLower(str: String) = Locales.transliteration.transliterate(str).trim.toLowerCase

    lazy val toLowerSymbolStripped = toLower(symbolStripped)

    def bucket(u: UserData): Int =
      if (filter.isEmpty) 0
      else if (isHandle) {
        if (u.handle.exists(_.exactMatchQuery(filter))) 0 else 1
      } else {
        val userName = toLower(u.getDisplayName)
        if (userName == toLowerSymbolStripped) 0 else if (userName.startsWith(toLowerSymbolStripped)) 1 else 2
      }

    results.sortWith { case (u1, u2) =>
        val b1 = bucket(u1)
        val b2 = bucket(u2)
        if (b1 == b2)
          u1.getDisplayName.compareTo(u2.getDisplayName) < 0
        else
          b1 < b2
    }
  }

  def search(filter: Filter = ""): Signal[SearchResults] = {

    val isHandle       = Handle.isHandle(filter)
    val symbolStripped = if (isHandle) Handle.stripSymbol(filter) else filter
    val query          = if (isHandle) RecommendedHandle(filter) else Recommended(filter)

    val shouldShowTopUsers = filter.isEmpty && teamId.isEmpty

    val shouldShowGroupConversations = if (isHandle) symbolStripped.length > 1 else !filter.isEmpty
    val shouldShowDirectorySearch    = !filter.isEmpty

    exactMatchUser ! None // reset the exact match to None on any query change

    val topUsers: Signal[IndexedSeq[UserData]] =
      if (shouldShowTopUsers) topPeople.map(_.filter(!_.isWireBot)) else Signal.const(IndexedSeq.empty)

    val conversations: Signal[IndexedSeq[ConversationData]] =
      if (shouldShowGroupConversations)
        Signal.future(convsStorage.findGroupConversations(SearchKey(filter), selfUserId, Int.MaxValue, handleOnly = isHandle))
          .map(_.filter(conv => teamId.forall(conv.team.contains)).distinct.toIndexedSeq)
          .flatMap { convs =>
            val gConvs = convs.map { c =>
              conversationsService.isGroupConversation(c.id).flatMap {
                case true  => Future.successful(true)
                case false => conversationsService.isWithService(c.id)
              }.map {
                case true  => Some(c)
                case false => None
              }
            }
            Signal.future(Future.sequence(gConvs).map(_.flatten)) //TODO avoid using Signal.future - will not update...
          }
      else Signal.const(IndexedSeq.empty)

    val directorySearch: Signal[IndexedSeq[UserData]] =
      for {
        dir <-
          if (shouldShowDirectorySearch)
            searchUserData(query)
              .map(_.filter(u => !u.isWireBot && u.expiresAt.isEmpty))
              .map(sortUsers(_, filter, isHandle, symbolStripped))
          else Signal.const(IndexedSeq.empty)
        exact <- exactMatchUser
      } yield {
        (dir, exact) match {
          case (_, None) => dir
          case (IndexedSeq(), Some(ex)) => IndexedSeq(ex)
          case (results, Some(ex)) => (results.toSet ++ Set(ex)).toIndexedSeq
        }
      }

    for {
      top       <- topUsers
      local     <- filterForPartner(filter, searchLocal(filter, showBlockedUsers = true, isHandle = isHandle))
      convs     <- conversations
      isPartner <- Signal.future(isPartner)
      dir       <- filterForPartner(filter, if (isPartner) Signal.const(IndexedSeq.empty[UserData]) else directorySearch)
    } yield SearchResults(top, local, convs, dir)
  }

  def updateSearchResults(query: SearchQuery, results: UserSearchResponse) = {

    def updating(ids: Vector[UserId])(cached: SearchQueryCache) =
      cached.copy(query, clock.instant(), if (ids.nonEmpty || cached.entries.isEmpty) Some(ids) else cached.entries)

    val users = unapply(results)
    val ids = users.map(_.id)(breakOut): Vector[UserId]

    for {
      updated <- userService.updateUsers(users)
      _       <- userService.syncIfNeeded(updated.map(_.id), Duration.Zero)
      _       <- queryCache.updateOrCreate(query, updating(ids), SearchQueryCache(query, clock.instant(), Some(ids)))
    } yield ()

    if (!users.map(_.handle).exists(_.exactMatchQuery(query.filter))) {
      sync.exactMatchHandle(Handle(query.filter))
    }

    Future.successful({})
  }

  def updateExactMatch(handle: Handle, userId: UserId) = {
    val query = RecommendedHandle(handle.withSymbol)
    def updating(id: UserId)(cached: SearchQueryCache) =
      cached.copy(query, clock.instant(), Some(cached.entries.map(_.toSet ++ Set(userId)).getOrElse(Set(userId)).toVector))

    usersStorage.get(userId).collect {
      case Some(user) =>
        exactMatchUser ! Some(user)
        queryCache.updateOrCreate(query, updating(userId), SearchQueryCache(query, clock.instant(), Some(Vector(userId))))
    }

    Future.successful({})
  }

  // not private for tests
  def searchUserData(query: SearchQuery): Signal[IndexedSeq[UserData]] = returning( queryCache.optSignal(query) ){ _ =>
    localSearch(query).flatMap(_ => sync.syncSearchQuery(query))
  }.flatMap {
    case None => Signal.const(IndexedSeq.empty[UserData])
    case Some(cached) => cached.entries match {
      case None => Signal.const(IndexedSeq.empty[UserData])
      case Some(ids) if ids.isEmpty => Signal.const(IndexedSeq.empty[UserData])
      case Some(ids) =>
        usersStorage.listSignal(ids).flatMap(res => Signal.future(filterForPartner(query.filter, res)))
    }
  }

  private def localSearch(query: SearchQuery) = (query match {
    case Recommended(prefix) =>
      usersStorage.find[UserData, Vector[UserData]](recommendedPredicate(prefix), db => UserDataDao.recommendedPeople(prefix)(db), identity)
    case RecommendedHandle(prefix) =>
      usersStorage.find[UserData, Vector[UserData]](recommendedHandlePredicate(prefix), db => UserDataDao.recommendedPeople(prefix)(db), identity)
    case _ => Future.successful(Vector.empty[UserData])
  }).flatMap { users =>
    lazy val fresh = SearchQueryCache(query, clock.instant(), Some(users.map(_.id)))

    def update(q: SearchQueryCache): SearchQueryCache = if ((cacheExpiryTime elapsedSince q.timestamp) || q.entries.isEmpty) fresh else q

    queryCache.updateOrCreate(query, update, fresh)
  }

  private def topPeople = {
    def messageCount(u: UserData) = messages.countLaterThan(ConvId(u.id.str), LocalInstant.Now.toRemote(Duration.Zero) - topPeopleMessageInterval)

    val loadTopUsers = (for {
      conns         <- usersStorage.find[UserData, Vector[UserData]](topPeoplePredicate, db => UserDataDao.topPeople(db), identity)
      messageCounts <- Future.sequence(conns.map(messageCount))
    } yield conns.zip(messageCounts)).map { counts =>
      counts.filter(_._2 > 0).sortBy(_._2)(Ordering[Long].reverse).take(MaxTopPeople).map(_._1)
    }

    Signal.future(loadTopUsers).map(_.toIndexedSeq)
  }

  private val topPeoplePredicate: UserData => Boolean = u => ! u.deleted && u.connection == ConnectionStatus.Accepted

  private def recommendedPredicate(prefix: String): UserData => Boolean = {
    val key = SearchKey(prefix)
    u => ! u.deleted && ! u.isConnected && (key.isAtTheStartOfAnyWordIn(u.searchKey) || u.email.exists(_.str == prefix) || u.handle.exists(_.startsWithQuery(prefix)))
  }

  private def recommendedHandlePredicate(prefix: String): UserData => Boolean = {
    u => ! u.deleted && ! u.isConnected && u.handle.exists(_.startsWithQuery(prefix))
  }

}

object UserSearchService {
  type Filter = String

  val MinCommonConnections = 4
  val MaxTopPeople = 10

  /**
    * Model object extracted from `UserSearchResponse`.
    */
  case class UserSearchEntry(id: UserId, name: Name, colorId: Option[Int], handle: Handle)

  object UserSearchEntry {
    def apply(searchUser: UserSearchResponse.User): UserSearchEntry = {
      import searchUser._
      UserSearchEntry(UserId(id), Name(name), accent_id, Handle(handle))
    }
  }

  /**
    * Extracts `UserSearchEntry` objects contained within the given search response.
    */
  def unapply(response: UserSearchResponse): Seq[UserSearchEntry] = {
    response.documents.map(UserSearchEntry.apply)
  }
}
