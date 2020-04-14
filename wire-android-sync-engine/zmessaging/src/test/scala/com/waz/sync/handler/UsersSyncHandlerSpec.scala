package com.waz.sync.handler

import com.waz.content.UsersStorage
import com.waz.model.nano.Messages
import com.waz.model._
import com.waz.service.UserService
import com.waz.service.assets.AssetService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.OtrClient.EncryptedContent
import com.waz.sync.client.UsersClient
import com.waz.sync.otr.OtrSyncHandler
import org.threeten.bp.Instant

import scala.concurrent.Future

class UsersSyncHandlerSpec extends AndroidFreeSpec {
  import UserData.ConnectionStatus._

  private val userService  = mock[UserService]
  private val usersStorage = mock[UsersStorage]
  private val assetService = mock[AssetService]
  private val usersClient  = mock[UsersClient]
  private val otrSync      = mock[OtrSyncHandler]

  val self = UserData("self")
  val teamId = TeamId()

  def handler: UsersSyncHandler = new UsersSyncHandlerImpl(
    userService, usersStorage, assetService, usersClient, otrSync
  )

  feature("Post availability status") {
    scenario("Post only to self and connected users if self is not in a team") {
      // given

      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(connection = Accepted)
      val user2 = UserData("user2").copy(connection = Blocked)
      val user3 = UserData("user3").copy(connection = Unconnected)
      val user4 = UserData("user4").copy(connection = PendingFromOther)
      (usersStorage.list _).expects().anyNumberOfTimes().returning(
        Future.successful(Seq(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          message.getAvailability.`type` shouldEqual Messages.Availability.AVAILABLE
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }

      // when
      result(handler.postAvailability(Availability.Available))
    }

    scenario("Post to all team users (also unconnected) if self is in the team") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId))
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Blocked)
      val user3 = UserData("user3").copy(teamId = Some(teamId), connection = Unconnected)
      val user4 = UserData("user4").copy(teamId = Some(teamId), connection = PendingFromOther)
      (usersStorage.list _).expects().anyNumberOfTimes().returning(
        Future.successful(Seq(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          message.getAvailability.`type` shouldEqual Messages.Availability.AVAILABLE
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id, user3.id, user4.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }

      // when
      result(handler.postAvailability(Availability.Available))
    }

    scenario("Post to to all team users and only connected non-team users") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId))
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Blocked)
      val user3 = UserData("user3").copy(teamId = Some(teamId), connection = Unconnected)
      val user4 = UserData("user4").copy(teamId = Some(teamId), connection = PendingFromOther)
      val user5 = UserData("user5").copy(connection = Accepted)
      val user6 = UserData("user6").copy(connection = Unconnected)
      (usersStorage.list _).expects().anyNumberOfTimes().returning(
        Future.successful(Seq(user1, user2, user3, user4, user5, user6))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          message.getAvailability.`type` shouldEqual Messages.Availability.AVAILABLE
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id, user3.id, user4.id, user5.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }

      // when
      result(handler.postAvailability(Availability.Available))
    }

    scenario("Cut off some non-team users if limit is reached") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId))
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted)
      val user3 = UserData("user3").copy(connection = Accepted)
      val user4 = UserData("user4").copy(connection = Accepted)
      (usersStorage.list _).expects().anyNumberOfTimes().returning(
        Future.successful(Seq(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          message.getAvailability.`type` shouldEqual Messages.Availability.AVAILABLE
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id, user3.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }

      // when
      result(handler.postAvailability(Availability.Available, limit = 4))
    }


    scenario("Cut off all non-team users and then some team users if limit is reached") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId))
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted)
      val user3 = UserData("user3").copy(connection = Accepted)
      val user4 = UserData("user4").copy(connection = Accepted)
      (usersStorage.list _).expects().anyNumberOfTimes().returning(
        Future.successful(Seq(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          message.getAvailability.`type` shouldEqual Messages.Availability.AVAILABLE
          recipients shouldEqual Some(Set(self.id, user1.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }

      // when
      result(handler.postAvailability(Availability.Available, limit = 2))
    }
  }
}
