package com.waz.sync.handler

import com.waz.content.UsersStorage
import com.waz.model._
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.{UserSearchService, UserService}
import com.waz.service.assets.AssetService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.OtrClient.EncryptedContent
import com.waz.sync.client.TeamsClient.TeamMember
import com.waz.sync.client.UsersClient
import com.waz.sync.otr.OtrSyncHandler
import com.wire.signals.CancellableFuture
import org.scalamock.function.FunctionAdapter1
import org.threeten.bp.Instant

import scala.concurrent.Future

class UsersSyncHandlerSpec extends AndroidFreeSpec {
  import UserData.ConnectionStatus._

  val federationSupported: Boolean = false

  private val userService      = mock[UserService]
  private val usersStorage     = mock[UsersStorage]
  private val assetService     = mock[AssetService]
  private val searchService     = mock[UserSearchService]
  private val usersClient      = mock[UsersClient]
  private val otrSync          = mock[OtrSyncHandler]
  private val teamsSyncHandler = mock[TeamsSyncHandler]

  val domain = if (federationSupported) Domain("chala.wire.link") else Domain.Empty
  val self = UserData("self").copy(domain = domain)
  val teamId = TeamId()

  def handler: UsersSyncHandler = new UsersSyncHandlerImpl(
    FederationSupport(federationSupported), userService, usersStorage, assetService, searchService,
    usersClient, otrSync, Some(teamId), teamsSyncHandler
  )

  private def checkAvailabilityStatus(message: GenericMessage, expectedAvailability: Messages.Availability.Type): Unit = message.unpackContent match {
    case content: GenericContent.AvailabilityStatus =>
      content.proto.getType shouldEqual expectedAvailability
    case _ => fail(s"Availability should be set to $expectedAvailability}")
  }

  feature("Post availability status") {
    scenario("Post only to self and connected users if self is not in a team") {
      // given

      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(connection = Blocked, domain = domain)
      val user3 = UserData("user3").copy(connection = Unconnected, domain = domain)
      val user4 = UserData("user4").copy(connection = PendingFromOther, domain = domain)
      (usersStorage.values _).expects().anyNumberOfTimes().returning(
        Future.successful(Vector(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          checkAvailabilityStatus(message, Messages.Availability.Type.AVAILABLE)
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }
      (userService.isFederated(_ : UserData)).expects(*).anyNumberOfTimes().returning(false)

      // when
      result(handler.postAvailability(Availability.Available))
    }

    scenario("Post to all team users (also unconnected) if self is in the team") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId), domain = domain)
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Blocked, domain = domain)
      val user3 = UserData("user3").copy(teamId = Some(teamId), connection = Unconnected, domain = domain)
      val user4 = UserData("user4").copy(teamId = Some(teamId), connection = PendingFromOther, domain = domain)
      (usersStorage.values _).expects().anyNumberOfTimes().returning(
        Future.successful(Vector(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          checkAvailabilityStatus(message, Messages.Availability.Type.AVAILABLE)
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id, user3.id, user4.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }
      (userService.isFederated(_ : UserData)).expects(*).anyNumberOfTimes().returning(false)

      // when
      result(handler.postAvailability(Availability.Available))
    }

    scenario("Don't post to users from other backends") {
      if (federationSupported) {
        // given
        val domain = Domain("chala.wire.link")
        val otherDomain = Domain("anta.wire.link")
        val self = UserData("self").copy(teamId = Some(teamId), domain = domain)
        (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
          Future.successful(Some(self))
        )

        val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
        val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
        val user3 = UserData("user3").copy(teamId = Some(teamId), connection = Accepted, domain = otherDomain)
        (usersStorage.values _).expects().anyNumberOfTimes().returning(
          Future.successful(Vector(user1, user2, user3))
        )

        // then
        (userService.isFederated(_: UserData)).expects(*).anyNumberOfTimes().onCall { user: UserData =>
          user.domain != domain
        }

        (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
          (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
            checkAvailabilityStatus(message, Messages.Availability.Type.AVAILABLE)
            recipients shouldEqual Some(Set(self.id, user1.id, user2.id))
            Future.successful(Right(RemoteInstant(Instant.now())))
        }

        // when
        result(handler.postAvailability(Availability.Available))
      }
    }

    scenario("Post to to all team users and only connected non-team users") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId), domain = domain)
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Blocked, domain = domain)
      val user3 = UserData("user3").copy(teamId = Some(teamId), connection = Unconnected, domain = domain)
      val user4 = UserData("user4").copy(teamId = Some(teamId), connection = PendingFromOther, domain = domain)
      val user5 = UserData("user5").copy(connection = Accepted, domain = domain)
      val user6 = UserData("user6").copy(connection = Unconnected, domain = domain)
      (usersStorage.values _).expects().anyNumberOfTimes().returning(
        Future.successful(Vector(user1, user2, user3, user4, user5, user6))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          checkAvailabilityStatus(message, Messages.Availability.Type.AVAILABLE)
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id, user3.id, user4.id, user5.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }
      (userService.isFederated(_ : UserData)).expects(*).anyNumberOfTimes().returning(false)

      // when
      result(handler.postAvailability(Availability.Available))
    }

    scenario("Cut off some non-team users if limit is reached") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId), domain = domain)
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user3 = UserData("user3").copy(connection = Accepted, domain = domain)
      val user4 = UserData("user4").copy(connection = Accepted, domain = domain)
      (usersStorage.values _).expects().anyNumberOfTimes().returning(
        Future.successful(Vector(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          checkAvailabilityStatus(message, Messages.Availability.Type.AVAILABLE)
          recipients shouldEqual Some(Set(self.id, user1.id, user2.id, user3.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }
      (userService.isFederated(_ : UserData)).expects(*).anyNumberOfTimes().returning(false)

      // when
      result(handler.postAvailability(Availability.Available, limit = 4))
    }


    scenario("Cut off all non-team users and then some team users if limit is reached") {
      // given
      val self = UserData("self").copy(teamId = Some(teamId), domain = domain)
      (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
        Future.successful(Some(self))
      )

      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user3 = UserData("user3").copy(connection = Accepted, domain = domain)
      val user4 = UserData("user4").copy(connection = Accepted, domain = domain)
      (usersStorage.values _).expects().anyNumberOfTimes().returning(
        Future.successful(Vector(user1, user2, user3, user4))
      )

      // then
      (otrSync.broadcastMessage _).expects(*, *, *, *).once().onCall {
        (message: GenericMessage, _: Int, _: EncryptedContent, recipients: Option[Set[UserId]]) =>
          checkAvailabilityStatus(message, Messages.Availability.Type.AVAILABLE)
          recipients shouldEqual Some(Set(self.id, user1.id))
          Future.successful(Right(RemoteInstant(Instant.now())))
      }
      (userService.isFederated(_ : UserData)).expects(*).anyNumberOfTimes().returning(false)

      // when
      result(handler.postAvailability(Availability.Available, limit = 2))
    }

    def toUserInfo(user: UserData): UserInfo = UserInfo(
      id = user.id,
      teamId = user.teamId
    )

    def toTeamMember(user: UserData): TeamMember = TeamMember(user.id, None, None)

    scenario("Update search results when in a team") {
      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user3 = UserData("user3").copy(connection = Accepted, domain = domain)
      val user4 = UserData("user4").copy(connection = Accepted, domain = domain)
      val users = Seq(user1, user2, user3, user4).toIdMap

      (usersClient.loadUsers _)
        .expects(users.keys.toSeq)
        .anyNumberOfTimes()
        .onCall { userIds: Seq[UserId] =>
          CancellableFuture.successful(
            Right(users.filterKeys(userIds.contains).values.map(toUserInfo).toSeq)
          )
        }

      (teamsSyncHandler.getMembers _)
        .expects(users.filter(_._2.teamId.contains(teamId)).keys.toSeq)
        .anyNumberOfTimes()
        .onCall { userIds: Seq[UserId] =>
          Future.successful(
            users.filterKeys(userIds.contains).values.map(toTeamMember).toSeq
          )
        }

      def checkRemoteUsers(remoteUsers: Map[UserId, (UserInfo, Option[TeamMember])]): Boolean = {
        remoteUsers.size == 4 &&
          remoteUsers.keySet.contains(user1.id) &&
          remoteUsers(user1.id) == (toUserInfo(user1), Some(toTeamMember(user1))) &&
          remoteUsers.keySet.contains(user2.id) &&
          remoteUsers(user2.id) == (toUserInfo(user2), Some(toTeamMember(user2)))
        remoteUsers.keySet.contains(user3.id) &&
          remoteUsers(user3.id) == (toUserInfo(user3), None) &&
          remoteUsers.keySet.contains(user4.id) &&
          remoteUsers(user4.id) == (toUserInfo(user4), None)
      }

      (searchService.updateSearchResults(_: Map[UserId, (UserInfo, Option[TeamMember])]))
        .expects(new FunctionAdapter1[Map[UserId, (UserInfo, Option[TeamMember])], Boolean](checkRemoteUsers))
        .once()

      result(handler.syncSearchResults(users.keys.toArray:_*)) shouldEqual SyncResult.Success
    }

    scenario("Update search results when NOT in a team") {
      val user1 = UserData("user1").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user2 = UserData("user2").copy(teamId = Some(teamId), connection = Accepted, domain = domain)
      val user3 = UserData("user3").copy(connection = Accepted, domain = domain)
      val user4 = UserData("user4").copy(connection = Accepted, domain = domain)
      val users = Seq(user1, user2, user3, user4).toIdMap

      (usersClient.loadUsers _)
        .expects(users.keys.toSeq)
        .anyNumberOfTimes()
        .onCall { userIds: Seq[UserId] =>
          CancellableFuture.successful(
            Right(users.filterKeys(userIds.contains).values.map(toUserInfo).toSeq)
          )
        }

      (teamsSyncHandler.getMembers _).expects(*).never()

      def checkRemoteUsers(remoteUsers: Map[UserId, (UserInfo, Option[TeamMember])]): Boolean = {
        remoteUsers.size == 4 &&
          remoteUsers.keySet.contains(user1.id) &&
          remoteUsers(user1.id) == (toUserInfo(user1), None) &&
          remoteUsers.keySet.contains(user2.id) &&
          remoteUsers(user2.id) == (toUserInfo(user2), None)
        remoteUsers.keySet.contains(user3.id) &&
          remoteUsers(user3.id) == (toUserInfo(user3), None) &&
          remoteUsers.keySet.contains(user4.id) &&
          remoteUsers(user4.id) == (toUserInfo(user4), None)
      }

      (searchService.updateSearchResults(_: Map[UserId, (UserInfo, Option[TeamMember])]))
        .expects(new FunctionAdapter1[Map[UserId, (UserInfo, Option[TeamMember])], Boolean](checkRemoteUsers))
        .once()

      val handler = new UsersSyncHandlerImpl(
        FederationSupport(federationSupported), userService, usersStorage, assetService,
        searchService, usersClient, otrSync, None, teamsSyncHandler
      )
      result(handler.syncSearchResults(users.keys.toArray:_*)) shouldEqual SyncResult.Success
    }
  }
}
