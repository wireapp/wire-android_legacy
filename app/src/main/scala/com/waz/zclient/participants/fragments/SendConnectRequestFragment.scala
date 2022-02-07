package com.waz.zclient.participants.fragments

import android.os.Bundle
import com.waz.model.{QualifiedId, UserId}
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}

import scala.concurrent.Future
import com.waz.threading.Threading._

final class SendConnectRequestFragment extends UntabbedRequestFragment {
  import Threading.Implicits.Ui

  override protected val Tag: String = SendConnectRequestFragment.Tag

  override protected lazy val footerCallback = new FooterMenuCallback {
    import ConversationChangeRequester.START_CONVERSATION

    private lazy val usersCtrl      = inject[UsersController]
    private lazy val pickUserCtrl   = inject[IPickUserController]
    private lazy val convCtrl       = inject[ConversationController]
    private lazy val convScreenCtrl = inject[IConversationScreenController]

    override def onLeftActionClicked(): Unit =
      for {
        Some(user)  <- userToConnect
        conv        <- usersCtrl.connectToUser(user.id)
        _           <- conv.fold(
                         Future.successful(pickUserCtrl.hideUserProfile())
                       ) (c =>
                         convCtrl.selectConv(c.id, START_CONVERSATION)
                       )
      } yield onBackPressed()

    override def onRightActionClicked(): Unit =
      for {
        conv    <- convCtrl.currentConv.head
        remPerm <- removeMemberPermission.head
      } yield
        if (conv.isActive && remPerm)
          convScreenCtrl.showConversationMenu(false, conv.id)
  }

  override protected def initFooterMenu(): Unit = returning( view[FooterMenu](R.id.not_tabbed_footer) ) { vh =>
    vh.foreach { menu =>
      menu.setLeftActionText(getString(R.string.glyph__plus))
      menu.setLeftActionLabelText(getString(R.string.send_connect_request__connect_button__text))
      menu.setCallback(footerCallback)
    }

    if (fromParticipants) {
      subs += removeMemberPermission.map { remPerm =>
        getString(if (remPerm)  R.string.glyph__more else R.string.empty_string)
      }.onUi(text => vh.foreach(_.setRightActionText(text)))
    }
  }
}

object SendConnectRequestFragment {
  import UntabbedRequestFragment._

  val Tag: String = classOf[SendConnectRequestFragment].getName

  def newInstance(userId: UserId, userRequester: UserRequester): SendConnectRequestFragment =
    returning(new SendConnectRequestFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(ArgumentUserId, userId.str)
        args.putString(ArgumentUserRequester, userRequester.toString)
      })
    )

  def newInstance(qualifiedId: QualifiedId, userRequester: UserRequester): SendConnectRequestFragment =
    returning(new SendConnectRequestFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(ArgumentUserId, qualifiedId.id.str)
        args.putString(ArgumentUserRequester, userRequester.toString)
      })
    )
}
