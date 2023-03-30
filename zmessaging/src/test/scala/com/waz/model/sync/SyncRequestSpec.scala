package com.waz.model.sync

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.model.sync.SyncRequest._
import com.waz.model.sync._
import com.waz.model.{ConversationRole, ConvId, QualifiedId, UserId}
import org.json.JSONObject

class SyncRequestSpec extends AndroidFreeSpec {

  private val testUserId = "asd"
  private val testDomain = "123"
  private val testSetOfIds = Set[QualifiedId](QualifiedId(UserId(testUserId), testDomain))

  feature("SyncClientsBatch (de)serialization") {

    val sampleJson = s"""{"cmd":"sync-user-clients-batch","qualified_ids":[{"id":"$testUserId","domain":"$testDomain"}]}"""

    scenario("Serializing a request") {
      SyncRequest.Encoder(SyncClientsBatch(testSetOfIds)).toString shouldEqual sampleJson
    }

    scenario("Deserializing a request") {
      val jsonObject = new JSONObject(sampleJson)

      SyncRequest.Decoder(jsonObject) shouldEqual SyncClientsBatch(testSetOfIds)
    }

    scenario("Serializing and Deserializing should match original value") {
      val request = SyncClientsBatch(testSetOfIds)
      val serialized = SyncRequest.Encoder(request).toString

      val deserialized = SyncRequest.Decoder(new JSONObject(serialized))

      deserialized shouldEqual request
    }
  }

  feature("SyncQualifiedUsers (de)serialization") {

    val sampleJson = s"""{"cmd":"sync-qualified-users","qualified_ids":[{"id":"$testUserId","domain":"$testDomain"}]}"""

    scenario("Serializing a request") {
      SyncRequest.Encoder(SyncQualifiedUsers(testSetOfIds)).toString shouldEqual sampleJson
    }

    scenario("Deserializing a request") {
      val jsonObject = new JSONObject(sampleJson)

      SyncRequest.Decoder(jsonObject) shouldEqual SyncQualifiedUsers(testSetOfIds)
    }

    scenario("Serializing and Deserializing should match original value") {
      val request = SyncQualifiedUsers(testSetOfIds)
      val serialized = SyncRequest.Encoder(request).toString

      val deserialized = SyncRequest.Decoder(new JSONObject(serialized))

      deserialized shouldEqual request
    }
  }

  feature("SyncQualifiedSearchResults (de)serialization") {

    val sampleJson = s"""{"cmd":"sync-qualified-search-results","qualified_ids":[{"id":"$testUserId","domain":"$testDomain"}]}"""

    scenario("Serializing a request") {
      SyncRequest.Encoder(SyncQualifiedSearchResults(testSetOfIds)).toString shouldEqual sampleJson
    }

    scenario("Deserializing a request") {
      val jsonObject = new JSONObject(sampleJson)

      SyncRequest.Decoder(jsonObject) shouldEqual SyncQualifiedSearchResults(testSetOfIds)
    }

    scenario("Serializing and Deserializing should match original value") {
      val request = SyncQualifiedSearchResults(testSetOfIds)
      val serialized = SyncRequest.Encoder(request).toString

      val deserialized = SyncRequest.Decoder(new JSONObject(serialized))

      deserialized shouldEqual request
    }
  }

  feature("PostQualifiedConvJoin (de)serialization") {

    val testConversationRole = ConversationRole.AdminRole
    val testConvId = ConvId("123")

    val sampleJson =
      s"""{"cmd":"post-qualified-conv-join","conv":"${testConvId.str}","users":[{"id":"$testUserId","domain":"$testDomain"}],"conversation_role":"${testConversationRole.label}"}"""

    scenario("Serializing a request") {
      SyncRequest.Encoder(PostQualifiedConvJoin(testConvId, testSetOfIds, testConversationRole)).toString shouldEqual sampleJson
    }

    scenario("Deserializing a request") {
      val jsonObject = new JSONObject(sampleJson)

      SyncRequest.Decoder(jsonObject) shouldEqual PostQualifiedConvJoin(testConvId, testSetOfIds, testConversationRole)
    }

    scenario("Serializing and Deserializing should match original value") {
      val request = PostQualifiedConvJoin(testConvId, testSetOfIds, testConversationRole)
      val serialized = SyncRequest.Encoder(request).toString

      val deserialized = SyncRequest.Decoder(new JSONObject(serialized))

      deserialized shouldEqual request
    }
  }
}
