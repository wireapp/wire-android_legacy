package com.waz.service

import com.waz.api.NotificationsHandler.NotificationType
import com.waz.content.{ConversationStorage, MessageAndLikesStorage, UsersStorage}
import com.waz.model._
import com.waz.service.call.CallingService
import com.waz.service.otr.NotificationParser
import com.waz.specs.AndroidFreeSpec
import com.waz.zms.BuildConfig
import org.threeten.bp.Instant

import scala.concurrent.Future

class NotificationParserSpec extends AndroidFreeSpec {

  private val selfId = UserId("selfUser")
  private val now = RemoteInstant(Instant.now())
  private val before = RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli - 1000L*60L*60L)

  private val convId = ConvId("conv_id1")
  private val rConvId = RConvId("r_conv_id1")
  private val domain = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty

  private val self = UserData.withName(selfId, "self").copy(availability = Availability.Available, domain = domain)
  private val conv = ConversationData(convId, rConvId, Some("conv"), selfId, lastRead = before, domain = domain, muted = MuteSet.AllAllowed)

  private val convStorage = mock[ConversationStorage]
  private val usersStorage = mock[UsersStorage]
  private val mlStorage = mock[MessageAndLikesStorage]
  private val calling = mock[CallingService]

  private def parser = NotificationParser(selfId, convStorage, usersStorage, () => mlStorage, () => calling)

  scenario("show a notification on the event of deleting a conversation") {
    val senderId = UserId("sender")
    val event = DeleteConversationEvent(rConvId, domain, now, senderId, domain)

    (usersStorage.get _).expects(selfId).anyNumberOfTimes().returning(
      Future.successful(Some(self))
    )
    (convStorage.getByRemoteId _).expects(rConvId).anyNumberOfTimes().returning(
      Future.successful(Some(conv))
    )

    val nots = result(parser.parse(Seq(event)))
    nots.size shouldEqual 1
    nots.head.msgType shouldEqual NotificationType.CONVERSATION_DELETED
  }

  // TODO: Write more unit tests
}
