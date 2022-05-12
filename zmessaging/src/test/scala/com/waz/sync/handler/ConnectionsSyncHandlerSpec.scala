package com.waz.sync.handler

import com.waz.api.ErrorType
import com.waz.api.impl.ErrorResponse
import com.waz.content.UsersStorage
import com.waz.model.{ErrorData, Name, UserId}
import com.waz.service.{ConnectionService, ErrorsService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConnectionsClient
import com.wire.signals.CancellableFuture

import scala.concurrent.Future

class ConnectionsSyncHandlerSpec extends AndroidFreeSpec {
  private val userStorage = mock[UsersStorage]
  private val connectionService = mock[ConnectionService]
  private val connectionsClient = mock[ConnectionsClient]
  private val errorsService = mock[ErrorsService]

  private def createHandler() =
    new ConnectionsSyncHandler(
      userStorage,
      connectionService,
      connectionsClient,
      errorsService
    )

  scenario("It reports error when connecting to user with missing consent") {
    // Given
    val handler = createHandler()
    val userId = UserId("userId")
    val errorResponse = ErrorResponse(412, "", "missing-legalhold-consent")

    // Mocks
    (connectionsClient.createConnection(_: UserId, _: Name, _: String))
        .expects(userId, *, *)
        .once()
        .returning(CancellableFuture.successful(Left(errorResponse)))

    // Expectation
    (errorsService.addErrorWhenActive _)
        .expects(where { data: ErrorData =>
          data.errType == ErrorType.CANNOT_CONNECT_USER_WITH_MISSING_LEGAL_HOLD_CONSENT &&
          data.responseCode == errorResponse.code &&
          data.responseLabel == errorResponse.label
        })
        .once()
        .returning(Future.successful(()))

    // When
    result(handler.postConnection(userId, Name("alice"), ""))
  }

}
