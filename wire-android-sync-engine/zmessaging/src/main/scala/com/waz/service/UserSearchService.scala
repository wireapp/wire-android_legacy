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
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.model.UserPermissions.{ExternalPermissions, decodeBitmask}
import com.waz.model._
import com.waz.service.conversation.{ConversationsService, ConversationsUiService}
import com.waz.service.teams.TeamsService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.UserSearchClient.UserSearchResponse
import com.waz.threading.Threading
import com.waz.utils.ContentChange.{Added, Removed, Updated}
import com.waz.utils._
import com.waz.utils.events._

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

  private val exactMatchUser = new SourceSignal[Option[UserData]]()

  private lazy val isExternal = userPrefs(SelfPermissions).apply()
    .map(decodeBitmask)
    .map(_ == ExternalPermissions)

  private def filterForExternal(query: SearchQuery, searchResults: IndexedSeq[UserData]): Future[IndexedSeq[UserData]] = {
    lazy val knownUsers = membersStorage.getByUsers(searchResults.map(_.id).toSet).map(_.map(_.userId).toSet)
    isExternal.flatMap {
      case true if teamId.isDefined =>
        verbose(l"filterForExternal1 Q: $query, RES: ${searchResults.map(_.getDisplayName)}) with partner = true and teamId")
        for {
          Some(self)    <- userService.getSelfUser
          filteredUsers <- knownUsers.map(knownUsersIds =>
                             searchResults.filter(u => self.createdBy.contains(u.id) || knownUsersIds.contains(u.id))
                           )
        } yield filteredUsers
      case false if teamId.isDefined =>
        verbose(l"filterForExternal2 Q: $query, RES: ${searchResults.map(_.getDisplayName)}) with partner = false and teamId")
        knownUsers.map { knownUsersIds =>
          searchResults.filter { u =>
            u.createdBy.contains(selfUserId) ||
            knownUsersIds.contains(u.id) ||
              u.teamId != teamId ||
              (u.teamId == teamId && !u.isExternal(teamId)) ||
              u.handle.exists(_.exactMatchQuery(query.str))
          }
        }
      case _ => Future.successful(searchResults)
    }
  }

  // a utility method for using `filterForExternal` with signals more easily
  private def filterForExternal(query: SearchQuery, searchResults: Signal[IndexedSeq[UserData]]): Signal[IndexedSeq[UserData]] =
    searchResults.flatMap(res => Signal.future(filterForExternal(query, res)))

  def usersForNewConversation(query: SearchQuery, teamOnly: Boolean): Signal[IndexedSeq[UserData]] =
    filterForExternal(
      query,
      searchLocal(query).map(_.filter(u => !(u.isGuest(teamId) && teamOnly)))
    )

  def usersToAddToConversation(query: SearchQuery, toConv: ConvId): Signal[IndexedSeq[UserData]] =
    for {
      curr <- membersStorage.activeMembers(toConv)
      conv <- convsStorage.signal(toConv)
      res  <- filterForExternal(query, searchLocal(query, curr).map(_.filter(conv.isUserAllowed)))
    } yield res

  def mentionsSearchUsersInConversation(convId: ConvId, filter: String, includeSelf: Boolean = false): Signal[IndexedSeq[UserData]] =
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

  private def searchLocal(query: SearchQuery, excluded: Set[UserId] = Set.empty, showBlockedUsers: Boolean = false): Signal[IndexedSeq[UserData]] = {
    for {
      connected <- userService.acceptedOrBlockedUsers.map(_.values)
      members   <- teamId.fold(Signal.const(Set.empty[UserData]))(_ => teamsService.searchTeamMembers(query))
    } yield {
      val included = (connected.toSet ++ members).filter { user =>
        !excluded.contains(user.id) &&
          selfUserId != user.id &&
          !user.isWireBot &&
          !user.deleted &&
          user.expiresAt.isEmpty &&
          user.matchesQuery(query) &&
          (showBlockedUsers || (user.connection != ConnectionStatus.Blocked))
      }.toIndexedSeq

      sortUsers(included, query)
    }
  }

  private def sortUsers(results: IndexedSeq[UserData], query: SearchQuery): IndexedSeq[UserData] = {
    def toLower(str: String) = Locales.transliteration.transliterate(str).trim.toLowerCase

    lazy val toLowerSymbolStripped = toLower(query.str)

    def bucket(u: UserData): Int =
      if (query.isEmpty) 0
      else if (query.handleOnly) {
        if (u.handle.exists(_.exactMatchQuery(query.str))) 0 else 1
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

  def search(queryStr: String = ""): Signal[SearchResults] = {
    verbose(l"search($queryStr)")
    val query = SearchQuery(queryStr)

    exactMatchUser ! None // reset the exact match to None on any query change

    val topUsers: Signal[IndexedSeq[UserData]] =
      if (query.isEmpty && teamId.isEmpty) topPeople.map(_.filter(!_.isWireBot)) else Signal.const(IndexedSeq.empty)

    val conversations: Signal[IndexedSeq[ConversationData]] =
      if (!query.isEmpty)
        Signal.future(convsStorage.findGroupConversations(SearchKey(query.str), selfUserId, Int.MaxValue, handleOnly = query.handleOnly))
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
        dir   <- if (!query.isEmpty) {
                   searchUserData(query).map(_.filter(u => !u.isWireBot && u.expiresAt.isEmpty)).map(sortUsers(_, query))
                 } else Signal.const(IndexedSeq.empty[UserData])
        _     =  verbose(l"directory search results: $dir")
        exact <- exactMatchUser.orElse(Signal.const(None))
        _     =  verbose(l"exact match: $exact")
      } yield
        (dir, exact) match {
          case (_, None)           => dir
          case (results, Some(ex)) => (results.toSet ++ Set(ex)).toIndexedSeq
        }

    for {
      top        <- topUsers
      local      <- filterForExternal(query, searchLocal(query, showBlockedUsers = true))
      convs      <- conversations
      isExternal <- Signal.future(isExternal)
      dir        <- filterForExternal(query, if (isExternal) Signal.const(IndexedSeq.empty[UserData]) else directorySearch)
      _ = verbose(l"dir results: $dir")
    } yield SearchResults(top, local, convs, dir)
  }

  def updateSearchResults(query: SearchQuery, results: UserSearchResponse): Future[Unit] = {
    val users = unapply(results)

    verbose(l"updateSearchResults($query), users: ${users.map(u => (u.name, u.handle))}")

    if (!users.map(_.handle).exists(_.exactMatchQuery(query.str))) {
      sync.exactMatchHandle(Handle(query.str))
    }

    for {
      updated <- userService.updateUsers(users)
      _       <- userService.syncIfNeeded(updated.map(_.id), Duration.Zero)
    } yield ()
  }

  def updateExactMatch(userId: UserId): Future[Unit] = {
    verbose(l"updateExactMatch($userId)")

    usersStorage.get(userId).collect {
      case Some(user) => verbose(l"exact match found: $user"); exactMatchUser ! Some(user)
    }.map(_ => ())
  }

  // not private for tests
  def searchUserData(query: SearchQuery): Signal[IndexedSeq[UserData]] = {
    verbose(l"searchUserData($query)")
    sync.syncSearchQuery(query)
    val changesStream = EventStream.union[Seq[ContentChange[UserId, UserData]]](
      usersStorage.onAdded.map(_.map(d => Added(d.id, d))),
      usersStorage.onUpdated.map(_.map { case (prv, curr) => Updated(prv.id, prv, curr) }),
      usersStorage.onDeleted.map(_.map(Removed(_)))
    )

    def load = localSearch(query).flatMap(filterForExternal(query, _))

    new AggregatingSignal[Seq[ContentChange[UserId, UserData]], IndexedSeq[UserData]](changesStream, load, { (current, changes) =>
      val added = changes.collect {
        case Added(_, data)      => data
        case Updated(_, _, data) => data
      }.toSet

      val removed = changes.collect {
        case Removed(id)       => id
        case Updated(id, _, _) => id
      }.toSet

      current.filterNot(d => removed.contains(d.id) || added.exists(_.id == d.id)) ++ added
    })
  }

  private def localSearch(query: SearchQuery) = {
    val predicate = if (query.handleOnly) recommendedHandlePredicate(query.str) else recommendedPredicate(query.str)
    usersStorage.find[UserData, Vector[UserData]](predicate, db => UserDataDao.recommendedPeople(query.str)(db), identity)
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

  val MinCommonConnections = 4
  val MaxTopPeople = 10

  /**
    * Model object extracted from `UserSearchResponse`.
    */
  case class UserSearchEntry(id: UserId, name: Name, colorId: Option[Int], handle: Handle)

  object UserSearchEntry {
    def apply(searchUser: UserSearchResponse.User): UserSearchEntry = {
      import searchUser._
      UserSearchEntry(UserId(id), Name(name), accent_id, handle.fold(Handle.Empty)(Handle(_)))
    }
  }

  /**
    * Extracts `UserSearchEntry` objects contained within the given search response.
    */
  def unapply(response: UserSearchResponse): Seq[UserSearchEntry] = {
    response.documents.map(UserSearchEntry.apply)
  }
}
