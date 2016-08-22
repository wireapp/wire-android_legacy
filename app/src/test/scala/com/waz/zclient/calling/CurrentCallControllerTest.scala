/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.calling

import android.content.Context
import android.content.res.Resources
import android.os.PowerManager
import com.waz.api.VoiceChannelState.{OTHER_CALLING, SELF_CALLING, SELF_CONNECTED, UNKNOWN}
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.VoiceChannelData.ConnectionState
import com.waz.model.VoiceChannelData.ConnectionState.{Connected, Connecting, Idle}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.call.FlowManagerService.{StateAndReason, StateOfReceivedVideo, UnknownState}
import com.waz.service.call.{FlowManagerService, VoiceChannelContent, VoiceChannelService}
import com.waz.testutils.TestUtils.{PrintValues, signalTest}
import com.waz.testutils.{MockZMessaging, TestWireContext}
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.{GlobalCallingController, CurrentCallController}
import com.waz.zclient.common.controllers.PermissionsController
import com.waz.zclient.{Module, R}
import org.junit.runner.RunWith
import org.junit.{Before, Test}
import org.mockito.Matchers.{any, anyInt}
import org.mockito.Mockito.{mock, when}
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.scalatest.junit.JUnitSuite

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest=Config.NONE)
class CurrentCallControllerTest extends JUnitSuite {

  implicit val printSignalVals: PrintValues = false
  implicit val context = mock(classOf[TestWireContext])

  implicit lazy val module = new Module {
    bind[Context] to context
    bind[Signal[Option[ZMessaging]]] to Signal.const(Some(zMessaging))
    bind[GlobalCallingController] to new GlobalCallingController(context)
    bind[PowerManager] to null //not needed for tests
    bind[PermissionsController] to null //not needed for tests
  }

  var zMessaging: MockZMessaging = _
  var controller: CurrentCallController = _

  lazy val mockTopChannels = Signal[(Option[VoiceChannelData], Option[VoiceChannelData])]
  lazy val currentChannelSignal = Signal[VoiceChannelData]
  lazy val stateOfReceivedVideo = Signal[StateOfReceivedVideo](UnknownState)
  lazy val cameraFailed = Signal[Boolean](false)

  lazy val selfUser = UserData("Self user")
  lazy val user2 = UserData("User 2")
  lazy val user3 = UserData("User 3")
  lazy val user4 = UserData("User 4")

  lazy val oneToOneConv = ConversationData(ConvId(user2.id.str), RConvId(), Some(user2.name), selfUser.id, ConversationType.OneToOne, generatedName = user2.name)
  lazy val groupConv = ConversationData(ConvId(), RConvId(), Some("Group Conversation"), selfUser.id, ConversationType.Group, generatedName = "Group Conversation")

  lazy val otherParticipantsInGroupCall = 2 //4 users minus the self user and the user not in the call

  lazy val mockVoiceChannelService = mock(classOf[VoiceChannelService])

  @Before
  def setup(): Unit = {
    zMessaging = new MockZMessaging(selfUserId = selfUser.id) {
      override lazy val voice = mockVoiceChannelService
      override lazy val voiceContent = mock(classOf[VoiceChannelContent])
      override lazy val flowmanager = mock(classOf[FlowManagerService])
    }

    zMessaging.insertUsers(Seq(selfUser, user2, user3))

    zMessaging.insertConv(oneToOneConv)
    zMessaging.insertConv(groupConv)

    zMessaging.addMember(oneToOneConv.id, user2.id)

    zMessaging.addMember(groupConv.id, user2.id)
    zMessaging.addMember(groupConv.id, user3.id)
    zMessaging.addMember(groupConv.id, user4.id)

    when(zMessaging.voiceContent.ongoingAndTopIncomingChannel).thenReturn(mockTopChannels)
    when(zMessaging.voice.voiceChannelSignal(any(classOf[ConvId]))).thenReturn(currentChannelSignal)
    when(zMessaging.flowmanager.stateOfReceivedVideo).thenReturn(stateOfReceivedVideo)
    when(zMessaging.flowmanager.cameraFailedSig).thenReturn(cameraFailed)

    setTablet(false)
    controller = new CurrentCallController()
  }

  @Test
  def callExistsForOngoingChannel(): Unit = {
    signalTest(controller.globController.callExists)(_ == true) {
      pushChannel(OngoingAudioCall(oneToOneConv))
    }
  }

  @Test
  def callDoesNotExistForNoChannels(): Unit = {
    signalTest(controller.globController.callExists)(_ == false) {
      pushChannel(null)
    }
  }

  @Test
  def channelMatchesTheUpdatedChannel(): Unit = {
    val channel = IncomingAudioCall(oneToOneConv)
    signalTest(controller.currentChannel)(_ equals (channel.data)) {
      pushChannel(channel)
    }
  }

  @Test
  def selfUserMatchesSetThisSelfUser(): Unit = {
    signalTest(controller.selfUser)(_.id equals (selfUser.id)) {
      pushChannel(IncomingAudioCall(oneToOneConv))
    }
  }

  @Test
  def isGroupCallForGroupChannel(): Unit = {
    signalTest(controller.groupCall)(_ == true) {
      pushChannel(IncomingAudioCall(groupConv))
    }
  }

  @Test
  def participantsOnOutgoingSingleCallShouldBeEmpty(): Unit = {
    signalTest(controller.otherParticipants)(_.isEmpty) {
      pushChannel(OutgoingAudioCall(oneToOneConv))
    }
  }

  @Test
  def participantsOnIncomingCallShouldBeEmpty(): Unit = {
    signalTest(controller.otherParticipants)(_.isEmpty) {
      pushChannel(IncomingAudioCall(oneToOneConv))
    }
  }

  @Test
  def otherParticipantsOnEstablishedCallShouldNotBeEmpty(): Unit = {
    signalTest(controller.otherParticipants)(_.size equals(1)) {
      pushChannel(OngoingAudioCall(oneToOneConv))
    }
  }

  @Test
  def notAllInGroupConversationShouldAppearAsCallParticipants(): Unit = {
    signalTest(controller.otherParticipants)(_.size equals(otherParticipantsInGroupCall)) {
      pushChannel(OngoingAudioCall(groupConv))
    }
  }

  @Test
  def participantsToDisplayInOneToOneIncomingCallShouldBeOtherUser(): Unit = {
    signalTest(controller.participantIdsToDisplay) {
      case partIds => partIds.size == 1 && partIds(0) == user2.id
    } {
      pushChannel(IncomingAudioCall(oneToOneConv))
    }
  }

  @Test
  def participantsToDisplayInOutgoingGroupCallShouldBeCaller(): Unit = {
    signalTest(controller.participantIdsToDisplay) {
      case partIds => partIds.size == 1 && partIds(0) == selfUser.id
    } {
      pushChannel(OutgoingAudioCall(groupConv))
    }
  }

  @Test
  def outgoingCallSignalReturnsTrueForOutgoingCall(): Unit = {
    signalTest(controller.outgoingCall) (_ == true) {
      pushChannel(OutgoingAudioCall(oneToOneConv))
    }
  }

  @Test
  def outgoingCallSignalReturnsFalseForOngoingCall(): Unit = {
    signalTest(controller.outgoingCall) (_ == false) {
      pushChannel(OngoingAudioCall(oneToOneConv))
    }
  }

  @Test
  def outgoingCallSignalReturnsFalseForIncomingCall(): Unit = {
    signalTest(controller.outgoingCall) (_ == false) {
      pushChannel(IncomingAudioCall(oneToOneConv))
    }
  }

  @Test
  def badConnectionMessageAppearsOnAvsStateChangeToBadConnection(): Unit = {
    val expectedMessage = "VIDEO STOPPED DUE TO POOR CONNECTION"
    when(context.getString(R.string.ongoing__poor_connection_message)).thenReturn(expectedMessage)
    signalTest(controller.stateMessageText)(_.getOrElse("") equals (expectedMessage)) {
      pushChannel(OngoingVideoCall(oneToOneConv))
      stateOfReceivedVideo ! StateAndReason(AvsVideoState.STOPPED, AvsVideoReason.BAD_CONNECTION)
    }
  }

  @Test
  def failingCameraOnOutgoingCallShouldShowCameraFailedMessage(): Unit = {
    val expectedMessage = s"CAMERA NOT AVAILABLE"
    when(context.getString(R.string.calling__self_preview_unavailable_long)).thenReturn(expectedMessage)
    signalTest(controller.stateMessageText)(_.getOrElse("") equals (expectedMessage)) {
      pushChannel(OutgoingVideoCall(oneToOneConv))
      cameraFailed ! true
    }
  }

  @Test
  def otherUserTurnsOffVideoMessage(): Unit = {
    val expectedMessage = s"${oneToOneConv.generatedName} TURNED OFF THE VIDEO"
    when(context.getString(R.string.ongoing__other_turned_off_video, oneToOneConv.generatedName)).thenReturn(expectedMessage)
    signalTest(controller.stateMessageText)(_.getOrElse("") equals (expectedMessage)) {
      pushChannel(OngoingVideoCall(oneToOneConv))
      pushChannel(OngoingVideoCallVideoOff(oneToOneConv))
    }
  }

  @Test
  def userTryingButUnableToSendVideoWithoutBadConnectionShouldDisplayUnableToSendVideoMessage(): Unit = {
    val expectedMessage = s"${oneToOneConv.generatedName} IS UNABLE TO SEND VIDEO"
    when(context.getString(R.string.ongoing__other_unable_to_send_video, oneToOneConv.generatedName)).thenReturn(expectedMessage)
    signalTest(controller.stateMessageText)(_.getOrElse("") equals (expectedMessage)) {
      pushChannel(OngoingVideoCall(oneToOneConv))
    }
  }

  @Test
  def rightButtonShouldNotBeDisplayedForAudioCallsOnTablets(): Unit = {
    setTablet(true)
    signalTest(controller.rightButtonShown)(_ == false) {
      pushChannel(OngoingAudioCall(oneToOneConv))
    }
  }

  //Tests that the tests are set up correctly, more than anything
  @Test
  def ensureWeGetCurrentConvAndVoiceService(): Unit = {
    signalTest(controller.voiceServiceAndCurrentConvId) {
      case (vcs, convId) => convId == oneToOneConv.id && vcs == mockVoiceChannelService
    } {
      pushChannel(OngoingVideoCall(oneToOneConv))
    }
  }

  @Test
  def cycleThroughCaptureDevices(): Unit = {
    pushChannel(OngoingVideoCallThreeCameras(oneToOneConv))

    signalTest(controller.currentCaptureDevice) (_ == Some(threeCaptureDevices(0)))(())

    signalTest(controller.currentCaptureDevice) (_ == Some(threeCaptureDevices(1))) {
      controller.currentCaptureDeviceIndex.mutate(_ + 1)
    }
    signalTest(controller.currentCaptureDevice) (_ == Some(threeCaptureDevices(2))) {
      controller.currentCaptureDeviceIndex.mutate(_ + 1)
    }
    signalTest(controller.currentCaptureDevice) (_ == Some(threeCaptureDevices(0))) {
      controller.currentCaptureDeviceIndex.mutate(_ + 1)
    }
  }


  private def pushChannel(callData: DefaultCall): Unit = {
    Option(callData) match {
      case Some(IncomingAudioCall(_)) | Some(IncomingVideoCall(_)) => mockTopChannels ! (None, callData.dataOpt)
      case Some(_) => mockTopChannels ! (callData.dataOpt, None)
      case None => mockTopChannels ! (None, None)
    }
    currentChannelSignal ! Option(callData).getOrElse(EmptyCall()).data
  }

  private def push(callData: DefaultCall, incoming: Boolean): Unit = {
    mockTopChannels !(if (incoming) None else callData.dataOpt, if (incoming) callData.dataOpt else None)
    currentChannelSignal ! callData.data
  }

  sealed abstract class DefaultCall(conversationData: ConversationData, channelState: VoiceChannelState, devState: ConnectionState, callerId: UserId, videoData: VideoCallData, participantsData: ParticipantsData) {
    val data = VoiceChannelData(conversationData.id, channelState, devState, lastSequenceNumber = None, caller = Some(callerId), selfId = selfUser.id, tracking = TrackingData(conversationData, channelState).data, video = videoData, revision = Revision(0), participantsById = participantsData.data)
    val dataOpt = Some(data)
  }

  case class EmptyCall() extends DefaultCall(oneToOneConv, UNKNOWN, Idle, selfUser.id, VideoDataForAudioCall().data, EmptyParticipantsData)

  case class OutgoingAudioCall(cData: ConversationData) extends DefaultCall(cData, SELF_CALLING, Connecting, selfUser.id, VideoDataForAudioCall().data, EmptyParticipantsData)
  case class IncomingAudioCall(cData: ConversationData) extends DefaultCall(cData, OTHER_CALLING, Idle, user2.id, VideoDataForAudioCall().data, EmptyParticipantsData)
  case class OngoingAudioCall(cData: ConversationData) extends DefaultCall(cData, SELF_CONNECTED, Connected, selfUser.id, VideoDataForAudioCall().data, ParticipantsData(cData, video = false))

  case class OutgoingVideoCall(cData: ConversationData) extends DefaultCall(cData, SELF_CALLING, Connecting, selfUser.id, VideoDataForVideoCall(VideoSendState.PREVIEW).data, EmptyParticipantsData)
  case class IncomingVideoCall(cData: ConversationData) extends DefaultCall(cData, OTHER_CALLING, Idle, user2.id, VideoDataForVideoCall(VideoSendState.PREVIEW).data, EmptyParticipantsData)
  case class OngoingVideoCall(cData: ConversationData) extends DefaultCall(cData, SELF_CONNECTED, Connected, selfUser.id, VideoDataForVideoCall(VideoSendState.SEND).data, ParticipantsData(cData, video = true))
  case class OngoingVideoCallVideoOff(cData: ConversationData) extends DefaultCall(cData, SELF_CONNECTED, Connected, selfUser.id, VideoDataForVideoCall(VideoSendState.SEND).data, ParticipantsData(cData, video = false))
  case class OngoingVideoCallThreeCameras(cData: ConversationData) extends DefaultCall(cData, SELF_CONNECTED, Connected, selfUser.id, VideoDataForVideoCallThreeCameras(VideoSendState.SEND).data, ParticipantsData(cData, video = false))

  case class TrackingData(cData: ConversationData, channelState: VoiceChannelState) {
    val data = CallTrackingData(None, None, None, maxNumParticipants = 1, kindOfCall = if (cData.convType == ConversationType.Group) KindOfCall.GROUP else KindOfCall.ONE_TO_ONE, callDirection = if (channelState == SELF_CALLING) CallDirection.OUTGOING else CallDirection.INCOMING)
  }

  val twoCaptureDevices = Vector(CaptureDeviceData("1", "Device 1"), CaptureDeviceData("2", "Device 2"))
  val threeCaptureDevices = Vector(CaptureDeviceData("1", "Device 1"), CaptureDeviceData("2", "Device 2"), CaptureDeviceData("3", "Device 3"))

  sealed abstract class VideoData(isVideoCall: Boolean, wantsToSendVideo: Boolean, canSendVideo: Boolean, videoSendState: VideoSendState, captureDevices: Vector[CaptureDeviceData], currentCaptureDevice: Option[CaptureDeviceData]) {
    val data = VideoCallData(isVideoCall, wantsToSendVideo, canSendVideo, videoSendState, captureDevices, captureDevices.headOption)
  }
  case class VideoDataForAudioCall() extends VideoData(false, false, false, VideoSendState.DONT_SEND, Vector.empty, None)
  case class VideoDataForVideoCall(sendState: VideoSendState) extends VideoData(true, true, true, sendState, twoCaptureDevices, twoCaptureDevices.headOption)
  case class VideoDataForVideoCallThreeCameras(sendState: VideoSendState) extends VideoData(true, true, true, sendState, threeCaptureDevices, twoCaptureDevices.headOption)

  sealed trait ParticipantsData {
    val data: Map[UserId, VoiceParticipantData]
  }

  object ParticipantsData {
    def apply(cData: ConversationData, video: Boolean) = {
      cData.convType match {
        case ConversationType.OneToOne => OneToOneParticipantsData(video)
        case ConversationType.Group => GroupParticipantsData(video)
        case _ => EmptyParticipantsData
      }
    }
  }

  case class OneToOneParticipantsData(video: Boolean) extends ParticipantsData {
    override val data = Map(
      (selfUser.id, VoiceParticipantData(oneToOneConv.id, selfUser.id, Connected, sendsVideo = video)),
      (user2.id, VoiceParticipantData(oneToOneConv.id, user2.id, Connected, sendsVideo = video))
      )
  }

  case class GroupParticipantsData(video: Boolean) extends ParticipantsData {
    override val data = Map(
      (selfUser.id, VoiceParticipantData(groupConv.id, selfUser.id, Connected, sendsVideo = video)),
      (user2.id, VoiceParticipantData(groupConv.id, user2.id, Connected, sendsVideo = video)),
      (user3.id, VoiceParticipantData(groupConv.id, user3.id, Connected, sendsVideo = video)),
      (user4.id, VoiceParticipantData(groupConv.id, user4.id, Idle, sendsVideo = false)) // user 4 is not part of the call
    )
  }

  case object EmptyParticipantsData extends ParticipantsData {
    override val data = Map.empty[UserId, VoiceParticipantData]
  }

  def setTablet(isTablet: Boolean): Unit = {
    val mockResources = mock(classOf[Resources])
    when(mockResources.getInteger(anyInt())).thenReturn(if (isTablet) 600 else 320)
    when(context.getResources).thenReturn(mockResources)
    controller = new CurrentCallController()
  }
}


