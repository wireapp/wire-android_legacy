package com.waz.sync.client

import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.UserSearchClient._
import UserSearchResponse._
import com.waz.model.{QualifiedId, UserId}
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
          |     "qualified_id": {
          |       "id": "d9700541-9b05-47b5-b85f-4a195593af71",
          |       "domain":"staging.zinfra.io"
          |     },
          |     "team": "399a5fd1-9a2b-4339-a005-09518baba91b"
          |   },
          |   {
          |     "name": "MA",
          |     "qualified_id": {
          |       "id": "b8864084-454c-4458-8e81-caf3a32780a7",
          |       "domain":"staging.zinfra.io"
          |     },
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
      val user1 = User(
        qualified_id = QualifiedId(UserId("d9700541-9b05-47b5-b85f-4a195593af71"), "staging.zinfra.io"),
        name = "ma",
        handle = Some("ma75"),
        accent_id = None,
        team = Some("399a5fd1-9a2b-4339-a005-09518baba91b"),
        assets = None
      )

      val user2 = User(
        qualified_id = QualifiedId(UserId("b8864084-454c-4458-8e81-caf3a32780a7"), "staging.zinfra.io"),
        name = "MA",
        handle = None,
        accent_id = Some(0),
        team = None,
        assets = None
      )

      val documents = Seq(user1, user2)

      result.isRight shouldBe true

      val searchResult = result.right.get
      searchResult shouldEqual UserSearchResponse(took = 13, found = 2, returned = 2, documents = documents)
    }

    scenario("Exact handle response decoding") {
      // Given
      val response =
        """
          |{
          |  "qualified_id": { "id": "d3410a8f-8903-4e2c-93e3-4fa039410e4f", "domain": "staging.zinfra.io" },
          |  "assets": [
          |    {
          |      "key": "3-2-78d7e7a8-357b-47bd-be69-c3be68056c9b",
          |      "size": "preview",
          |      "type": "image"
          |    }
          |  ],
          |  "team": "399a5fd1-9a2b-4339-a005-09518baba91b",
          |  "name": "aaa",
          |  "accent_id": 2,
          |  "handle": "aaa"
          |}
        """.stripMargin

      // When
      val result = decode[User](response)

      // Then
      val asset = Asset(
        key = "3-2-78d7e7a8-357b-47bd-be69-c3be68056c9b",
        size = "preview",
        `type` = "image"
      )

      val user = User(
        qualified_id = QualifiedId(UserId("d3410a8f-8903-4e2c-93e3-4fa039410e4f"), "staging.zinfra.io"),
        name = "aaa",
        handle = Some("aaa"),
        accent_id = Some(2),
        team = Some("399a5fd1-9a2b-4339-a005-09518baba91b"),
        assets = Some(Seq(asset))
      )

      result.isRight shouldBe true

      val searchResult = result.right.get
      searchResult shouldEqual user
    }
  }

}
