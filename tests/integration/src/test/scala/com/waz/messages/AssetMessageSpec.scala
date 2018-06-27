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
package com.waz.messages

import java.io._
import java.util
import java.util.Date
import java.util.concurrent.{CountDownLatch, TimeUnit}

import akka.pattern.ask
import com.waz.ZLog.ImplicitTag._
import com.waz.api.AssetStatus._
import com.waz.api.MessageContent.Asset.{Answer, ErrorHandler}
import com.waz.api._
import com.waz.api.impl.{DoNothingAndProceed, ErrorResponse}
import com.waz.model
import com.waz.model.AssetData.MaxAllowedAssetSizeInBytes
import com.waz.model.AssetMetaData.{Audio, Video}
import com.waz.model.AssetStatus.{UploadCancelled, UploadDone, UploadFailed}
import com.waz.model.otr.ClientId
import com.waz.model.{GenericContent, Mime, AssetStatus => _, MessageContent => _, _}
import com.waz.provision.ActorMessage._
import com.waz.service.conversation.ConversationsUiService.LargeAssetWarningThresholdInBytes
import com.waz.service.{UserModule, ZMessaging, ZMessagingFactory}
import com.waz.sync.otr.OtrSyncHandlerImpl
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.testutils.{DefaultPatienceConfig, FeigningAsyncClientImpl, TestResourceContentProvider}
import com.waz.threading.Threading
import com.waz.utils.returning
import org.robolectric.Robolectric.{getShadowApplication, shadowOf}
import org.robolectric.shadows.ShadowContentResolver2
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.Matcher

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Random, Success}

class AssetMessageSpec extends FeatureSpec with BeforeAndAfter with Matchers with OptionValues with ProvisionedApiSpec with ThreadActorSpec with ScalaFutures with Inspectors with DefaultPatienceConfig {

  lazy val errors = api.getErrors

  before {
    ShadowContentResolver2.reset()
    testClient.simulateNetworkFailure = false
    zmessaging.network.networkMode ! NetworkMode.WIFI
    postStarted = false
    postCancelled = false
    beforePostAsset = None
    otrMessageSyncs = Vector.empty

    errors.foreach(_.dismiss())
  }

  scenario("init remote") {
    (messages should have size 1).soon
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
  }

  feature("Sending an asset message") {
    scenario("Blue sky") {

      val fromBefore = messages.size

      val upload = uriAssetForUpload(pdf)
      upload.name.await().value shouldEqual pdf.name
      upload.mimeType.await() shouldEqual pdf.mime
      upload.sizeInBytes.await().value shouldEqual pdf.size

      var reportedIssues = 0
      zmessaging.convsUi.sendMessage(conv.id, upload, new ErrorHandler {
        override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: Answer): Unit = reportedIssues += 1
      })

      (messages should have size (fromBefore + 1)).soon
      reportedIssues shouldEqual 0

      soon {
        messages(1).msgType shouldBe Message.Type.ANY_ASSET
        getAsset(messages.last.assetId) shouldBe 'defined
      }
      val msg = messages(1)
      val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      val spy = new AssetStatusSpy(asset)
      soon {
        asset.getId shouldEqual upload.getId
        asset.getName shouldEqual pdf.name
        asset.getMimeType shouldEqual pdf.mime.str
        asset.getSizeInBytes shouldEqual pdf.size
        asset.getStatus shouldEqual DOWNLOAD_DONE
        spy.states shouldEqual Seq(UPLOAD_NOT_STARTED, UPLOAD_IN_PROGRESS, UPLOAD_DONE, DOWNLOAD_DONE)
      }
      errors shouldBe empty
    }

    scenario("Send audio asset from uri") {
      val fromBefore = messages.size

      val upload = uriAssetForUpload(audio)

      zmessaging.convsUi.sendMessage(conv.id, upload, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon

      soon {
        messages.last.msgType shouldBe Message.Type.AUDIO_ASSET
        getAsset(messages.last.assetId) shouldBe 'defined
      }

      val msg = messages.last
      val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      val spy = new AssetStatusSpy(asset)
      soon {
        asset.getId shouldEqual upload.getId
        asset.getName shouldEqual audio.name
        asset.getMimeType shouldEqual audio.mime.str
        asset.getSizeInBytes shouldEqual audio.size
        asset.getStatus shouldEqual DOWNLOAD_DONE
        asset.getDuration.getSeconds shouldEqual 4
        spy.states shouldEqual Seq(UPLOAD_NOT_STARTED, UPLOAD_IN_PROGRESS, UPLOAD_DONE, DOWNLOAD_DONE)
        getMessage(msg.id).get.state shouldEqual Message.Status.SENT
      }

      messages.last.protos should beMatching {
        case Seq(GenericMessage(_, GenericContent.Asset(AssetData(_, Mime.Audio.MP4, _, UploadDone, _, _, _, _, _, _, _, Some(Audio(d, _)), _, _, _, _, _, _), _))) if d.getSeconds == 4 => true
      }

      errors shouldBe empty
    }

    scenario("Send video asset from uri") {
      val fromBefore = messages.size

      val upload = uriAssetForUpload(video)

      zmessaging.convsUi.sendMessage(conv.id, upload, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon

      soon {
        messages.last.msgType shouldBe Message.Type.VIDEO_ASSET
        getAsset(messages.last.assetId) shouldBe 'defined
      }

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      val spy = new AssetStatusSpy(asset)
      soon {
        asset.getId shouldEqual upload.getId
        asset.getName shouldEqual video.name
        asset.getMimeType shouldEqual video.mime.str
        asset.getSizeInBytes shouldEqual video.size
        asset.getStatus shouldEqual DOWNLOAD_DONE
        asset.getDuration.getSeconds shouldEqual 3
        asset.getWidth shouldEqual 1080
        asset.getHeight shouldEqual 1920
        spy.states shouldEqual Seq(UPLOAD_NOT_STARTED, UPLOAD_IN_PROGRESS, UPLOAD_DONE, DOWNLOAD_DONE)
        messages.last.state shouldEqual Message.Status.SENT
      }

      messages.last.protos should beMatching {
        case Seq(GenericMessage(_, GenericContent.Asset(AssetData(_, Mime.Audio.MP4, _, UploadDone, _, _, _, _, _, _, _, Some(Video(Dim2(1080, 1920), d)), _, _, _, _, _, _), _))) if d.getSeconds == 3 => true
      }

      errors shouldBe empty
    }

    scenario("Send image asset from uri - should be sent as regular image message") {
      val fromBefore = messages.size

      val upload = uriAssetForUpload(image)

      zmessaging.convsUi.sendMessage(conv.id, upload, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon

      soon {
        messages.last.msgType shouldBe Message.Type.ASSET
      }
    }

    scenario("Asset loading from stream takes long time") {
      val env = LatchedUpload()

      val fromBefore = messages.size
      zmessaging.convsUi.sendMessage(conv.id, env.upload, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon
      soon {
        messages.last.msgType shouldBe Message.Type.ANY_ASSET
        getAsset(messages.last.assetId) shouldBe 'defined
      }

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      soon {
        asset.getId shouldEqual env.upload.getId
        asset.getName shouldEqual "name"
        asset.getMimeType shouldEqual "application/octet-stream"
        asset.getSizeInBytes shouldEqual 102400L
        asset.getStatus shouldEqual AssetStatus.UPLOAD_NOT_STARTED
      }

      env.latch.countDown()

      (asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS).soon
      (asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE).soon
    }

    scenario("Placeholder message is sent before asset data is fetched from provider") {
      val fromBefore = messages.size
      val latch = new util.concurrent.CountDownLatch(1)
      val upload = uriAssetForUpload(pdf, Some(latch))
      zmessaging.convsUi.sendMessage(conv.id, upload, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon
      soon {
        messages.last.msgType shouldBe Message.Type.ANY_ASSET
        getAsset(messages.last.assetId) shouldBe 'defined
      }

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      soon {
        asset.getId shouldEqual upload.getId
        asset.getName shouldEqual "ScalaReference.pdf"
        asset.getMimeType shouldEqual "application/pdf"
        asset.getSizeInBytes shouldEqual pdf.size
        asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS // placeholder is sent before data is fetched form provider
      }

      val spy = new AssetStatusSpy(asset)
      latch.countDown()

      soon {
        spy.states shouldEqual Seq(UPLOAD_IN_PROGRESS, UPLOAD_DONE, DOWNLOAD_DONE)
      }
    }

    scenario("Sending is cancelled while fetching asset data from stream") {
      val env = LatchedUpload(delay = 50.millis)
      val fromBefore = messages.size

      zmessaging.convsUi.sendMessage(conv.id, env.upload, DoNothingAndProceed)
      (messages should have size (fromBefore + 1)).soon

      env.arrival.await(5, TimeUnit.SECONDS)

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      asset.getUploadProgress.cancel()

      env.latch.countDown()

      (messages should have size fromBefore).soon
      env.completelyRead shouldBe false
      idle(3.seconds)
    }

    scenario("Sending is cancelled while fetching asset data from provider") {
      val fromBefore = messages.size
      val latch = new util.concurrent.CountDownLatch(1)
      val upload = uriAssetForUpload(pdf, Some(latch))
      zmessaging.convsUi.sendMessage(conv.id, upload, DoNothingAndProceed)
      (messages should have size (fromBefore + 1)).soon

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS
      }
      awaitUi(250.millis)

      asset.getUploadProgress.cancel()
      latch.countDown()

      (messages should have size fromBefore).soon
      idle(3.seconds)
    }

    scenario("Sending is cancelled during sync") {
      val fromBefore = messages.size

      postStarted = false
      zmessaging.convsUi.sendMessage(conv.id, assetForUpload(5 * (1 << 20)), DoNothingAndProceed)
      (messages should have size (fromBefore + 1)).soon

      (postStarted shouldBe true).soon

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      asset.getUploadProgress.cancel()

      soon {
        postCancelled shouldBe true
        messages should have size fromBefore
      }
    }

    scenario("Cancel is attempted after upload has completed") {
      val fromBefore = messages.size

      zmessaging.convsUi.sendMessage(conv.id, assetForUpload(100000), DoNothingAndProceed)
      (messages should have size (fromBefore + 1)).soon

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      soon {
        messages.last.msgType shouldBe Message.Type.ANY_ASSET
        asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE
      }

      asset.getUploadProgress.cancel()

      idle(3.second)

      (messages should have size (fromBefore + 1)).soon

      lazy val msg2 = messages.last
      lazy val asset2 = new com.waz.api.impl.Asset(msg2.assetId, msg2.id)
      soon {
        messages.last.msgType shouldBe Message.Type.ANY_ASSET
        asset2.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE
      }
    }

    scenario("Cancel during network outage") {
      val fromBefore = messages.size

      beforePostAsset = Some { () =>
        zmessaging.network.networkMode ! NetworkMode.OFFLINE
        testClient.simulateNetworkFailure = true
      }

      val uploadedAsset = assetForUpload(1L << 20)
      zmessaging.convsUi.sendMessage(conv.id, uploadedAsset, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon
      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      soon {
        messages.last.msgType shouldBe Message.Type.ANY_ASSET
        asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS
      }
      asset.getUploadProgress.cancel()

      (asset.getStatus shouldEqual AssetStatus.UPLOAD_CANCELLED).soon

      otrMessageSyncs = Vector.empty
      testClient.simulateNetworkFailure = false
      zmessaging.network.networkMode ! NetworkMode.WIFI

      within(10.seconds) {
        messages should have size fromBefore
        no(otrMessageSyncs) should beStatusMessage(uploadedAsset.id, UploadFailed)
        exactly(1, otrMessageSyncs) should beStatusMessage(uploadedAsset.id, UploadCancelled)
      }
    }

    scenario("Following text messages should not get reordered after video upload is complete") {
      val fromBefore = messages.size

      val uploadedAsset = videoForUpload(10L << 20)
      zmessaging.convsUi.sendMessage(conv.id, uploadedAsset, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon
      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages.last.msgType shouldBe Message.Type.VIDEO_ASSET
      }

      val numberOfTextMessages = 10
      (1 to (numberOfTextMessages / 2)).foreach { n =>
        idle(250.millis)
        zmessaging.convsUi.sendMessage(conv.id, s"test message $n")
        auto2 ? SendText(conv.data.remoteId, s"test message ${n + 1}") should eventually(be(Successful))
      }

      within(20.seconds)(asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE)

      val finalSize = fromBefore + 11
      forAsLongAs(3.seconds, after = 1.second) {
        messages should have size finalSize
        forAll(1 to numberOfTextMessages map (finalSize - _))(n => messages(n).msgType shouldEqual Message.Type.TEXT)
        messages(finalSize - numberOfTextMessages - 1).msgType shouldEqual Message.Type.VIDEO_ASSET
      }
    }
  }

  feature("Error handling") {
    scenario("Provider throws error") {
      val fromBefore = messages.size

      val asset = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, None)(_ => throw new FileNotFoundException("imaginary file moved"))
      val handler = new ErrorHandler {
        override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: Answer): Unit = fail()
      }

      zmessaging.convsUi.sendMessage(conv.id, asset, handler)

      soon {
        errors should not be empty
        errors.headOption.map(_.getType) shouldEqual Some(ErrorType.CANNOT_SEND_ASSET_FILE_NOT_FOUND)
        errors.foreach(_.dismiss())
      }

      idle(1.second)
      (messages should have size fromBefore).soon
      zmessaging.assetsStorage.get(asset.id).await() shouldBe None
    }

    scenario("Asset size (as reported by provider) is too big") {
      val fromBefore = messages.size

      val asset = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, Some(MaxAllowedAssetSizeInBytes + 1L)) {
        val data = returning(Array.ofDim[Byte](1000))(Random.nextBytes)
        _ => new ByteArrayInputStream(data)
      }
      val handler = new ErrorHandler {
        override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: Answer): Unit = fail()
      }

      zmessaging.convsUi.sendMessage(conv.id, asset, handler)

      soon {
        errors should not be empty
        errors.headOption.map(_.getType) shouldEqual Some(ErrorType.CANNOT_SEND_ASSET_TOO_LARGE)
        errors.foreach(_.dismiss())
      }

      idle(1.second)
      (messages should have size fromBefore).soon
      zmessaging.assetsStorage.get(asset.id).await() shouldBe None
    }

    scenario("Actual asset size is too big") {
      val fromBefore = messages.size

      val asset = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, None) {
        val data = returning(Array.ofDim[Byte](MaxAllowedAssetSizeInBytes.toInt + 1))(Random.nextBytes)
        _ => new ByteArrayInputStream(data)
      }
      val handler = new ErrorHandler {
        override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: Answer): Unit = fail()
      }

      zmessaging.convsUi.sendMessage(conv.id, asset, handler)

      soon {
        errors should not be empty
        errors.headOption.map(_.getType) shouldEqual Some(ErrorType.CANNOT_SEND_ASSET_TOO_LARGE)
        zmessaging.assetsStorage.get(asset.id).await() match {
          case Some(asset: AssetData) => asset.status shouldEqual UploadFailed
          case _ => fail(s"unexpected asset: $asset")
        }
      }

      errors.foreach(_.dismiss())
      idle(1.second)
      (messages should have size fromBefore).soon
      zmessaging.assetsStorage.get(asset.id).await() shouldBe None
    }

    scenario("Asset size warning on non-wifi network") {
      val fromBefore = messages.size
      zmessaging.network.networkMode ! NetworkMode._2G
      info("network mode: " + zmessaging.network.networkMode.currentValue.value)

      def newAsset(name: String, size: Long = LargeAssetWarningThresholdInBytes + 1L) = impl.AssetForUpload(AssetId(), Some(name), Mime.Default, Some(size)) {
        val data = returning(Array.ofDim[Byte](size.toInt))(Random.nextBytes)
        _ => new ByteArrayInputStream(data)
      }

      class TestErrorHandler(f: Answer => Unit) extends ErrorHandler {
        val promisedWarning = Promise[(Long, NetworkMode)]
        override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: Answer): Unit = {
          promisedWarning.success((sizeInBytes, net))
          f(answer)
        }
      }

      val asset1 = newAsset("asset 1")
      val handler1 = new TestErrorHandler(_.cancel())
      zmessaging.convsUi.sendMessage(conv.id, asset1, handler1)

      handler1.promisedWarning.future.await("warning should be reported") shouldEqual (LargeAssetWarningThresholdInBytes + 1L, NetworkMode._2G)
      idle(1.second)
      (messages should have size fromBefore).soon
      zmessaging.assetsStorage.get(asset1.id).await() shouldBe None

      val asset2 = newAsset("asset 2")
      val handler2 = new TestErrorHandler(_.ok())
      zmessaging.convsUi.sendMessage(conv.id, asset2, handler2)

      handler2.promisedWarning.future.await("warning should be reported") shouldEqual (LargeAssetWarningThresholdInBytes + 1L, NetworkMode._2G)
      soon {
        messages should have size (fromBefore + 1)
        messages.last.state shouldEqual Message.Status.SENT
      }
      zmessaging.assetsStorage.get(asset2.id).await() shouldBe 'defined

      val asset3 = newAsset("asset 3", 1000L)
      val handler3 = new TestErrorHandler(_.cancel())
      zmessaging.convsUi.sendMessage(conv.id, asset3, handler3)

      (messages should have size (fromBefore + 2)).soon
      handler3.promisedWarning.isCompleted shouldBe false
      zmessaging.assetsStorage.get(asset3.id).await() shouldBe 'defined
      errors shouldBe empty
    }

    scenario("Sending an asset of exactly the maximum allowed size") {
      val fromBefore = messages.size

      zmessaging.convsUi.sendMessage(conv.id, assetForUpload(MaxAllowedAssetSizeInBytes), DoNothingAndProceed)

      within(1.second)(messages should have size (fromBefore + 1))

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      within(20.seconds) {
        messages.last.state shouldBe Message.Status.SENT
        messages.last.msgType shouldBe Message.Type.ANY_ASSET
        asset.getName shouldEqual "asset"
        asset.getMimeType shouldEqual "application/octet-stream"
        asset.getSizeInBytes shouldEqual MaxAllowedAssetSizeInBytes
        asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE
      }
    }

    scenario("Asset upload fails due to network interruption") {
      val fromBefore = messages.size

      beforePostAsset = Some { () =>
        zmessaging.network.networkMode ! NetworkMode.OFFLINE
        testClient.simulateNetworkFailure = true
      }

      val uploadedAsset = assetForUpload(1L << 20)
      zmessaging.convsUi.sendMessage(conv.id, uploadedAsset, DoNothingAndProceed)

      (messages should have size (fromBefore + 1)).soon
      lazy val message = messages.last
      (message.state shouldBe Message.Status.FAILED).soon

      idle(1.second)

      otrMessageSyncs = Vector.empty
      testClient.simulateNetworkFailure = false
      zmessaging.network.networkMode ! NetworkMode.WIFI

      within(10.seconds)(exactly(1, otrMessageSyncs) should beStatusMessage(uploadedAsset.id, UploadFailed))
    }
  }

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = conversations.head
  def messages = listMessages(conv.id)
  lazy val authority = s"${context.getPackageName}.testassets"
  lazy val resolver = shadowOf(getShadowApplication.getContentResolver)

  lazy val provider = new TestResourceContentProvider(authority)
  lazy val pdfResource = "/assets/ScalaReference.pdf"
  lazy val pdfUri = provider.resourceUri(pdfResource)
  lazy val pdf = provider.getResource(pdfUri)

  lazy val audioUri = provider.resourceUri("/assets/audio.m4a")
  lazy val audio = provider.getResource(audioUri)

  lazy val videoUri = provider.resourceUri("/assets/video_hd.mp4")
  lazy val video = provider.getResource(videoUri)

  lazy val imageUri = provider.resourceUri("/images/penguin.png")
  lazy val image = provider.getResource(imageUri)

  lazy val auto2 = registerDevice("auto2")

  def uriAssetForUpload(res: provider.Resource, latch: Option[CountDownLatch] = None) = {
    val stream = new FilterInputStream(res.inputStream) {
      override def read(): Int = {
        latch.foreach(_.await())
        super.read()
      }

      override def read(buffer: Array[Byte], byteOffset: Int, byteCount: Int): Int = {
        latch.foreach(_.await())
        super.read(buffer, byteOffset, byteCount)
      }
    }

    ShadowContentResolver2.registerProvider(authority, provider)
    res.registerStream()
    AssetFactory.fromContentUri(res.uri).asInstanceOf[impl.AssetForUpload]
  }

  def assetForUpload(size: Long) = impl.AssetForUpload(AssetId(), Some("asset"), Mime.Default, Some(size)) {
    val data = returning(Array.ofDim[Byte](size.toInt))(Random.nextBytes)
    _ => new ByteArrayInputStream(data)
  }

  def videoForUpload(size: Long) = impl.AssetForUpload(AssetId(), Some("video.mp4"), Mime.Video.MP4, Some(size)) {
    val data = returning(Array.ofDim[Byte](size.toInt))(Random.nextBytes)
    _ => new ByteArrayInputStream(data)
  }

  case class LatchedUpload(size: Long = 100L * (1L << 10), delay: FiniteDuration = Duration.Zero) {
    val arrival = new CountDownLatch(1)
    val latch = new CountDownLatch(1)
    @volatile var completelyRead = false
    val upload = impl.AssetForUpload(AssetId(), Some("name"), Mime.Default, Some(size)) { context =>
      val data = returning(Array.ofDim[Byte](size.toInt))(Random.nextBytes)
      new ByteArrayInputStream(data) {
        override def read(buffer: Array[Byte], byteOffset: Int, byteCount: Int): Int = {
          arrival.countDown()
          latch.await(5, TimeUnit.SECONDS)
          Thread.sleep(delay.toMillis)
          returning(super.read(buffer, byteOffset, byteCount))(n => if (n == -1) completelyRead = true)
        }

        override def read(): Int = {
          arrival.countDown()
          latch.await(5, TimeUnit.SECONDS)
          Thread.sleep(delay.toMillis)
          returning(super.read())(n => if (n == -1) completelyRead = true)
        }
      }
    }
  }

  @volatile var postStarted = false
  @volatile var postCancelled = false
  @volatile var beforePostAsset = Option.empty[() => Unit]
  @volatile var otrMessageSyncs = Vector.empty[Sent]

  type Sent = (RConvId, GenericMessage, Either[ErrorResponse, Date])

  def beStatusMessage(aid: AssetId, status: model.AssetStatus): Matcher[Sent] =
    be(conv.data.remoteId).compose((_: Sent)._1) and
    be(aid.str).compose((_: Sent)._2.messageId) and
    beMatching { case GenericMessage(_, GenericContent.Asset(AssetData.WithStatus(`status`), _)) => }.compose((_: Sent)._2) and
    be('right).compose((_: Sent)._3)

  override val provisionFile: String = "/two_users_connected.json"

  override lazy val zmessagingFactory: ZMessagingFactory = new ZMessagingFactory(globalModule) {

    override def zmessaging(teamId: Option[TeamId], clientId: ClientId, userModule: UserModule): ZMessaging =
      new ApiZMessaging(teamId, clientId, userModule) {

        override lazy val otrSync = new OtrSyncHandlerImpl(otrClient, messagesClient, assetClient, otrService, assets, conversations, convsStorage, users, messages, errors, otrClientsSync, cache, push) {
          override def postOtrMessage(convId: ConvId, remoteId: RConvId, message: GenericMessage, recipients: Option[Set[UserId]], nativePush: Boolean = true): Future[Either[ErrorResponse, Date]] =
            super.postOtrMessage(convId, remoteId, message).andThen { case Success(r) if r.isRight => otrMessageSyncs :+=(remoteId, message, r) }(Threading.Background)
        }
      }
  }

  override lazy val testClient = new FeigningAsyncClientImpl
}

class AssetStatusSpy(asset: Asset) extends UpdateListener {

  var states = Seq(asset.getStatus)

  asset.addUpdateListener(this)

  override def updated(): Unit = states = (states :+ asset.getStatus).distinct
}
