package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.model.{QualifiedId, UserId}
import com.waz.service.{SearchQuery, UserSearchService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.UserSearchClient
import com.waz.sync.client.UserSearchClient.UserSearchResponse
import com.waz.sync.client.UserSearchClient.UserSearchResponse.User
import com.wire.signals.CancellableFuture

import scala.concurrent.Future

class UserSearchSyncHandlerSpec extends AndroidFreeSpec {

  private val userSearch       = mock[UserSearchService]
  private val userSearchClient = mock[UserSearchClient]

  private val dummyUser = User(
    qualified_id = QualifiedId(UserId("d9700541-9b05-47b5-b85f-4a195593af71"), "staging.zinfra.io"),
    name = "ma",
    handle = Some("ma75"),
    accent_id = None,
    team = Some("399a5fd1-9a2b-4339-a005-09518baba91b"),
    assets = None
  )

  feature("Sync Search Query request") {

    scenario("Given search query is synced, when contacts request is successful, then update search results") {
      val searchQuery = SearchQuery("Test query", "", handleOnly = false)
      val documents = Seq(dummyUser)
      val results = UserSearchResponse(took = 13, found = 2, returned = 2, documents = documents)

      (userSearchClient.search(_: SearchQuery, _: Int)).expects(searchQuery, *).once().returning(CancellableFuture.successful(Right(results)))
      (userSearch.updateSearchResults(_: UserSearchClient.UserSearchResponse)).expects(results).once().returning(Future.successful(Unit))

      result(initHandler().syncSearchQuery(searchQuery)) shouldEqual SyncResult.Success

    }

    scenario("Given search query is synced, when contacts request fails, then return SyncResult.Failure") {

      val timeoutError = ErrorResponse(ErrorResponse.ConnectionErrorCode, s"Request failed with timeout", "connection-error")

      val searchQuery = SearchQuery("Test query", "", handleOnly = false)

      (userSearchClient.search(_: SearchQuery, _: Int)).expects(searchQuery, *).once().returning(CancellableFuture.successful(Left(timeoutError)))

      result(initHandler().syncSearchQuery(searchQuery)) shouldEqual SyncResult(timeoutError)
    }

  }

  def initHandler() = new UserSearchSyncHandler(userSearch, userSearchClient)

}
