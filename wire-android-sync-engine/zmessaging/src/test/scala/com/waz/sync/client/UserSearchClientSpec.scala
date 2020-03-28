package com.waz.sync.client

import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.UserSearchClient.UserSearchResponse
import com.waz.utils.CirceJSONSupport

class UserSearchClientSpec extends AndroidFreeSpec with CirceJSONSupport {

  feature("User search response decoding") {

    import io.circe.parser._

    scenario("Contact response decoding") {
      // Given
      val response =
        """
          |{
          | "documents": [
          |   {
          |     "handle": "ma75",
          |     "name": "ma",
          |     "id": "d9700541-9b05-47b5-b85f-4a195593af71",
          |     "team_id": "399a5fd1-9a2b-4339-a005-09518baba91b"
          |   },
          |   {
          |     "name": "MA",
          |     "id": "b8864084-454c-4458-8e81-caf3a32780a7",
          |     "accent_id": 0
          |   }
          | ],
          | "found": 2,
          | "returned": 2,
          | "took": 13
          |}
        """.stripMargin

      // When
      val result = decode[UserSearchResponse](response)

      // Then
      val user1 = UserSearchResponse.User(id = "d9700541-9b05-47b5-b85f-4a195593af71",
                                          name = "ma",
                                          handle = Some("ma75"),
                                          accent_id = None,
                                          team_id = Some("399a5fd1-9a2b-4339-a005-09518baba91b"))

      val user2 = UserSearchResponse.User(id = "b8864084-454c-4458-8e81-caf3a32780a7",
                                          name = "MA",
                                          handle = None,
                                          accent_id = Some(0),
                                          team_id = None)

      val documents = Seq(user1, user2)

      result.isRight shouldBe true

      val searchResult = result.right.get
      searchResult shouldEqual UserSearchResponse(took = 13, found = 2, returned = 2, documents = documents)
    }

  }

}
