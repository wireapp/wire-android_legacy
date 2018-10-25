/**
  * Wire
  * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.notifications.controllers

import java.util.concurrent.TimeUnit

import com.waz.ZLog.verbose
import com.waz.api.IConversation
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.content._
import com.waz.model._
import com.waz.service.conversation.{ConversationsListStateService, ConversationsService, ConversationsUiService}
import com.waz.service.images.ImageLoader
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.service.push.{GlobalNotificationsService, NotificationService}
import com.waz.service.{AccountsService, UiLifeCycle, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.WireApplication.{AccountToAssetsStorage, AccountToImageLoader}
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.ResString
import com.waz.zclient.utils.ResString.{AnyRefArgs, StringArgs}
import com.waz.zclient.{Module, WireContext}
import org.junit.Test
import org.scalatest.Suite
import org.threeten.bp.Instant

import scala.collection.immutable.SortedSet
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class MessageNotificationsControllerTest extends AndroidFreeSpec { this: Suite =>
  import com.waz.ZLog.ImplicitTag.implicitLogTag

  implicit val context: WireContext = null

  implicit val timeout = Duration(5000, TimeUnit.MILLISECONDS)

  val teamId = TeamId()
  val userName = "TestUser"
  val userData = UserData(userName).copy(teamId = Some(teamId))
  val userId = userData.id
  val teamName = "Team"
  val teamData = TeamData(teamId, teamName, userId)
  val convId = ConvId(userId.str)
  val convData = ConversationData(convId, RConvId(), None, userId, IConversation.Type.ONE_TO_ONE)
  val convName = "Conversation"

  val displayedNots = new SourceSignal[Map[UserId, Seq[NotId]]]()

  val globalNotifications = new GlobalNotificationsService {
    override val groupedNotifications = Signal(Map.empty[UserId, (Boolean, Seq[NotificationService.NotificationInfo])])

    override def markAsDisplayed(userId: UserId, nots: Seq[NotId]): Future[Any] =
      Future.successful(displayedNots.mutate { cur =>
        cur + (userId -> (cur.getOrElse(userId, Seq.empty).toSet ++ nots).toSeq)
      })
  }

  private val notsInManager = Signal[Map[Int, NotificationProps]]()
  private val notsToCancel  = Signal[Set[Int]]()

  private val convsVisible = Signal[Map[UserId, Set[ConvId]]]()

  class NewModule extends Module {

    private val notificationManager = new NotificationManagerWrapper {
      override def getActiveNotificationIds: Seq[Int] = notsInManager.currentValue.getOrElse(Map.empty).keys.toSeq

    }

    private val uiLifeCycle = returning(mock[UiLifeCycle]) { uiLifeCycle =>
      (uiLifeCycle.uiActive _).expects().anyNumberOfTimes().returning(Signal.const(true))
    }

    private val usersStorage = returning(mock[UsersStorage]) { usersStorage =>
      (usersStorage.signal _).expects(userId).anyNumberOfTimes().returning(Signal.const(userData))
      (usersStorage.optSignal _).expects(userId).anyNumberOfTimes().returning(Signal.const(Some(userData)))
      (usersStorage.get _).expects(userId).anyNumberOfTimes().returning(Future.successful(Some(userData)))
    }

    private val convStorage = returning(mock[ConversationStorage]) { convStorage =>
      (convStorage.convsSignal _).expects().anyNumberOfTimes().returning(Signal(ConversationsSet(SortedSet(convData))))
      (convStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Option(convData)))
      (convStorage.conversations _).expects().anyNumberOfTimes().returning(IndexedSeq(convData))
    }

    private val convsStats = returning(mock[ConversationsListStateService]) { convsStats =>
      (convsStats.selectedConversationId _).expects().anyNumberOfTimes().returning(Signal.const(Some(convId)))
      (convsStats.selectConversation _).expects(Some(convId)).anyNumberOfTimes().returning(Future.successful(()))
    }

    private val conversations = returning(mock[ConversationsService]) { conversations =>
      (conversations.forceNameUpdate _).expects(convId).anyNumberOfTimes().returning(Future.successful(Option((convData, convData))))
      (conversations.isGroupConversation _).expects(convId).anyNumberOfTimes().returning(Future.successful(false))
      (conversations.groupConversation _).expects(convId).anyNumberOfTimes().returning(Signal.const(false))
    }

    private val imageLoader = mock[ImageLoader]

    private val assetsStorage = mock[AssetsStorage]
    (assetsStorage.get _).expects(*).anyNumberOfTimes().returning(Future.successful(Option.empty[AssetData]))

    private val iNavController = returning(mock[INavigationController]) { iNavController =>
      (iNavController.addNavigationControllerObserver _).expects(*).anyNumberOfTimes()
    }

    private val soundController = returning(mock[SoundController]) { ctrl =>
      (ctrl.isVibrationEnabled _).expects(*).anyNumberOfTimes().returning(false)
      (ctrl.soundIntensityFull _).expects().anyNumberOfTimes().returning(false)
      (ctrl.soundIntensityNone _).expects().anyNumberOfTimes().returning(true)
    }

    // `MessageNotificationController` receives `NotificationInfo`s from here
    bind[GlobalNotificationsService] to globalNotifications
    // processed notifications end up here
    bind[NotificationManagerWrapper] to notificationManager

    private val teamsStorage = mock[TeamsStorage]
    (teamsStorage.get _).expects(*).anyNumberOfTimes().returning(Future.successful(Some(teamData)))

    // mocked global entities
    bind[TeamsStorage]    to teamsStorage
    bind[AccountsService] to accounts
    bind[UiLifeCycle]     to uiLifeCycle

    // mocked services of the current ZMessaging
    bind[Signal[UserId]]                        to Signal.const(userId)
    bind[Signal[Option[UserId]]]                to Signal.const(Some(userId))
    bind[Signal[AccentColor]]                   to Signal.const(AccentColor(0, 0, 0, 0))
    bind[Signal[UsersStorage]]                  to Signal.const(usersStorage)
    bind[Signal[ConversationStorage]]           to Signal.const(convStorage)
    bind[Signal[ConversationsListStateService]] to Signal.const(convsStats)
    bind[Signal[ConversationsUiService]]        to Signal.const(mock[ConversationsUiService])
    bind[Signal[ConversationsService]]          to Signal.const(conversations)
    bind[Signal[MembersStorage]]                to Signal.const(mock[MembersStorage])
    bind[Signal[UserService]]                   to Signal.const(mock[UserService])
    bind[Signal[OtrClientsStorage]]             to Signal.const(mock[OtrClientsStorage])
    bind[Signal[AssetsStorage]]                 to Signal.const(mock[AssetsStorage])
    bind[Signal[ImageLoader]]                   to Signal.const(imageLoader)

    bind[Signal[AccountsService]] to Signal.const(accounts)
    (accounts.accountsWithManagers _).expects().anyNumberOfTimes().returning(Signal.const(Set(userId)))

    bind [AccountToImageLoader]   to (_ => Future.successful(Option(imageLoader)))
    bind [AccountToAssetsStorage] to (_ => Future.successful(Option(assetsStorage)))

    // mocked controllers
    bind[AccentColorController]  to new AccentColorController()
    bind[INavigationController]  to iNavController
    lazy val navigationController = new NavigationController()
    bind[NavigationController]   to navigationController

    private val convController = new ConversationController()
    convController.selectConv(Some(convId), ConversationChangeRequester.START_CONVERSATION)
    bind[ConversationController] to convController
    bind[SoundController]        to soundController

  }

  private def createInfo(text: String,
                        convId: ConvId = convId,
                        userName: String = userName,
                        convName: String = convName,
                        isEphemeral: Boolean = false,
                        notificationType: NotificationType = NotificationType.TEXT
                       ) =
    NotificationInfo(
      NotId(Uid().str), notificationType, RemoteInstant(Instant.now()), text, convId, Some(userName),
      Some(convName), userPicture = None, isEphemeral = isEphemeral, hasBeenDisplayed = false
    )

  private def sendInfos(ns: Seq[NotificationInfo], userId: UserId = userId): Unit =
    globalNotifications.groupedNotifications ! Map(userId -> (true, ns))

  private def sendInfo(info: NotificationInfo, userId: UserId = userId): Unit =
    globalNotifications.groupedNotifications ! Map(userId -> (true, Seq(info)))

  private def waitForProps(filter: Map[Int, NotificationProps] => Boolean) =
    Await.result(notsInManager.filter(filter).head, timeout)

  private def waitForOne(filter: NotificationProps => Boolean = _ => true) =
    waitForProps(_.exists(p => filter(p._2))).head._2

  private var controller: MessageNotificationsController = _

  // TODO: check why 'beforeAll' is not called automatically
  private def setup(bundleEnabled: Boolean = false): NewModule = {
    this.beforeAll()
    implicit val module: NewModule = new NewModule

    notsInManager ! Map.empty
    notsToCancel ! Set.empty
    waitForProps(_.isEmpty)

    controller = new MessageNotificationsController(bundleEnabled = bundleEnabled, applicationId = "")

    controller.notificationsToCancel { ids =>
      verbose(s"nots to cancel: $ids")
      notsToCancel.mutate(_ ++ ids)
    }

    controller.notificationToBuild {
      case (id, props) =>
        verbose(s"NEW PROPS: $id -> $props")
        notsInManager.mutate(_ + (id -> props))
    }

    globalNotifications.notificationsSourceVisible { sources =>
      convsVisible.mutate(_ => sources)
    }

    module.navigationController.visiblePage ! Page.NONE
    convsVisible ! Map.empty

    module
  }

  @Test
  def displayNotificationForReceivedLike(): Unit = {
    setup()

    sendInfo(NotificationInfo(NotId(Uid().str), NotificationType.LIKE, RemoteInstant(Instant.now()), "", convId) )

    waitForOne()
  }

  @Test
  def receiveConnectRequest(): Unit = {
    setup()

    sendInfo(createInfo(text = "", convId = convId, userName = userData.name, convName = userData.name, notificationType = NotificationType.CONNECT_REQUEST))

    waitForOne()
  }

  @Test
  def receiveTextNotification(): Unit = {
    setup(bundleEnabled = true)

    sendInfo(createInfo("1:1"))

    waitForOne { props =>
     props.contentTitle.exists(_.spans.exists(_.style == Span.StyleSpanBold)) && // the title should be in bold
     props.style.exists(_.summaryText.contains(teamName)) // and since the user has a team account, the summary text contains the name
    }
  }

  @Test
  def receiveTwoTextsFromOneUser(): Unit = {
    setup()

    val convId = ConvId()

    sendInfos(List(createInfo("1:1", convId = convId), createInfo("1:2", convId = convId)))

    waitForOne { props =>
      props.style.exists(_.lines.size == 2) && // we received two messages
        props.contentTitle.exists(_.header.args == StringArgs(List(userData.name))) // but the conv is the same, so its name is the title
    }
  }

  @Test
  def receiveTwoTextsFromTwoUsers(): Unit = {
    setup()

    val convId1 = ConvId()
    val convId2 = ConvId()

    sendInfos(List(createInfo("one", convId = convId1), createInfo("two", convId = convId2)))

    waitForOne { props =>
      props.contentTitle.exists(_.header.args == AnyRefArgs(List(2.asInstanceOf[AnyRef], "2"))) && // the title says there are two notifications from two conversations
        props.contentText.exists(_.body == ResString(0, 0, StringArgs(List("two")))) // the main message is the last one
    }
  }

  @Test
  def receiveNotificationsFromManyUsers(): Unit = {
    setup()

    val user1 = (UserId(), "user1")
    val user2 = (UserId(), "user2")
    val user3 = (UserId(), "user3")

    val ns = List(
      (user1, "1:1"),
      (user2, "2:2"),
      (user3, "3:3"),
      (user1, "1:4"),
      (user3, "3:5"),
      (user2, "2:6"),
      (user3, "3:7"),
      (user1, "1:8"),
      (user3, "3:9")
    )

    val infos = ns.map { n =>
      createInfo(text = n._2, convId = ConvId(n._1._1.str), userName = n._1._2, convName = n._1._2)
    }

    sendInfos(infos)

    waitForOne { props =>
      props.contentTitle.exists(_.header.args == AnyRefArgs(List(9.asInstanceOf[AnyRef], "3"))) &&  // nine notifications from three conversations
      props.contentText.exists(_.body == ResString(0, 0, StringArgs(List("3:9")))) // the main message is the last one
    }
  }

  @Test
  def receiveTwoTextsFromOneUserInABundle(): Unit = {
    setup(bundleEnabled = true)

    val convId = ConvId()

    sendInfos(List(createInfo(text = "1:1", convId = convId), createInfo(text = "1:2", convId = convId)))

    waitForOne { props => // the summary comes first
      props.groupSummary.contains(true) &&
      props.group.contains(userId)
    }

    waitForProps { nots => // the summary is followed by a notification with the same group id (= userId)
      nots.size == 2 &&
      nots.tail.head._2.group.contains(userId)
    }
  }

  @Test
  def receiveEphemeralMessageInOneConv(): Unit = {
    setup()

    val convId = ConvId()

    val info1 = createInfo(text = "111", convId = convId)
    val info2 = createInfo(text = "222", convId = convId)
    val info3 = createInfo(text = "333", convId = convId, isEphemeral = true)

    sendInfos(List(info1, info2, info3))

    waitForOne { props =>
      props.contentTitle.exists(_.header.args == StringArgs(Nil)) && // the title doesn't contain the username
      props.style.exists(_.lines.isEmpty) // the body doesn't contain the messages - this is the ephemeral one
    }

    waitForOne { props =>
      props.contentTitle.exists(_.header.args == StringArgs(List(userData.name))) // and there is another props with the other two messages
    }
  }

  @Test
  def receiveEphemeralMessageInTwoConvs(): Unit = {
    setup()

    val info1 = createInfo(text = "111", convId = ConvId(), userName = "user1 conv name", convName = "user1 user name")
    val info2 = createInfo(text = "222", convId = ConvId(), userName = "user2", convName = "user2", isEphemeral = true)

    sendInfos(List(info1, info2))

    waitForOne { props =>
      props.contentTitle.exists(_.header.args == StringArgs(Nil)) && // the title doesn't contain the username
        props.style.exists(_.lines.isEmpty) // the body doesn't contain the messages - this is the ephemeral one
    }

    waitForOne { props =>
      props.contentTitle.exists(_.header.args == StringArgs(List("user1 conv name"))) // and there is another props with the other two messages
    }
  }

  @Test
  def receiveManyEphemeralsAsABundle(): Unit = {
    setup(bundleEnabled = true)

    val convId = ConvId()
    val userName = "user1"

    val info1 = createInfo(text = "111", convId = convId, userName = userName, convName = userName, isEphemeral = true)
    val info2 = createInfo(text = "222", convId = convId, userName = userName, convName = userName, isEphemeral = true)
    val info3 = createInfo(text = "333", convId = convId, userName = userName, convName = userName, isEphemeral = true)

    sendInfos(List(info1, info2, info3))

    waitForProps(_.size == 2) // only the summary and one notification, not three
  }

  @Test
  def cancelNotificationAfterOpeningConvNoBundle(): Unit = {
    val module = setup()

    convsVisible ! Map.empty
    Await.result(convsVisible.filter(_.isEmpty).head, timeout)

    sendInfo(createInfo("1:1", convId = convId), userId = userId)

    waitForOne()
    //Await.result(convsVisible.filter(_.isEmpty).head, timeout)

    // the conversation controller is already set, so this should be enough to simulate opening a conversation
    module.navigationController.visiblePage ! Page.MESSAGE_STREAM

    Await.result(convsVisible.filter(map => map.get(userId).exists(_.contains(convId))).head, timeout)
  }

  @Test
  def cancelNotificationAfterOpeningConvWithBundle(): Unit = {
    val module = setup(bundleEnabled = true)
    sendInfo(createInfo("1:1", convId = convId), userId = userId)

    waitForOne()

    // the conversation controller is already set, so this should be enough to simulate opening a conversation
    module.navigationController.visiblePage ! Page.MESSAGE_STREAM

    Await.result(convsVisible.filter(map => map.get(userId).exists(_.contains(convId))).head, timeout)

  }

  @Test
  def receivePingAfterText(): Unit = {
    setup(bundleEnabled = true)

    val convId = ConvId()

    sendInfos(List(createInfo("1:1", convId = convId), createInfo("1:2", convId = convId, notificationType = NotificationType.KNOCK)))

    waitForProps(_.exists(_._2.lastIsPing.contains(true)))
  }
}
