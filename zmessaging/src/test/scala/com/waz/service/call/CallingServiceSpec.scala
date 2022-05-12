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
package com.waz.service.call

import com.sun.jna.Pointer
import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.SkipTerminatingState
import com.waz.content.{MembersStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.otr.{ClientId, OtrClientIdMap}
import com.waz.model.{LocalInstant, UserId, _}
import com.waz.permissions.PermissionsService
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.call.Avs.AvsClosedReason.{AnsweredElsewhere, Normal, StillOngoing}
import com.waz.service.call.Avs._
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.call.CallInfo.Participant
import com.waz.service.call.CallingServiceSpec.CallStateCheckpoint
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.service.{BackendConfigFixture, MediaManagerService, NetworkModeService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.otr.OtrSyncHandler
import com.waz.sync.otr.OtrSyncHandler.TargetRecipients
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.utils.RichInstant
import com.wire.signals.Signal
import com.waz.utils.jna.Uint32_t
import com.waz.utils.wrappers.Context
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class CallingServiceSpec extends AndroidFreeSpec with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  val federationSupported: Boolean = false

  val avs            = mock[Avs]
  val flows          = mock[FlowManagerService]
  val members        = mock[MembersStorage]
  val media          = mock[MediaManagerService]
  val network        = mock[NetworkModeService]
  val convs          = mock[ConversationsContentUpdater]
  val convsService   = mock[ConversationsService]
  val messages       = mock[MessagesService]
  val permissions    = mock[PermissionsService]
  val push           = mock[PushService]
  val otrSyncHandler = mock[OtrSyncHandler]
  val globalPrefs    = new TestGlobalPreferences

  val selfUserId      = UserId("self-user")
  val selfQualifiedId = QualifiedId(selfUserId, "")
  val selfClientId    = ClientId("selfClient")
  val domain          = Domain("domain")
  val selfParticipant = Participant(selfQualifiedId, selfClientId)
  val selfUserData    = UserData.withName(selfUserId, "")

  // For one to one
  val otherUserId       = UserId("otherUser")
  val otherQualifiedId  = QualifiedId(otherUserId, "")
  val otherUserClientId = ClientId("otherUserClient")
  val otherUser         = Participant(otherQualifiedId, otherUserClientId)

  // To be added to ms for groups
  val otherUser2Id       = UserId("otherUser2")
  val otherQualified2Id  = QualifiedId(otherUser2Id, "")
  val otherUser2ClientId = ClientId("otherUserClient2")
  val otherUser2         = Participant(otherQualified2Id, otherUser2ClientId)

  val _1to1Conv     = ConversationData(ConvId(otherUserId.str), RConvId(otherUserId.str), Some(Name(otherUserId.str)),   selfUserId, ConversationType.OneToOne)
  val _1to1Conv2    = ConversationData(ConvId(otherUser2Id.str), RConvId(otherUser2Id.str), Some(Name(otherUser2Id.str)), selfUserId, ConversationType.OneToOne)
  val team1to1Conv  = ConversationData(ConvId("team-1:1"), RConvId("team-1:1"), Some(Name("1:1 Team Conv")), selfUserId, ConversationType.Group, Some(TeamId("team-id"))) //all team convs are goup by type
  val groupConv     = ConversationData(ConvId("group-conv"), RConvId("group-conv"), Some(Name("Group Conv")), selfUserId, ConversationType.Group)
  val teamGroupConv = ConversationData(ConvId("team-group-conv"), RConvId("team-group-conv"), Some(Name("Group Team Conv")), selfUserId, ConversationType.Group, Some(TeamId("team-id"))) //all team convs are goup by type

  lazy val service: CallingServiceImpl = initCallingService()

  scenario("CallingService intialization") {
    val handle = Uint32_t(1)
    val service = initCallingService(handle)
    result(service.wCall) shouldEqual handle
  }

  feature("Incoming 1:1 calls") {

    val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == OtherCalling  && c.startTime == LocalInstant(Instant.EPOCH)))
    val checkpoint2 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfJoining   && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 10.seconds))))
    val checkpoint3 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfConnected && c.allParticipants == Set(otherUser) && c.estabTime.contains(LocalInstant(Instant.EPOCH + 20.seconds))))
    val checkpoint4 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == Terminating   && c.allParticipants == Set(otherUser) && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))))
    val checkpoint5 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)      && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))), _.isEmpty)

    def progressToSelfConnected(): Unit = {
      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      awaitCP(checkpoint1)

      clock.advance(10.seconds)

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      val callJoined = Signal(false)
      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        callJoined.filter(identity).head.foreach { _ =>
          clock.advance(10.seconds)
          service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
          service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
        }
      }

      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint2)

      callJoined ! true

      awaitCP(checkpoint3)
    }

    scenario("Incoming 1:1 call, self user ends the call - close_h returns before dismissal") {
      progressToSelfConnected()
      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      }

      clock.advance(10.seconds)
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      service.dismissCall()
      awaitCP(checkpoint5)
    }

    scenario("Incoming 1:1 call, self user ends the call - close_h returns after dismissal") {
      progressToSelfConnected()

      val dismissedCall = Signal(false)

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        dismissedCall.filter(identity).head.foreach { _ =>
          service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
        }
      }

      clock.advance(10.seconds)
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      await(service.dismissCall())

      dismissedCall ! true

      awaitCP(checkpoint5)
    }

    scenario("Incoming 1:1 call, other use ends call") {
      progressToSelfConnected()

      clock.advance(10.seconds)
      service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      await(service.dismissCall())

      awaitCP(checkpoint5)
    }

    scenario("Incoming 1:1 call, other use ends call with skipTerminating set to true") {
      await(globalPrefs(SkipTerminatingState) := true)
      progressToSelfConnected()

      clock.advance(10.seconds)
      service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint5)
    }

    scenario("Team conversation with only 1 other member should be treated as 1:1 conversation - incoming") {
      val checkpoint1 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == OtherCalling  && c.startTime == LocalInstant(Instant.EPOCH)))
      val checkpoint2 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == SelfJoining   && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 10.seconds))))
      val checkpoint3 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == SelfConnected && c.allParticipants == Set(otherUser) && c.estabTime.contains(LocalInstant(Instant.EPOCH + 20.seconds))))
      val checkpoint4 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == Terminating   && c.allParticipants == Set(otherUser) && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))))
      val checkpoint5 = callCheckpoint(_.get(team1to1Conv.id).exists(c => c.convId == team1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)      && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))), _.isEmpty)

      service.onIncomingCall(team1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      awaitCP(checkpoint1)

      clock.advance(10.seconds)

      (convsService.activeMembersData _).expects(team1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, team1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      val callJoined = Signal(false)
      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        callJoined.filter(identity).head.foreach { _ =>
          clock.advance(10.seconds)
          service.onEstablishedCall(team1to1Conv.remoteId, otherUserId)
          service.onParticipantsChanged(team1to1Conv.remoteId, Set(otherUser))
        }
      }

      service.startCall(team1to1Conv.id)
      awaitCP(checkpoint2)

      callJoined ! true

      awaitCP(checkpoint3)

      (avs.endCall _).expects(*, RConvQualifiedId(team1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(AvsClosedReason.Normal, team1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      }

      clock.advance(10.seconds)
      service.endCall(team1to1Conv.id)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      service.dismissCall()
      awaitCP(checkpoint5)
    }

    scenario("Reject incoming 1:1 call should set it to ongoing, and after close_h has been called it should be Ended, it should have skipped the Terminating phase, and the end time should be empty") {

      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(cur => cur.convId == _1to1Conv.id && cur.state == OtherCalling))
      val checkpoint2 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ongoing), _.isEmpty)
      val checkpoint3 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal) && c.endTime.isEmpty), _.isEmpty)

      var terminatingPhaseEntered = false
      service.currentCall.map(_.map(_.state)).foreach {
        case Some(Terminating) => terminatingPhaseEntered = true
        case _ =>
      }

      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      awaitCP(checkpoint1)

      (avs.rejectCall _).expects(*, *).once()
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint2)

      //Avs gives us reason StillOngoing, state shouldn't have changed
      service.onClosedCall(StillOngoing, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint2)

      //other side stops or call times out
      service.onClosedCall(Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint3)

      terminatingPhaseEntered shouldEqual false
    }
  }

  feature("Outgoing 1:1 calls") {

    val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfCalling && c.startTime == LocalInstant(Instant.EPOCH)))
    val checkpoint2 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfJoining && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 10.seconds))))
    val checkpoint3 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfConnected && c.allParticipants == Set(otherUser) && c.estabTime.contains(LocalInstant(Instant.EPOCH + 20.seconds))))
    val checkpoint4 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == Terminating   && c.allParticipants == Set(otherUser) && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))))
    val checkpoint5 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)      && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))), _.isEmpty)

    def progressToSelfConnected(conv: ConversationData = _1to1Conv): Unit = {
      (convsService.activeMembersData _).expects(conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, RConvQualifiedId(conv.remoteId), WCallType.Normal, WCallConvType.OneOnOne, *).once().returning(Future(0))
      service.startCall(conv.id, isVideo = false, forceOption = false)
      awaitCP(checkpoint1)

      clock.advance(10.seconds)
      service.onOtherSideAnsweredCall(conv.remoteId)
      awaitCP(checkpoint2)

      clock.advance(10.seconds)
      service.onEstablishedCall(conv.remoteId, otherUserId)
      service.onParticipantsChanged(conv.remoteId, Set(otherUser))
      awaitCP(checkpoint3)
    }

    scenario("Outgoing 1:1 call, self user ends the call - close_h returns before dismissal") {
      progressToSelfConnected()

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      }

      clock.advance(10.seconds)
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      service.dismissCall()
      awaitCP(checkpoint5)
    }

    scenario("Outgoing 1:1 call, self user ends call with skipTerminating set to true") {
      await(globalPrefs(SkipTerminatingState) := true)
      progressToSelfConnected()

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      }

      clock.advance(10.seconds)
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint5)
    }

    scenario("Outgoing 1:1 call, self user ends the call - close_h returns after dismissal") {
      progressToSelfConnected()

      val dismissedCall = Signal(false)

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        dismissedCall.filter(identity).head.foreach { _ =>
          service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
        }
      }

      clock.advance(10.seconds)
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      await(service.dismissCall())

      dismissedCall ! true

      awaitCP(checkpoint5)
    }

    scenario("Outgoing 1:1 call, other use ends call") {
      progressToSelfConnected()

      clock.advance(10.seconds)
      service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      await(service.dismissCall())

      awaitCP(checkpoint5)
    }

    scenario("Team conversation with only 1 other member should be treated as 1:1 conversation") {
      val checkpoint1 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == SelfCalling && c.startTime == LocalInstant(Instant.EPOCH)))
      val checkpoint2 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == SelfJoining && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 10.seconds))))
      val checkpoint3 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == SelfConnected && c.allParticipants == Set(otherUser) && c.estabTime.contains(LocalInstant(Instant.EPOCH + 20.seconds))))
      val checkpoint4 = callCheckpoint(_.contains(team1to1Conv.id), _.exists(c => c.convId == team1to1Conv.id && c.state == Terminating   && c.allParticipants == Set(otherUser) && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))))
      val checkpoint5 = callCheckpoint(_.get(team1to1Conv.id).exists(c => c.convId == team1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)      && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))), _.isEmpty)

      (convsService.activeMembersData _).expects(team1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, team1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, RConvQualifiedId(team1to1Conv.remoteId), WCallType.Normal, WCallConvType.OneOnOne, *).once().returning(Future(0))
      service.startCall(team1to1Conv.id, isVideo = false, forceOption = false)
      awaitCP(checkpoint1)

      clock.advance(10.seconds)
      service.onOtherSideAnsweredCall(team1to1Conv.remoteId)
      awaitCP(checkpoint2)

      clock.advance(10.seconds)
      service.onEstablishedCall(team1to1Conv.remoteId, otherUserId)
      service.onParticipantsChanged(team1to1Conv.remoteId, Set(otherUser))
      awaitCP(checkpoint3)

      (avs.endCall _).expects(*, RConvQualifiedId(team1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(AvsClosedReason.Normal, team1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      }

      clock.advance(10.seconds)
      service.endCall(team1to1Conv.id)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      service.dismissCall()
      awaitCP(checkpoint5)
    }

    scenario("Cancelling outgoing call should skip terminating phase") {

      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(cur => cur.convId == _1to1Conv.id && cur.state == SelfCalling))
      val checkpoint2 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)), _.isEmpty)

      var terminatingPhaseEntered = false
      service.currentCall.map(_.map(_.state)).foreach {
        case Some(Terminating) => terminatingPhaseEntered = true
        case _ =>
      }

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId), WCallType.Normal, WCallConvType.OneOnOne, *).once().returning(Future(0))
      service.startCall(_1to1Conv.id, isVideo = false, forceOption = false)
      awaitCP(checkpoint1)

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      }

      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint2)

      terminatingPhaseEntered shouldEqual false
    }
  }

  feature("Group calls") {

    scenario("Incoming group call goes through OtherCalling to SelfJoining to become SelfConnected") {

      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == OtherCalling))
      val checkpoint2 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfJoining))
      val checkpoint3 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfConnected  && cur.allParticipants == Set(otherUser, otherUser2)))

      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)

      awaitCP(checkpoint1)

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(groupConv.remoteId, otherUserId)
        service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      }

      service.startCall(groupConv.id)
      awaitCP(checkpoint2)
      awaitCP(checkpoint3)
    }

    scenario("Outgoing group call goes through SelfCalling to SelfJoining to SelfConnected and other users join at different times") {
      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfCalling && cur.caller == selfUserId))
      val checkpoint2 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfJoining && cur.caller == selfUserId))
      val checkpoint3 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfConnected && cur.caller == selfUserId && cur.allParticipants == Set(otherUser)))
      val checkpoint4 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfConnected && cur.caller == selfUserId && cur.allParticipants == Set(otherUser, otherUser2)))

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, *, *, *, *).once().returning(Future(0))

      service.startCall(groupConv.id)
      awaitCP(checkpoint1)

      service.onOtherSideAnsweredCall(groupConv.remoteId)
      awaitCP(checkpoint2)

      service.onEstablishedCall(groupConv.remoteId, otherUserId)
      service.onParticipantsChanged(groupConv.remoteId, Set(otherUser))
      awaitCP(checkpoint3)

      service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      awaitCP(checkpoint4)
    }

    scenario("Group Team conversation treated as group call") {
      val checkpoint1 = callCheckpoint(_.contains(teamGroupConv.id), _.exists(cur => cur.convId == teamGroupConv.id && cur.state == SelfCalling && cur.caller == selfUserId))
      val checkpoint2 = callCheckpoint(_.contains(teamGroupConv.id), _.exists(cur => cur.convId == teamGroupConv.id && cur.state == SelfJoining && cur.caller == selfUserId))
      val checkpoint3 = callCheckpoint(_.contains(teamGroupConv.id), _.exists(cur => cur.convId == teamGroupConv.id && cur.state == SelfConnected && cur.caller == selfUserId && cur.allParticipants == Set(otherUser)))
      val checkpoint4 = callCheckpoint(_.contains(teamGroupConv.id), _.exists(cur => cur.convId == teamGroupConv.id && cur.state == SelfConnected && cur.caller == selfUserId && cur.allParticipants == Set(otherUser, otherUser2)))

      (convsService.activeMembersData _).expects(teamGroupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, teamGroupConv.id, "member"),
          ConversationMemberData(otherUser2Id, teamGroupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, *, *, *, *).once().returning(Future(0))

      service.startCall(teamGroupConv.id)
      awaitCP(checkpoint1)

      service.onOtherSideAnsweredCall(teamGroupConv.remoteId)
      awaitCP(checkpoint2)

      service.onEstablishedCall(teamGroupConv.remoteId, otherUserId)
      service.onParticipantsChanged(teamGroupConv.remoteId, Set(otherUser))
      awaitCP(checkpoint3)

      service.onParticipantsChanged(teamGroupConv.remoteId, Set(otherUser, otherUser2))
      awaitCP(checkpoint4)
    }

    scenario("Leave a group call and it will continue running in the background with state Ongoing - established time should not be affected") {
      val estTime = LocalInstant(clock.instant() + 10.seconds)

      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.exists(c => c.state == SelfConnected && c.estabTime.contains(estTime)))
      val checkpoint2 = callCheckpoint(_.get(groupConv.id).exists(c => c.state == Ongoing && c.estabTime.contains(estTime)), _.isEmpty)


      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)

      clock + 10.seconds

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(groupConv.remoteId, otherUserId)
        service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      }
      service.startCall(groupConv.id)
      awaitCP(checkpoint1)

      (avs.endCall _).expects(*, *).once().onCall { (rId, isGroup) =>
        service.onClosedCall(StillOngoing, groupConv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      }

      clock + 10.seconds

      service.endCall(groupConv.id)
      awaitCP(checkpoint2)
    }

    scenario("Incoming group call answered on another device") {
      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.exists(_.state == OtherCalling))
      val checkpoint2 = callCheckpoint(_.get(groupConv.id).exists(c => c.state == Ended && c.endReason.contains(AvsClosedReason.AnsweredElsewhere)), _.isEmpty)

      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      awaitCP(checkpoint1)

      service.onClosedCall(AnsweredElsewhere, groupConv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint2)
    }

    scenario("Cancel outgoing group call should set it to Ended") {
      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfCalling && cur.caller == selfUserId))
      val checkpoint2 = callCheckpoint(_.get(groupConv.id).exists(c => c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)), _.isEmpty)

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, *, *, *, *).once().returning(Future(0))

      service.startCall(groupConv.id)
      awaitCP(checkpoint1)

      (avs.endCall _).expects(*, RConvQualifiedId(groupConv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(Normal, groupConv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      }
      service.endCall(groupConv.id)
      awaitCP(checkpoint2)
    }
  }

  feature("Ongoing group calls") {

    val checkpoint1  = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == OtherCalling))
    val checkpoint2  = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfJoining))
    val checkpoint3  = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfConnected  && cur.allParticipants == Set(otherUser, otherUser2)))
    val checkpoint3a = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == Terminating))
    val checkpoint4  = callCheckpoint(_.get(groupConv.id).exists(cur => cur.state == Ongoing  && cur.allParticipants == Set(otherUser, otherUser2)), _.isEmpty)
    val checkpoint5  = checkpoint4
    val checkpoint6  = callCheckpoint(_.get(groupConv.id).exists(cur => cur.state == Ended), _.isEmpty)

    val checkpoint7  = callCheckpoint(_.get(groupConv.id).exists(cur => cur.state == Ongoing && cur.allParticipants == Set(otherUser)), _.isEmpty)
    val checkpoint8  = callCheckpoint(_.get(groupConv.id).exists(cur => cur.state == Ongoing && cur.allParticipants == Set(otherUser, otherUser2)), _.isEmpty)
    val checkpoint9  = checkpoint8

    scenario("Leaving a group call with more than 1 other member should put the call into the Ongoing state if we skip terminating") {
      await(globalPrefs(SkipTerminatingState) := true)

      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)

      awaitCP(checkpoint1)

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(groupConv.remoteId, otherUserId)
        service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      }

      service.startCall(groupConv.id)
      awaitCP(checkpoint2)
      awaitCP(checkpoint3)

      (avs.endCall _).expects(*, RConvQualifiedId(groupConv.remoteId)).once()

      service.endCall(groupConv.id)

      awaitCP(checkpoint4)
      service.onClosedCall(StillOngoing, groupConv.remoteId, RemoteInstant(clock.instant()), selfUserId)

      awaitCP(checkpoint5)

      service.onClosedCall(Normal, groupConv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      awaitCP(checkpoint6)
    }

    scenario("Leaving a group call with more than 1 other member should put the call into the Ongoing state after the terminating state") {
      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)

      awaitCP(checkpoint1)

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(groupConv.remoteId, otherUserId)
        service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      }

      service.startCall(groupConv.id)
      awaitCP(checkpoint2)
      awaitCP(checkpoint3)

      (avs.endCall _).expects(*, RConvQualifiedId(groupConv.remoteId)).once()

      service.endCall(groupConv.id)

      awaitCP(checkpoint3a)
      service.dismissCall()

      awaitCP(checkpoint4)
      service.onClosedCall(StillOngoing, groupConv.remoteId, RemoteInstant(clock.instant()), selfUserId)

      awaitCP(checkpoint5)

      service.onClosedCall(Normal, groupConv.remoteId, RemoteInstant(clock.instant()), selfUserId)
      awaitCP(checkpoint6)

      //TODO now what happens if the user rejoins the call before the other side hangs up??
    }

    scenario("If a user joins an ongoing group call in the background, it shouldn't be bumped to active") {
      await(globalPrefs(SkipTerminatingState) := true)
      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      service.onParticipantsChanged(groupConv.remoteId, Set(otherUser))

      service.endCall(groupConv.id)
      service.dismissCall()
      service.onClosedCall(StillOngoing, groupConv.remoteId, RemoteInstant(clock.instant()), selfUserId)

      awaitCP(checkpoint7)

      service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))

      awaitCP(checkpoint8)

      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = false, isConferenceCall = false) //Group check message gets triggered after a bit

      awaitCP(checkpoint9)
    }
  }

  feature("Ending calls") {

    scenario("Chaining a startCall after endCall should wait for onClosedCallback and successfully start second call, terminating state should be skipped") {
      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), cur => cur.exists(_.state == SelfConnected) && cur.exists(_.allParticipants.contains(otherUser)))
      //hang up first call and start second call, first call should be replaced
      val checkpoint2 = callCheckpoint(_.contains(_1to1Conv2.id), cur => cur.exists(_.state == SelfCalling) && cur.exists(_.allParticipants.isEmpty))
      val checkpoint3 = callCheckpoint(_.contains(_1to1Conv2.id), cur => cur.exists(_.state == SelfConnected) && cur.exists(_.allParticipants.contains(otherUser2)))

      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
        service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
      }

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint1)

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      }

      (convsService.activeMembersData _).expects(_1to1Conv2.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUser2Id, _1to1Conv2.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, RConvQualifiedId(_1to1Conv2.remoteId), *, WCallConvType.OneOnOne, false).once().returning(Future(0))

      for {
        _ <- service.endCall(_1to1Conv.id, skipTerminating = true)
        _ <- service.startCall(_1to1Conv2.id)
      } yield {}

      awaitCP(checkpoint2)

      service.onOtherSideAnsweredCall(_1to1Conv2.remoteId)
      service.onEstablishedCall(_1to1Conv2.remoteId, otherUser2Id)
      service.onParticipantsChanged(_1to1Conv2.remoteId, Set(otherUser2))

      awaitCP(checkpoint3)
    }

    scenario("End a call and later start another call in the same conversation") {

      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfCalling && c.startTime == LocalInstant(Instant.EPOCH)))
      val checkpoint2 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfJoining && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 10.seconds))))
      val checkpoint3 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfConnected && c.allParticipants == Set(otherUser) && c.estabTime.contains(LocalInstant(Instant.EPOCH + 20.seconds))))
      val checkpoint4 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == Terminating   && c.allParticipants == Set(otherUser) && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))))
      val checkpoint5 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)      && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))), _.isEmpty)

      val checkpoint6 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfCalling && c.startTime == LocalInstant(Instant.EPOCH + 50.seconds)))
      val checkpoint7 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfJoining && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 60.seconds))))

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId), WCallType.Normal, WCallConvType.OneOnOne, *).twice().returning(Future(0))
      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint1)

      clock.advance(10.seconds)
      service.onOtherSideAnsweredCall(_1to1Conv.remoteId)
      awaitCP(checkpoint2)

      clock.advance(10.seconds)
      service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
      service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
      awaitCP(checkpoint3)

      clock.advance(10.seconds) //other side ends call
      service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      await(service.dismissCall())
      awaitCP(checkpoint5)

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      clock.advance(10.seconds)
      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint6)

      clock.advance(10.seconds)
      service.onOtherSideAnsweredCall(_1to1Conv.remoteId)
      awaitCP(checkpoint7)
    }

    scenario("End a call and later receive another call in the same conversation") {

      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfCalling && c.startTime == LocalInstant(Instant.EPOCH)))
      val checkpoint2 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfJoining && c.joinedTime.contains(LocalInstant(Instant.EPOCH + 10.seconds))))
      val checkpoint3 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == SelfConnected && c.allParticipants == Set(otherUser) && c.estabTime.contains(LocalInstant(Instant.EPOCH + 20.seconds))))
      val checkpoint4 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == Terminating   && c.allParticipants == Set(otherUser) && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))))
      val checkpoint5 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.convId == _1to1Conv.id && c.state == Ended && c.endReason.contains(AvsClosedReason.Normal)      && c.endTime.contains(LocalInstant(Instant.EPOCH + 30.seconds))), _.isEmpty)

      val checkpoint6 = callCheckpoint(_.contains(_1to1Conv.id), _.exists(c => c.convId == _1to1Conv.id && c.state == OtherCalling && c.startTime == LocalInstant(Instant.EPOCH + 50.seconds)))

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.startCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId), WCallType.Normal, WCallConvType.OneOnOne, *).once().returning(Future(0))
      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint1)

      clock.advance(10.seconds)
      service.onOtherSideAnsweredCall(_1to1Conv.remoteId)
      awaitCP(checkpoint2)

      clock.advance(10.seconds)
      service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
      service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
      awaitCP(checkpoint3)

      clock.advance(10.seconds) //other side ends call
      service.onClosedCall(AvsClosedReason.Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      awaitCP(checkpoint4)

      clock.advance(10.seconds)
      await(service.dismissCall())
      awaitCP(checkpoint5)

      clock.advance(10.seconds)
      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      awaitCP(checkpoint6)
    }
  }

  feature("Simultaneous calls") {

    scenario("Receive incoming call while 1:1 call ongoing - should become active if ongoing call is dropped by self user and terminating state should be skipped") {

      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), cur => cur.exists(_.state == SelfConnected) && cur.exists(_.allParticipants.contains(otherUser)))
      //Both calls should be in available calls, but the ongoing call should be current
      val checkpoint2 = callCheckpoint({ cs => cs.contains(_1to1Conv.id) && cs.get(_1to1Conv2.id).exists(_.state == OtherCalling )}, c => c.exists(_.state == SelfConnected ) && c.exists(_.allParticipants.contains(otherUser)))
      //Hang up the ongoing call - incoming 1:1 call should become current
      val checkpoint3 = callCheckpoint(_.contains(_1to1Conv2.id), cur => cur.exists(_.state == OtherCalling) && cur.exists(_.allParticipants.isEmpty))

      var terminatingPhaseEntered = false
      service.currentCall.map(_.map(_.state)).foreach {
        case Some(Terminating) => terminatingPhaseEntered = true
        case _ =>
      }

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
        service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
      }
      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint1)

      service.onIncomingCall(_1to1Conv2.remoteId, otherUser2Id, videoCall = false, shouldRing = true, isConferenceCall = false) //Receive the second call after first is established
      awaitCP(checkpoint2)

      (avs.endCall _).expects(*, RConvQualifiedId(_1to1Conv.remoteId)).once().onCall { (_, _) =>
        service.onClosedCall(Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      }
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint3)

      terminatingPhaseEntered shouldEqual false
    }

    scenario("Receive incoming call while 1:1 call ongoing - should become active if ongoing call is dropped by other user and terminating state should be skipped") {

      val checkpoint1 = callCheckpoint(_.contains(_1to1Conv.id), cur => cur.exists(_.state == SelfConnected) && cur.exists(_.allParticipants.contains(otherUser)))
      //Both calls should be in available calls, but the ongoing call should be current
      val checkpoint2 = callCheckpoint({ cs => cs.contains(_1to1Conv.id) && cs.get(_1to1Conv2.id).exists(_.state == OtherCalling )}, c => c.exists(_.state == SelfConnected ) && c.exists(_.allParticipants.contains(otherUser)))
      //Hang up the ongoing call - incoming 1:1 call should become current
      val checkpoint3 = callCheckpoint(_.contains(_1to1Conv2.id), cur => cur.exists(_.state == OtherCalling) && cur.exists(_.allParticipants.isEmpty))

      var terminatingPhaseEntered = false
      service.currentCall.map(_.map(_.state)).foreach {
        case Some(Terminating) => terminatingPhaseEntered = true
        case _ =>
      }

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
        service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
      }
      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint1)

      service.onIncomingCall(_1to1Conv2.remoteId, otherUser2Id, videoCall = false, shouldRing = true, isConferenceCall = false) //Receive the second call after first is established
      awaitCP(checkpoint2)

      service.onClosedCall(Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)

      awaitCP(checkpoint3)

      terminatingPhaseEntered shouldEqual false
    }

    scenario("With a background group call, receive a 1:1 call, finish it, and then still join the group call afterwards - we should go through terminating state") {

      //Receive and reject a group call
      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.isEmpty)

      //Receive and accept a 1:1 call
      val checkpoint2 = callCheckpoint(act => act.contains(groupConv.id) && act.contains(_1to1Conv.id), _.exists(c => c.allParticipants.contains(otherUser) && c.state == SelfConnected))

      //1:1 call is finished, but hasn't been dismissed
      val checkpoint3 = callCheckpoint(_.contains(groupConv.id), _.exists(_.state == Terminating))

      //1:1 call is dismissed
      val checkpoint4 = callCheckpoint(_.contains(groupConv.id), _.isEmpty)

      //Join group call
      val checkpoint5 = callCheckpoint(_.contains(groupConv.id), _.exists(c => c.allParticipants == Set(otherUser, otherUser2) && c.state == SelfConnected))

      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      (avs.rejectCall _).expects(*, *).anyNumberOfTimes().onCall { (_, _) =>
        service.onClosedCall(StillOngoing, groupConv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      }
      service.endCall(groupConv.id) //user rejects the group call
      awaitCP(checkpoint1)

      service.onIncomingCall(_1to1Conv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)
      (avs.answerCall _).expects(*, *, *, *).once().onCall { (rId, _, _, _) =>
        service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
        service.onParticipantsChanged(_1to1Conv.remoteId, Set(otherUser))
      }

      (convsService.activeMembersData _).expects(_1to1Conv.id).once().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      service.startCall(_1to1Conv.id) //user accepts 1:1 call
      awaitCP(checkpoint2)

      (avs.endCall _).expects(*, *).once().onCall { (rId, _) =>
        service.onClosedCall(Normal, _1to1Conv.remoteId, RemoteInstant(clock.instant()), otherUserId)
      }
      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint3)

      service.dismissCall()
      awaitCP(checkpoint4)

      (avs.answerCall _).expects(*, *, *, *).once().onCall { (rId, _, _, _) =>
        service.onEstablishedCall(groupConv.remoteId, otherUserId)
        service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      }

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(
          Seq(ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      service.startCall(groupConv.id)

      awaitCP(checkpoint5)
    }
  }

  feature("tracking") {

    scenario("Toggling audio or video state during a call sets wasVideoToggled to true for the rest of the call") {

      val checkpoint1 = callCheckpoint(_.nonEmpty, _.exists(_.state == SelfCalling))
      val checkpoint2 = callCheckpoint(_.nonEmpty, _.exists(_.state == SelfJoining))
      val checkpoint3 = callCheckpoint(_.nonEmpty, _.exists(_.state == SelfConnected))
      val checkpoint4 = callCheckpoint(_.nonEmpty, _.exists(_.isVideoCall))
      val checkpoint5 = callCheckpoint(_.nonEmpty, _.exists(_.state == Terminating))
      val checkpoint6 = callCheckpoint(_.get(_1to1Conv.id).exists(c => c.state == Ended && c.wasVideoToggled), _.isEmpty)

      (convsService.activeMembersData _).expects(_1to1Conv.id).atLeastOnce().returning(
        Signal(Seq(ConversationMemberData(otherUserId, _1to1Conv.id, "member")))
      )

      (permissions.ensurePermissions _).expects(*).atLeastOnce().returning(Future.successful(()))

      (avs.startCall _).expects(*, *, *, *, *).once().returning(Future(0))
      (avs.setVideoSendState _).expects(*, *, *).once()

      service.startCall(_1to1Conv.id)
      awaitCP(checkpoint1)

      service.onOtherSideAnsweredCall(_1to1Conv.remoteId)
      awaitCP(checkpoint2)

      service.onEstablishedCall(_1to1Conv.remoteId, otherUserId)
      awaitCP(checkpoint3)

      service.setVideoSendState(_1to1Conv.id, VideoState.Started, shouldUpdateVideoState = true)
      awaitCP(checkpoint4)

      (avs.endCall _).expects(*, *).once().onCall { (_: WCall, rConvQualifiedId: RConvQualifiedId) =>
        service.onClosedCall(Avs.AvsClosedReason.Normal, rConvQualifiedId.id, RemoteInstant(clock.instant()), selfUserId)
      }

      service.endCall(_1to1Conv.id)
      awaitCP(checkpoint5)

      service.dismissCall()
      awaitCP(checkpoint6)
    }
  }

  feature("Targeted call messages") {

    def progressToSelfConnected(): Unit = {
      val checkpoint1 = callCheckpoint(_.contains(groupConv.id), _.exists(cur => cur.convId == groupConv.id && cur.state == SelfConnected  && cur.allParticipants == Set(otherUser, otherUser2)))

      service.onIncomingCall(groupConv.remoteId, otherUserId, videoCall = false, shouldRing = true, isConferenceCall = false)

      (convsService.activeMembersData _).expects(groupConv.id).once().returning(
        Signal(Seq(
          ConversationMemberData(otherUserId, groupConv.id, "member"),
          ConversationMemberData(otherUser2Id, groupConv.id, "member")
        ))
      )

      (permissions.ensurePermissions _).expects(*).once().returning(Future.successful(()))

      (avs.answerCall _).expects(*, *, *, *).once().onCall { (_, _, _, _) =>
        service.onEstablishedCall(groupConv.remoteId, otherUserId)
        service.onParticipantsChanged(groupConv.remoteId, Set(otherUser, otherUser2))
      }

      service.startCall(groupConv.id)
      awaitCP(checkpoint1)
    }

    scenario("Messages are targeted if target recipients are specified") {
      val expectedTargetRecipients =
        TargetRecipients.SpecificClients(OtrClientIdMap.from(otherUser.qualifiedId.id -> Set(otherUser.clientId)))

      (otrSyncHandler.postOtrMessage _)
        .expects(groupConv.id, *, *, expectedTargetRecipients, *, *)
        .once()
        .returning(Future.successful(Right(RemoteInstant(Instant.now(clock)))))

      val ctx = Pointer.createConstant(0)
      val targetRecipients = AvsClientList(Seq(AvsClient(otherUser.qualifiedId.id.str, otherUser.clientId.str)))

      progressToSelfConnected()
      result(service.onSend(ctx, msg = "", groupConv.remoteId, Some(targetRecipients)))
    }

    scenario("Messages are not targeted if no target recipients are specified") {
      (otrSyncHandler.postOtrMessage _)
        .expects(groupConv.id, *, *, TargetRecipients.ConversationParticipants, *, *)
        .once()
        .returning(Future.successful(Right(RemoteInstant(Instant.now(clock)))))

      val ctx = Pointer.createConstant(0)

      progressToSelfConnected()
      result(service.onSend(ctx, msg = "", groupConv.remoteId, targetRecipients = None))
    }
  }

  var cpCount = 0
  def awaitCP(cp: CallStateCheckpoint) = {
    cpCount += 1
    try {
      result(cp.head)
    } catch {
      case NonFatal(e) =>
        fail(s"Checkpoint $cpCount didn't match state: ${service.callProfile.currentValue}", e)
    }
    cp.unsubscribeAll()
  }

  def callCheckpoint(callsCheck: Map[ConvId, CallInfo] => Boolean, currentCheck: Option[CallInfo] => Boolean): CallStateCheckpoint =
    (for {
      calls <- service.calls
      current <- service.currentCall
    } yield (calls, current))
      .filter { case (calls, current) => callsCheck(calls) && currentCheck(current) }
      .disableAutowiring()

  def signalTest[A](signal: Signal[A])(test: A => Boolean)(trigger: => Unit): Unit = {
    signal.disableAutowiring()
    trigger
    result(signal.filter(test).head)
  }

  def initCallingService(wCall: WCall = Uint32_t(1)) = {
    val prefs = new TestUserPreferences()

    (convs.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
      Future.successful {
        if (id == _1to1Conv.remoteId)          Some(_1to1Conv)
        else if (id == _1to1Conv2.remoteId)    Some(_1to1Conv2)
        else if (id == groupConv.remoteId)     Some(groupConv)
        else if (id == team1to1Conv.remoteId)  Some(team1to1Conv)
        else if (id == teamGroupConv.remoteId) Some(teamGroupConv)
        else fail(s"Unexpected conversation id: $id")
      }
    }

    (convs.convById _).expects(*).anyNumberOfTimes().onCall { id: ConvId =>
      Future.successful {
        if (id == _1to1Conv.id)          Some(_1to1Conv)
        else if (id == _1to1Conv2.id)    Some(_1to1Conv2)
        else if (id == groupConv.id)     Some(groupConv)
        else if (id == team1to1Conv.id)  Some(team1to1Conv)
        else if (id == teamGroupConv.id) Some(teamGroupConv)
        else fail(s"Unexpected conversation id: $id")
      }
    }
    (convsService.isGroupConversation _).expects(*).anyNumberOfTimes().onCall { id: ConvId =>
      Future.successful {
        if (id == _1to1Conv.id)     false
        else if (id == _1to1Conv2.id)    false
        else if (id == groupConv.id)     true
        else if (id == team1to1Conv.id)  false
        else if (id == teamGroupConv.id) true
        else fail(s"Unexpected conversation id: $id")
      }
    }

    (members.getActiveUsers _).expects(*).anyNumberOfTimes().onCall { id: ConvId =>
      val others =
        if (id == _1to1Conv.id)    Set(otherUserId)
        else if (id == _1to1Conv2.id)    Set(otherUser2Id)
        else if (id == groupConv.id)     Set(otherUserId, otherUser2Id)
        else if (id == team1to1Conv.id)  Set(otherUserId)
        else if (id == teamGroupConv.id) Set(otherUserId, otherUser2Id)
        else fail(s"Unexpected conversation id: $id")

      Future.successful((others + selfUserId).toSeq)
    }

    (permissions.allPermissions _).expects(*).anyNumberOfTimes().returning(Signal.const(true))

    (flows.flowManager _).expects().once().returning(None)
    (messages.addMissedCallMessage(_:RConvId, _:UserId, _:RemoteInstant)).expects(*, *, *).anyNumberOfTimes().returning(Future.successful(None))
    (messages.addMissedCallMessage(_:ConvId, _:UserId, _:RemoteInstant)).expects(*, *, *).anyNumberOfTimes().returning(Future.successful(None))
    (messages.addSuccessfulCallMessage _).expects(*, *, *, *).anyNumberOfTimes().returning(Future.successful(None))
    (network.networkMode _).expects().once().returning(Signal.empty[NetworkMode])
    (push.beDrift _).expects().anyNumberOfTimes().returning(Signal.const(Duration.ZERO))

    (avs.registerAccount _).expects(*).once().returning(Future.successful(wCall))

    val s = new CallingServiceImpl(
      selfUserId, selfClientId, domain, BackendConfigFixture.backendSignal, null, avs, convs, convsService, members, otrSyncHandler,
      flows, messages, media, push, network, prefs, globalPrefs, permissions, httpProxy = None
    )
    result(s.wCall)
    s
  }
}

object CallingServiceSpec {

  type CallStateCheckpoint = Signal[(Map[ConvId, CallInfo], Option[CallInfo])]
}
