package com.waz.zclient.participants.fragments

import android.os.Bundle
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{UserData, UserId}
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}

import scala.concurrent.Future

final class BlockedUserFragment extends UntabbedRequestFragment {
  import com.waz.threading.Threading.Implicits.Ui

  override protected val Tag: String = BlockedUserFragment.Tag

  override protected lazy val footerCallback: FooterMenuCallback = new FooterMenuCallback {
    override def onLeftActionClicked(): Unit =
      for {
        Some(userId) <- userToConnectId.head
        _            <- inject[UsersController].unblockUser(userId)
      } yield getActivity.onBackPressed()

    override def onRightActionClicked(): Unit =
      for {
        conv    <- inject[ConversationController].currentConv.head
        remPerm <- removeMemberPermission.head
      } yield
        if (conv.isActive && remPerm)
          inject[IConversationScreenController].showConversationMenu(false, conv.id)
  }

  override protected def initFooterMenu(): Unit = returning( view[FooterMenu](R.id.not_tabbed_footer) ) { vh =>
    for {
      Some(userId) <- userToConnectId.head
      Some(user)   <- participantsController.getUser(userId)
      false        =  user.connection == ConnectionStatus.BlockedDueToMissingLegalHoldConsent
    } yield {
      vh.foreach { menu =>
        menu.setLeftActionText(getString(R.string.glyph__block))
        menu.setLeftActionLabelText(getString(R.string.connect_request__unblock__button__text))
        menu.setCallback(footerCallback)
      }

      if (fromParticipants) {
        subs += removeMemberPermission.map { remPerm =>
          getString(if (remPerm)  R.string.glyph__more else R.string.empty_string)
        }.onUi(text => vh.foreach(_.setRightActionText(text)))
      }
    }
  }

  override protected def linkedText(user: UserData): Future[Option[(String, Int)]] = user.connection match {
    case ConnectionStatus.BlockedDueToMissingLegalHoldConsent =>
      val accentColor = inject[AccentColorController].accentColor
      accentColor.map(_.color).head.map { color =>
        Some((getString(R.string.legal_hold_user_blocked_message), color))
      }
    case _ =>
      Future.successful(None)
  }

  override protected def onLinkedTextClick(): Unit = {
    inject[BrowserController].openAboutLegalHold()
  }

}

object BlockedUserFragment {
  val Tag: String = classOf[BlockedUserFragment].getName

  def newInstance(userId: UserId, userRequester: UserRequester): BlockedUserFragment =
    returning(new BlockedUserFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(UntabbedRequestFragment.ArgumentUserId, userId.str)
        args.putString(UntabbedRequestFragment.ArgumentUserRequester, userRequester.toString)
      })
    )
}
