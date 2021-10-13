package com.waz.zclient.participants.fragments

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.model.{ConversationRole, UserData, UserId}
import com.waz.service.ZMessaging
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.UserRequester

import scala.concurrent.duration._
import com.waz.threading.Threading._

import scala.concurrent.Future

abstract class UntabbedRequestFragment extends SingleParticipantFragment {
  import Threading.Implicits.Ui
  import UntabbedRequestFragment._

  protected val Tag: String

  override protected val layoutId: Int = R.layout.fragment_participants_not_tabbed

  private lazy val userRequester            = UserRequester.valueOf(getArguments.getString(ArgumentUserRequester))
  private lazy val fromDeepLink             = userRequester == UserRequester.DEEP_LINK
  protected lazy val fromParticipants       = userRequester == UserRequester.PARTICIPANTS
  protected lazy val userToConnectId        = UserId(getArguments.getString(ArgumentUserId))
  protected lazy val removeMemberPermission = participantsController.selfRole.map(_.canRemoveGroupMember)

  protected lazy val userToConnect = participantsController.getUser(userToConnectId)

  override protected def initViews(savedInstanceState: Bundle): Unit = {
    initDetailsView()
    initFooterMenu()
  }

  override protected def initDetailsView(): Unit = returning(view[RecyclerView](R.id.not_tabbed_recycler_view)) { vh =>
    vh.foreach(_.setLayoutManager(new LinearLayoutManager(ctx)))

    (for {
        zms           <- inject[Signal[ZMessaging]].head
        Some(user)    <- userToConnect
        isGroup       <- participantsController.isGroup.head
        isFederated   =  usersController.isFederated(user)
        handle        =  usersController.displayHandle(user)
        isGuest       =  !user.isWireBot && user.isGuest(zms.teamId)
        isExternal    =  !user.isWireBot && user.isExternal(zms.teamId)
        isDarkTheme   <- inject[ThemeController].darkThemeSet.head
        isWireless    =  user.expiresAt.isDefined
        linkedText    <- linkedText(user)
      } yield (user, handle, isGuest, isExternal, isDarkTheme, isGroup, isWireless, isFederated, linkedText)).foreach {
        case (user, handle, isGuest, isExternal, isDarkTheme, isGroup, isWireless, isFederated, linkedText) =>
          val participantRole = participantsController.participants.map(_.get(userToConnectId))
          val selfRole =
            if (fromParticipants)
              participantsController.selfRole.map(Option(_))
            else
              Signal.const(Option.empty[ConversationRole])

          val adapter = new UnconnectedParticipantAdapter(
            user.id, isGuest, isExternal, isDarkTheme, isGroup, isWireless,
            user.name, handle, isFederated, linkedText
          )
          subs += Signal.zip(timerText, participantRole, selfRole).onUi {
            case (tt, pRole, sRole) => adapter.set(tt, pRole, sRole)
          }
          subs += adapter.onParticipantRoleChange.on(Threading.Background)(participantsController.setRole(user.id, _))
          subs += adapter.onLinkedTextClicked.onUi(_ => onLinkedTextClick())
          vh.foreach(_.setAdapter(adapter))
      }
  }

  protected def linkedText(user: UserData): Future[Option[(String, Int)]] =
    Future.successful(Option.empty)

  protected def onLinkedTextClick(): Unit = { }

  override def onBackPressed(): Boolean = {
    inject[IPickUserController].hideUserProfile()
    if (fromParticipants) {
      participantsController.selectedParticipant ! None
      false
    } else if (fromDeepLink) {
      CancellableFuture.delay(750.millis).map { _ =>
        getFragmentManager.popBackStack(Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      }
      true
    } else {
      val returnPage = userRequester match {
        case UserRequester.SEARCH => Page.PICK_USER
        case _                    => Page.CONVERSATION_LIST
      }
      navigationController.setLeftPage(returnPage, Tag)
      false
    }
  }
}

object UntabbedRequestFragment {
  val ArgumentUserId = "ARGUMENT_USER_ID"
  val ArgumentUserRequester = "ARGUMENT_USER_REQUESTER"
}
