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
import com.waz.log.LogSE.{info, _}
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.model.UserPermissions.{ExternalPermissions, decodeBitmask}
import com.waz.model._
import com.waz.service.conversation.{ConversationsService, ConversationsUiService}
import com.waz.service.teams.TeamsService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.UserSearchClient.UserSearchResponse
import com.waz.threading.Threading
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

trait UserSearchService {
  def usersForNewConversation(query: SearchQuery, teamOnly: Boolean): Signal[SearchResults]
  def usersToAddToConversation(query: SearchQuery, toConv: ConvId): Signal[SearchResults]
  def mentionsSearchUsersInConversation(convId: ConvId, filter: String, includeSelf: Boolean = false): Signal[IndexedSeq[UserData]]
  def search(queryStr: String = ""): Signal[SearchResults]
  def syncSearchResults(query: SearchQuery): Unit
  def updateSearchResults(query: SearchQuery, results: UserSearchResponse): Unit
  def updateSearchResults(info: Seq[UserInfo]): Unit
  def updateExactMatch(result: UserSearchResponse.User): Unit
}

class UserSearchServiceImpl(selfUserId:           UserId,
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
                            userPrefs:            UserPreferences) extends UserSearchService with DerivedLogTag {

  import Threading.Implicits.Background
  import com.waz.service.UserSearchService._
  import timeouts.search._

  private val exactMatchUser = Signal(Option.empty[UserData])
  private val userSearchResult = Signal(IndexedSeq.empty[UserData])

  private lazy val isExternal = userPrefs(SelfPermissions).apply()
    .map(decodeBitmask)
    .map(_ == ExternalPermissions)

  private def filterForExternal(query: SearchQuery, searchResults: IndexedSeq[UserData]): Future[IndexedSeq[UserData]] = {
    lazy val knownUsers = membersStorage.getByUsers(searchResults.map(_.id).toSet).map(_.map(_.userId).toSet)
    isExternal.flatMap {
      case true if teamId.isDefined =>
        verbose(l"filterForExternal1 Q: $query, RES: ${searchResults.map(_.name)}) with partner = true and teamId")
        for {
          Some(self)    <- userService.getSelfUser
          filteredUsers <- knownUsers.map(knownUsersIds =>
                             searchResults.filter(u => self.createdBy.contains(u.id) || knownUsersIds.contains(u.id))
                           )
        } yield filteredUsers
      case false if teamId.isDefined =>
        verbose(l"filterForExternal2 Q: $query, RES: ${searchResults.map(_.name)}) with partner = false and teamId")
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

  override def usersForNewConversation(query: SearchQuery, teamOnly: Boolean): Signal[SearchResults] =
    for {
      localResults      <- filterForExternal(query, searchLocal(query)
                          .map(_.filter(u => !(u.isGuest(teamId) && teamOnly))))
      remoteResults     <- directoryResults(query)
    } yield SearchResults(local = localResults, dir = remoteResults)

  override def usersToAddToConversation(query: SearchQuery, toConv: ConvId): Signal[SearchResults] =
    for {
      curr              <- membersStorage.activeMembers(toConv)
      conv              <- convsStorage.signal(toConv)
      localResults      <- filterForExternal(query, searchLocal(query, curr).map(_.filter(conv.isUserAllowed)))
      remoteResults     <- directoryResults(query)
    } yield SearchResults(local = localResults, dir = remoteResults)

  override def mentionsSearchUsersInConversation(convId: ConvId, filter: String, includeSelf: Boolean = false): Signal[IndexedSeq[UserData]] =
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
        _.name.toLowerCase.startsWith(filter),
        _.searchKey.asciiRepresentation.split(" ").exists(_.startsWith(filter)),
        cmpHandle(_, _.startsWith(filter)),
        _.name.toLowerCase.contains(filter),
        cmpHandle(_, _.contains(filter))
      )

      rules.foldLeft((Set.empty[UserId], IndexedSeq.empty[UserData])){ case ((found, results), rule) =>
        val matches = included.filter(rule).filter(u => !found.contains(u.id)).sortBy(_.name.toLowerCase)
        (found ++ matches.map(_.id).toSet, results ++ matches)
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
        val userName = toLower(u.name)
        if (userName == toLowerSymbolStripped) 0 else if (userName.startsWith(toLowerSymbolStripped)) 1 else 2
      }

    results.sortWith { case (u1, u2) =>
        val b1 = bucket(u1)
        val b2 = bucket(u2)
        if (b1 == b2)
          u1.name.compareTo(u2.name) < 0
        else
          b1 < b2
    }
  }

  override def search(queryStr: String = ""): Signal[SearchResults] = {
    verbose(l"search($queryStr)")
    val query = SearchQuery(queryStr)

    userSearchResult ! IndexedSeq.empty[UserData]
    exactMatchUser ! None // reset the exact match to None on any query change

    syncSearchResults(query)

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

    val directorySearch = directoryResults(query)

    for {
      top        <- topUsers
      local      <- filterForExternal(query, searchLocal(query, showBlockedUsers = true))
      convs      <- conversations
      isExternal <- Signal.future(isExternal)
      dir        <- filterForExternal(query, if (isExternal) Signal.const(IndexedSeq.empty[UserData]) else directorySearch)
    } yield SearchResults(top, local, convs, dir)
  }

  def syncSearchResults(query: SearchQuery): Unit = {
    if (!query.isEmpty) {
      sync.syncSearchQuery(query)
    }
  }

  private def directoryResults(query: SearchQuery): Signal[IndexedSeq[UserData]] =
      for {
        dir   <- if (!query.isEmpty) {
          userSearchResult.map(_.filter(u => !u.isWireBot && u.expiresAt.isEmpty)).map(sortUsers(_, query))
        } else Signal.const(IndexedSeq.empty[UserData])
        _     =  verbose(l"directory search results: $dir")
        exact <- exactMatchUser.orElse(Signal.const(None))
        _     =  verbose(l"exact match: $exact")
      } yield
        (dir, exact) match {
          case (_, None)           => dir
          case (results, Some(ex)) => (results.toSet ++ Set(ex)).toIndexedSeq
        }

  override def updateSearchResults(query: SearchQuery, results: UserSearchResponse): Unit = {
    val users = unapply(results)
    userSearchResult ! users.map(UserData.apply).toIndexedSeq
    sync.syncSearchResults(users.map(_.id).toSet)
  }

  override def updateSearchResults(info: Seq[UserInfo]): Unit = {
    userSearchResult.mutate(_.map(user => info.find(_.id == user.id).fold(user)(user.updated)))
    exactMatchUser.mutate(_.map(user => info.find(_.id == user.id).fold(user)(user.updated)))
  }

  override def updateExactMatch(result: UserSearchResponse.User): Unit = {
    verbose(l"updateExactMatch(${result.id})")
    val userData = UserData(UserSearchEntry(result))
    exactMatchUser ! Some(userData)
    sync.syncSearchResults(Set(userData.id))
  }

  private def topPeople = {
    def messageCount(u: UserData) =
      messages.countLaterThan(ConvId(u.id.str), LocalInstant.Now.toRemote(Duration.Zero) - topPeopleMessageInterval)

    val loadTopUsers = (for {
      conns         <- usersStorage.find[UserData, Vector[UserData]](topPeoplePredicate, db => UserDataDao.topPeople(db), identity)
      messageCounts <- Future.sequence(conns.map(messageCount))
    } yield conns.zip(messageCounts)).map { counts =>
      counts.filter(_._2 > 0).sortBy(_._2)(Ordering[Long].reverse).take(MaxTopPeople).map(_._1)
    }

    Signal.future(loadTopUsers).map(_.toIndexedSeq)
  }

  private val topPeoplePredicate: UserData => Boolean = u => ! u.deleted && u.connection == ConnectionStatus.Accepted
}

object UserSearchService {

  val MinCommonConnections = 4
  val MaxTopPeople = 10

  /**
    * Model object extracted from `UserSearchResponse`.
    */
  case class UserSearchEntry(id: UserId, name: Name, colorId: Option[Int], handle: Handle, teamId: Option[TeamId])

  object UserSearchEntry {
    def apply(searchUser: UserSearchResponse.User): UserSearchEntry = {
      import searchUser._
      UserSearchEntry(UserId(id),
                      Name(name),
                      accent_id,
                      handle.fold(Handle.Empty)(Handle(_)),
                      team.map(TeamId.apply))
    }
  }

  /**
    * Extracts `UserSearchEntry` objects contained within the given search response.
    */
  def unapply(response: UserSearchResponse): Seq[UserSearchEntry] = {
    response.documents.map(UserSearchEntry.apply)
  }
}
