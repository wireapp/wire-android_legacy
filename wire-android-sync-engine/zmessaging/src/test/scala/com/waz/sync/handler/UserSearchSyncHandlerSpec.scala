package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.model.Handle
import com.waz.service.{SearchQuery, UserSearchService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.UserSearchClient
import com.waz.sync.client.UserSearchClient.UserSearchResponse
import com.waz.sync.client.UserSearchClient.UserSearchResponse.User
import com.waz.threading.CancellableFuture

import scala.concurrent.Future

class UserSearchSyncHandlerSpec extends AndroidFreeSpec {

  private val userSearch       = mock[UserSearchService]
  private val userSearchClient = mock[UserSearchClient]

  private val dummyUser = User(
    id = "d9700541-9b05-47b5-b85f-4a195593af71",
    name = "ma",
    handle = Some("ma75"),
    accent_id = None,
    team = Some("399a5fd1-9a2b-4339-a005-09518baba91b"),
    assets = None
  )

  feature("Sync Search Query request") {

    scenario("Given search query is synced, when contacts request is successful, then update search results") {
      val searchQuery = SearchQuery("Test query", handleOnly = false)
      val documents = Seq(dummyUser)
      val results = UserSearchResponse(took = 13, found = 2, returned = 2, documents = documents)

      (userSearchClient.getContacts(_: SearchQuery, _: Int)).expects(searchQuery, *).once().returning(CancellableFuture.successful(Right(results)))
      (userSearch.updateSearchResults(_: SearchQuery, _: UserSearchClient.UserSearchResponse)).expects(searchQuery, results).once().returning(Future.successful(Unit))

      result(initHandler().syncSearchQuery(searchQuery)) shouldEqual SyncResult.Success

    }

    scenario("Given search query is synced, when contacts request fails, then return SyncResult.Failure") {

      val timeoutError = ErrorResponse(ErrorResponse.ConnectionErrorCode, s"Request failed with timeout", "connection-error")

      val searchQuery = SearchQuery("Test query", handleOnly = false)

      (userSearchClient.getContacts(_: SearchQuery, _: Int)).expects(searchQuery, *).once().returning(CancellableFuture.successful(Left(timeoutError)))

      result(initHandler().syncSearchQuery(searchQuery)) shouldEqual SyncResult(timeoutError)
    }

  }

  feature("Exact Match Handle query request") {

    scenario("Given handle is queried, when exactMatchHandle is successful, then update exact matches") {
      val handle = Handle("ma75")
      (userSearchClient.exactMatchHandle(_: Handle)).expects(handle).once().returning(CancellableFuture.successful(Right(Some(dummyUser))))
      (userSearch.updateExactMatch(_: UserSearchResponse.User)).expects(dummyUser).once().returning(Future.successful(Unit))
      result(initHandler().exactMatchHandle(handle)) shouldEqual SyncResult.Success
    }

    scenario("Given handle is queried, when exactMatchHandle fails. then return SyncResult.Failure") {
      val handle = Handle("ma75")
      val timeoutError = ErrorResponse(ErrorResponse.ConnectionErrorCode, s"Request failed with timeout", "connection-error")

      (userSearchClient.exactMatchHandle(_: Handle)).expects(handle).once().returning(CancellableFuture.successful(Left(timeoutError)))
      result(initHandler().exactMatchHandle(handle)) shouldEqual SyncResult(timeoutError)
    }
  }

  def initHandler() = new UserSearchSyncHandler(userSearch, userSearchClient)

}
