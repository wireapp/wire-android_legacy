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

import com.google.protobuf.ByteString
import com.waz.api.Message.Status
import com.waz.api.Message.Type._
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.{Composite, Text}
import com.waz.model._
import com.waz.model.otr.UserClients
import com.waz.service.assets.{AssetService, DownloadAssetStorage}
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.{MessageEventProcessor, MessagesContentUpdater, MessagesService}
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.TestGlobalPreferences
import com.waz.testutils.TestUserPreferences
import com.waz.threading.Threading
import com.waz.utils.crypto.ReplyHashing
import com.waz.zms.BuildConfig
import com.wire.signals.{EventStream, Signal}
import org.scalatest.Inside

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageEventProcessorSpec extends AndroidFreeSpec with Inside with DerivedLogTag {

  val selfUserId        = UserId("self")
  val storage           = mock[MessagesStorage]
  val convsStorage      = mock[ConversationStorage]
  val otrClientsStorage = mock[OtrClientsStorage]
  val deletions         = mock[MsgDeletionStorage]
  val assets            = mock[AssetService]
  val replyHashing      = mock[ReplyHashing]
  val msgsService       = mock[MessagesService]
  val convs             = mock[ConversationsContentUpdater]
  val convsService      = mock[ConversationsService]
  val downloadStorage   = mock[DownloadAssetStorage]
  val buttonsStorage    = mock[ButtonsStorage]
  val prefs             = new TestGlobalPreferences()
  val userPrefs         = new TestUserPreferences()

  val domain            = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty

  val messagesInStorage = Signal[Seq[MessageData]](Seq.empty)
  (storage.getMessages _).expects(*).atLeastOnce.onCall { ids: Traversable[MessageId] =>
    messagesInStorage.head.map(msgs => ids.map(id => msgs.find(_.id == id)).toSeq)(Threading.Background)
  }

  (replyHashing.hashMessages _).expects(*).atLeastOnce.onCall { msgs: Seq[MessageData] =>
    Future.successful(msgs.filter(m => m.quote.isDefined && m.quote.flatMap(_.hash).isDefined).map(m => m.id -> m.quote.flatMap(_.hash).get).toMap)
  }

  feature("Push events processing") {
    scenario("Process text message event") {
      val sender = UserId("sender")
      val text = "hello"

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)

      clock.advance(5.seconds)
      val event = GenericMessageEvent(conv.remoteId, domain, RemoteInstant(clock.instant()), sender, domain, GenericMessage(Uid("uid"), Text(text)))

      (storage.updateOrCreate _).expects(*, *, *).onCall { (_, _, creator) => Future.successful(creator)}
      (storage.get _).expects(*).once().returns(Future.successful(None))

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(event))).head) {
        case m =>
          m.msgType              shouldEqual TEXT
          m.convId               shouldEqual conv.id
          m.userId               shouldEqual sender
          m.content              shouldEqual MessageData.textContent(text)
          m.time                 shouldEqual event.time
          m.localTime            shouldEqual event.localTime
          m.state                shouldEqual Status.SENT
          m.genericMsgs.head.toString shouldEqual event.asInstanceOf[GenericMessageEvent].content.toString
      }
    }

    scenario("Process MemberJoin events sent from other user") {
      val sender = UserId("sender")

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      val membersAdded = Seq(
        UserId("user1"),
        UserId("user2")
      )

      clock.advance(5.seconds)
      val event = MemberJoinEvent(
        conv.remoteId,
        domain,
        RemoteInstant(clock.instant()),
        sender,
        domain,
        membersAdded,
        membersAdded.map(id => QualifiedId(id) -> ConversationRole.AdminRole).toMap
      )

      (storage.hasSystemMessage _).expects(conv.id, event.time, MEMBER_JOIN, sender).returning(Future.successful(false))
      (storage.getLastSentMessage _).expects(conv.id).anyNumberOfTimes().returning(Future.successful(None))
      (convsStorage.get _).expects(conv.id).anyNumberOfTimes().returning(Future.successful(Some(conv)))
      (storage.getLastSystemMessage _).expects(conv.id, MEMBER_JOIN, *).anyNumberOfTimes().returning(Future.successful(None))
      (storage.addMessage _).expects(*).once().onCall { m: MessageData =>
        messagesInStorage.mutate(_ ++ Seq(m))
        Future.successful(m)
      }

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(event))).head) {
        case m =>
          m.msgType       shouldEqual MEMBER_JOIN
          m.convId        shouldEqual conv.id
          m.userId        shouldEqual sender
          m.time          shouldEqual event.time
          m.localTime     shouldEqual event.localTime
          m.state         shouldEqual Status.SENT
          m.members       shouldEqual membersAdded.toSet
      }
    }

    scenario("Discard duplicate system message events") {
      val sender = UserId("sender")

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      val membersAdded = Seq(
        UserId("user1"),
        UserId("user2")
      )

      (storage.hasSystemMessage _).expects(*, *, *, *).repeated(3).returning(Future.successful(true))
      (storage.addMessage _).expects(*).never()

      val processor = getProcessor

      def testRound(event: MessageEvent) =
        result(processor.processEvents(conv, isGroup = false, Seq(event))) shouldEqual Set.empty

      clock.advance(1.second) //conv will have time EPOCH, needs to be later than that
      testRound(MemberJoinEvent(
        conv.remoteId,
        domain,
        RemoteInstant(clock.instant()),
        sender,
        domain,
        membersAdded,
        membersAdded.map(id => QualifiedId(id) -> ConversationRole.AdminRole).toMap
      ))
      clock.advance(1.second)
      testRound(MemberLeaveEvent(conv.remoteId, domain, RemoteInstant(clock.instant()), sender, domain, membersAdded, reason = None))
      clock.advance(1.second)
      testRound(RenameConversationEvent(conv.remoteId, domain, RemoteInstant(clock.instant()), sender, domain, Name("new name")))
    }

    scenario("System message events are overridden if only local version is present") {
      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      (convsStorage.get _).expects(conv.id).anyNumberOfTimes().returning(Future.successful(Some(conv)))

      clock.advance(1.second) //here, we create a local message
      val localMsg = MessageData(MessageId(), conv.id, RENAME, selfUserId, time = RemoteInstant(clock.instant()), localTime = LocalInstant(clock.instant()), state = Status.PENDING)

      clock.advance(1.second) //some time later, we get the response from the backend
      val event = RenameConversationEvent(conv.remoteId, domain, RemoteInstant(clock.instant()), selfUserId, domain, Name("new name"))

      (storage.hasSystemMessage _).expects(conv.id, event.time, RENAME, selfUserId).returning(Future.successful(false))
      (storage.getLastSentMessage _).expects(conv.id).anyNumberOfTimes().returning(Future.successful(None))
      (storage.getLastSystemMessage _).expects(conv.id, RENAME, *).anyNumberOfTimes().returning(Future.successful(Some(localMsg)))
      (storage.remove (_: MessageId)).expects(localMsg.id).returning(Future.successful({}))
      (storage.addMessage _).expects(*).onCall { msg : MessageData =>
        messagesInStorage.mutate(_ ++ Seq(msg))
        Future.successful(msg)
      }
      (convs.updateConversationLastRead _).expects(conv.id, event.time).onCall { (convId: ConvId, instant: RemoteInstant) =>
        Future.successful(Some((conv, conv.copy(lastRead = instant))))
      }

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(event))).head) { case msg =>
        msg.msgType shouldEqual RENAME
        msg.time    shouldEqual event.time
      }
    }

    scenario("Processing asset message pairs together") {
      // Given
      val sender = UserId("sender")
      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      val messageId = Uid("messageId")
      val remoteAssetId = AssetId("remoteAssetId")

      val originalAsset = GenericContent.Asset {
        val orig =
          Messages.Asset.Original.newBuilder
            .setName("The Alphabet")
            .setMimeType("text/plain")
            .setSize(26)
            .build

        Messages.Asset.newBuilder
          .setOriginal(orig)
          .build
      }

      val uploadAsset = GenericContent.Asset {
        val remoteData =
          Messages.Asset.RemoteData.newBuilder()
            .setAssetId(remoteAssetId.str)
            .setOtrKey(ByteString.copyFromUtf8(""))
            .setSha256(ByteString.copyFromUtf8(""))
            .build
        Messages.Asset.newBuilder
          .setUploaded(remoteData)
          .build
      }

      clock.advance(5.seconds)
      val originalEvent = GenericMessageEvent(conv.remoteId, domain, RemoteInstant(clock.instant()), sender, domain, GenericMessage(messageId, originalAsset))

      clock.advance(5.seconds)
      val uploadEvent = GenericMessageEvent(conv.remoteId, domain, RemoteInstant(clock.instant()), sender, domain, GenericMessage(messageId, uploadAsset))

      // Expectations

      // both events will check storage when looking for local data, but none will exist.
      (storage.get _).expects(*).twice().returns(Future.successful(None))

      // We will add both messages to storage together
      (storage.updateOrCreateAll _).expects(*).onCall { updaters: Map[MessageId, Option[MessageData] => MessageData] =>
        Future.successful(updaters.values.map(_.apply(None)).toSet)
      }

      // We will save both assets
      (assets.save _).expects(*).twice().returns(Future.successful(()))

      // When
      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(originalEvent, uploadEvent))).head) {
        // Then
        case m =>
          m.msgType              shouldEqual ANY_ASSET
          m.convId               shouldEqual conv.id
          m.userId               shouldEqual sender
          m.time                 shouldEqual originalEvent.time
          m.localTime            shouldEqual originalEvent.localTime
          m.state                shouldEqual Status.SENT
          m.genericMsgs.head.toString shouldEqual uploadEvent.asInstanceOf[GenericMessageEvent].content.toString
          m.assetId              shouldEqual Some(remoteAssetId)
      }
    }

    scenario("Process a composite event") {
      val senderId = UserId("sender")
      val messageId = MessageId("uid")
      val messageText = "hello"
      val button1Id = ButtonId("1")
      val button1Text = "button1"
      val button2Id = ButtonId("2")
      val button2Text = "button2"

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)

      clock.advance(5.seconds)
      val messageEvent = event(conv.remoteId, senderId, messageId.str, composite(text(messageText), button(button1Id.str, button1Text), button(button2Id.str, button2Text)))
      (storage.updateOrCreate _).expects(*, *, *).anyNumberOfTimes().onCall { (_, _, creator) => Future.successful(creator)}
      (storage.get _).expects(*).anyNumberOfTimes().returns(Future.successful(None))

      val expectedButtons = Set(
        ButtonData(messageId, button1Id, button1Text, 0),
        ButtonData(messageId, button2Id, button2Text, 1)
      )

      (buttonsStorage.updateOrCreateAll2 _).expects(expectedButtons.map(_.id), *).atLeastOnce().returning(Future.successful((expectedButtons)))

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(messageEvent))).head) {
        case m =>
          m.msgType              shouldEqual COMPOSITE
          m.convId               shouldEqual conv.id
          m.userId               shouldEqual senderId
          m.content              shouldEqual MessageData.textContent(messageText)
          m.time                 shouldEqual messageEvent.time
          m.localTime            shouldEqual messageEvent.localTime
          m.state                shouldEqual Status.SENT
          m.genericMsgs.head.toString shouldEqual messageEvent.asInstanceOf[GenericMessageEvent].content.toString
      }
    }
  }

  def getProcessor = {
    val content = new MessagesContentUpdater(storage, convsStorage, deletions, buttonsStorage, prefs, userPrefs)

    //TODO make VerificationStateUpdater mockable
    (otrClientsStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream[Seq[UserClients]]())
    (otrClientsStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream[Seq[(UserClients, UserClients)]]())
    (convsService.addUnexpectedMembersToConv _).expects(*, *).anyNumberOfTimes().returning(Future.successful({}))

    //often repeated mocks
    (deletions.getAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq.empty))

    new MessageEventProcessor(selfUserId, storage, content, assets, replyHashing, msgsService, convsService, convs, downloadStorage, ZMessaging.currentGlobal)
  }

  // if we need those utility methods in other specs, we can think of turning them into `apply` methods in GenericContent
  private def button(id: String, text: String) = {
    val b = Messages.Button.newBuilder.setId(id).setText(text).build
    Messages.Composite.Item.newBuilder().setButton(b).build
  }

  private def text(text: String) =
    Messages.Composite.Item.newBuilder.setText(Text(text).proto).build

  private def composite(items: Messages.Composite.Item*) = GenericContent.Composite {
    import scala.collection.JavaConverters._
    Messages.Composite.newBuilder
      .setExpectsReadConfirmation(false)
      .setLegalHoldStatus(Messages.LegalHoldStatus.UNKNOWN)
      .addAllItems(items.toIterable.asJava)
      .build
  }

  private def event(convId: RConvId, sender: UserId, uid: String, composite: Composite) =
    GenericMessageEvent(convId, domain, RemoteInstant(clock.instant()), sender, domain, GenericMessage(Uid(uid), composite))
}
