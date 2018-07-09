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
package com.waz.model

import com.waz.api.EphemeralExpiration
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.db.ZMessagingDB
import com.waz.model.AddressBook.ContactHashes
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.ClientId
import com.waz.model.sync.SyncJob.SyncJobDao
import com.waz.model.sync.SyncRequest._
import com.waz.model.sync.{ReceiptType, SyncJob, SyncRequest}
import com.waz.testutils.Matchers._
import com.waz.utils.JsonDecoder._
import com.waz.utils.JsonEncoder._
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, TableDrivenPropertyChecks}
import org.scalatest._
import org.threeten.bp.Instant
import scala.concurrent.duration._

@Ignore class SyncJobDaoSpec extends FeatureSpec with Matchers with TableDrivenPropertyChecks with BeforeAndAfter with GeneratorDrivenPropertyChecks with RobolectricTests {

  import com.waz.Generators.SyncRequests._

  var dbHelper: ZMessagingDB = _

  before {
    dbHelper = new ZMessagingDB(Robolectric.application, "dbName")
  }

  after {
    dbHelper.close()
    Robolectric.application.getDatabasePath("dbName").delete()
  }

  implicit def db: DB = dbHelper.getWritableDatabase

  feature("json serialization") {
    scenario("Simple requests") {
      forAll (Table("request", SyncSelf, SyncConversations, SyncConnectedUsers)) { req: SyncRequest =>
        req should beUnchangedByEncodingAndDecoding
      }
    }

    scenario("SyncUser requests") { SyncUser(Set(UserId(), UserId(), UserId())) should beUnchangedByEncodingAndDecoding }
    scenario("SyncConversation requests") { SyncConversation(Set(ConvId(), ConvId(), ConvId())) should beUnchangedByEncodingAndDecoding }
    scenario("PostSelf requests") { PostSelf(UserInfo(UserId(), Some("name"), Some(1), Some(EmailAddress("email")), Some(PhoneNumber("phone")), None, Some(TrackingId()))) should beUnchangedByEncodingAndDecoding }
    scenario("PostConv requests") { PostConv(ConvId(), Set(UserId(), UserId()), Some("name"), Some(TeamId()), Set(Access.INVITE, Access.CODE), AccessRole.NON_ACTIVATED) should beUnchangedByEncodingAndDecoding }
    scenario("PostConvName requests") { PostConvName(ConvId(), "name") should beUnchangedByEncodingAndDecoding }
    scenario("PostConvState requests") { PostConvState(ConvId(), ConversationState(Some(false), Some(RemoteInstant(Instant.now)), Some(true), Some(RemoteInstant.Epoch))) should beUnchangedByEncodingAndDecoding }
    scenario("PostTypingState requests") { PostTypingState(ConvId(), isTyping = true) should beUnchangedByEncodingAndDecoding }
    scenario("DeletePushToken requests") { DeletePushToken(PushToken()) should beUnchangedByEncodingAndDecoding }
    scenario("SyncSearchQuery requests") { SyncSearchQuery(SearchQuery.Recommended("meep moop")) should beUnchangedByEncodingAndDecoding }
    scenario("PostMessage requests") { PostMessage(ConvId(), MessageId(), RemoteInstant(Instant.now())) should beUnchangedByEncodingAndDecoding }
    scenario("PostConvJoin requests") { PostConvJoin(ConvId(), Set(UserId(), UserId())) should beUnchangedByEncodingAndDecoding }
    scenario("PostConvLeave requests") { PostConvLeave(ConvId(), UserId()) should beUnchangedByEncodingAndDecoding }
    scenario("PostConnection requests") { PostConnection(UserId(), "name", "message") should beUnchangedByEncodingAndDecoding }
    scenario("PostConnectionStatus requests") { PostConnectionStatus(UserId(), Some(ConnectionStatus.Blocked)) should beUnchangedByEncodingAndDecoding }
    scenario("SyncRichMedia requests") { SyncRichMedia(MessageId()) should beUnchangedByEncodingAndDecoding }
    scenario("PostAddressBook requests") { PostAddressBook(AddressBook(Seq("test"), Seq(ContactHashes(ContactId(), Set("hash1"))))) should beUnchangedByEncodingAndDecoding }
    scenario("Empty PostAddressBook requests") { PostAddressBook(AddressBook.Empty) should beUnchangedByEncodingAndDecoding }
    scenario("PostLiking requests") { forAll((_: PostLiking) should beUnchangedByEncodingAndDecoding) }
    scenario("PostLastRead requests") { forAll((_: PostLastRead) should beUnchangedByEncodingAndDecoding) }
    scenario("SyncPreKeys requests") { forAll((_: SyncPreKeys) should beUnchangedByEncodingAndDecoding) }
    scenario("PostAssetStatus requests") { forAll((_: PostAssetStatus) should beUnchangedByEncodingAndDecoding) }
    scenario("PostReceipt requests") { forAll((_: PostReceipt) should beUnchangedByEncodingAndDecoding) }

    scenario("SelfPicture requests") {
      forAll (Table("asset", Some(AssetId()), None)) { asset: Option[AssetId] => PostSelfPicture(asset) should beUnchangedByEncodingAndDecoding }
    }
  }

  feature("Storing to and retrieving from the database") {
    scenario("Store a list of requests and retrieve them again") {
      val requests: Seq[SyncJob] = Seq(
        SyncConversations,
        SyncUser(Set(UserId(), UserId(), UserId())),
        SyncConversation(Set(ConvId(), ConvId(), ConvId())),
        PostConv(ConvId(), Set(UserId()), None, Some(TeamId()), Set(Access.INVITE, Access.CODE), AccessRole.NON_ACTIVATED),
        DeletePushToken(PushToken()),
        PostSelf(UserInfo(UserId(), Some("name"), Some(1), Some(EmailAddress("email")), Some(PhoneNumber("phone")), None, None)),
        PostConvState(ConvId(), ConversationState(Some(false), Some(RemoteInstant(Instant.now)), Some(true), Some(RemoteInstant.Epoch))),
        SyncSearchQuery(SearchQuery.Recommended("meep moop")),
        PostSelfPicture(Some(AssetId())),
        PostMessage(ConvId(), MessageId(), RemoteInstant(Instant.now)),
        PostConvJoin(ConvId(), Set(UserId(), UserId())),
        PostConnection(UserId(), "name", "message"),
        PostConnectionStatus(UserId(), Some(ConnectionStatus.Ignored)),
        SyncRichMedia(MessageId()),
        SyncPreKeys(UserId(), Set(ClientId(), ClientId())),
        PostLastRead(ConvId(), RemoteInstant(Instant.now)),
        PostCleared(ConvId(), RemoteInstant(Instant.now)),
        PostAssetStatus(ConvId(), MessageId(), Some(5.seconds), AssetStatus.UploadCancelled),
        PostReceipt(ConvId(), MessageId(), UserId(), ReceiptType.Delivery)
      ) map { SyncJob(SyncId(), _) }

      SyncJobDao.insertOrReplace(requests)

      SyncJobDao.list shouldEqual requests
    }
  }

  feature("Db updates") {
    scenario("Load PostAddressBook with no payload") {
      val json = encode[SyncRequest](PostAddressBook(AddressBook(Seq("test"), Seq(ContactHashes(ContactId(), Set("hash1"))))))
      json.remove("addressBook")
      info(json.toString)
      decode[SyncRequest](json.toString) shouldEqual PostAddressBook(AddressBook.Empty)
    }

    scenario("Load ConversationState without 'archived'") {
      val original = ConversationState(Some(false), Some(RemoteInstant(Instant.now)), Some(true), Some(RemoteInstant.Epoch))
      val json = encode[ConversationState](original)
      json.remove("otr_archived")
      json.remove("otr_archived_ref")
      decode[ConversationState](json.toString) shouldEqual original.copy(archived = None, archiveTime = None)
    }
  }
}
