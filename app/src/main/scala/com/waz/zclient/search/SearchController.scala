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
package com.waz.zclient.search

import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.IntegrationData
import com.waz.service.{IntegrationsService, SearchQuery, SearchResults, UserSearchService}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{Injectable, Injector}

class SearchController(implicit inj: Injector, eventContext: EventContext) extends Injectable with DerivedLogTag {

  import SearchController._

  private val servicesService = inject[Signal[IntegrationsService]]
  private val searchService   = inject[Signal[UserSearchService]]

  private val createConvController = inject[CreateConversationController]

  val filter = Signal("")
  val tab    = Signal[Tab](Tab.People)

  lazy val addUserOrServices: Signal[AddUserListState] = {
    import AddUserListState._

    for {
      filter  <- filter
      tab     <- tab
      res     <- tab match {
        case Tab.People =>
          lazy val query = SearchQuery(filter)
          for {
            search      <- searchService
            _           <- Signal(search.syncSearchResults(query))
            convId      <- createConvController.convId
            teamOnly    <- createConvController.teamOnly
            results     <- convId match {
              case Some(cId) => search.usersToAddToConversation(query, cId)
              case None => search.usersForNewConversation(query, teamOnly)
            }
          } yield
            if (results.isEmpty) {
              if (filter.isEmpty) NoUsers else NoUsersFound
            } else Users(results)
        case Tab.Services =>
          servicesService.flatMap { svc =>
            Signal
              .future(svc.searchIntegrations(Option(filter).filter(_.nonEmpty)))
              .map(_.fold[AddUserListState](Error, ss =>
                if (ss.isEmpty)
                  if (filter.isEmpty) NoServices else NoServicesFound
                else Services(ss)))
              .orElse(Signal.const(LoadingServices))
          }
      }
    } yield res
  }

  lazy val searchUserOrServices: Signal[SearchUserListState] = {
    import SearchUserListState._
    for {
      filter  <- filter
      tab     <- tab
      res     <- tab match {
        case Tab.People =>
          for {
            search      <- searchService
            results     <- search.search(filter)
            _ = verbose(l"results: $results")
          } yield
            if (results.isEmpty) {
              if (filter.isEmpty) NoUsers else NoUsersFound
            } else Users(results)
        case Tab.Services =>
          servicesService.flatMap { svc =>
            Signal
              .future(svc.searchIntegrations(Option(filter).filter(_.nonEmpty)))
              .map(_.fold[SearchUserListState](Error, ss =>
                if (ss.isEmpty)
                  if (filter.isEmpty) NoServices else NoServicesFound
                else Services(ss)))
              .orElse(Signal.const(LoadingServices))
          }
      }
    } yield res
  }

}

object SearchController {

  //TODO merge these two types somehow
  sealed trait AddUserListState
  object AddUserListState {
    case object NoUsers extends AddUserListState
    case object NoUsersFound extends AddUserListState
    case class Users(us: SearchResults) extends AddUserListState

    case object NoServices extends AddUserListState
    case object NoServicesFound extends AddUserListState
    case object LoadingServices extends AddUserListState
    case class Services(ss: Seq[IntegrationData]) extends AddUserListState
    case class Error(err: ErrorResponse) extends AddUserListState
  }

  sealed trait SearchUserListState
  object SearchUserListState {
    case object NoUsers extends SearchUserListState
    case object NoUsersFound extends SearchUserListState
    case class Users(us: SearchResults) extends SearchUserListState

    case object NoServices extends SearchUserListState
    case object NoServicesFound extends SearchUserListState
    case object LoadingServices extends SearchUserListState
    case class Services(ss: Seq[IntegrationData]) extends SearchUserListState
    case class Error(err: ErrorResponse) extends SearchUserListState
  }

  sealed trait Tab

  object Tab {
    case object People extends Tab
    case object Services extends Tab
  }

}
