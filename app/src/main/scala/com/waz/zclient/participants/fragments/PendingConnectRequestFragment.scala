package com.waz.zclient.participants.fragments

import android.content.DialogInterface
import android.os.Bundle
import com.waz.api.{ConnectionStatus, ErrorType}
import com.waz.model.{ErrorData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.{ErrorsController, R}
import com.waz.zclient.common.controllers.{BrowserController, UserAccountsController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.threading.Threading._
import com.waz.zclient.utils.ViewUtils
import com.wire.signals.Signal

final class PendingConnectRequestFragment extends UntabbedRequestFragment {
  import Threading.Implicits.Ui

  private lazy val zms                  = inject[Signal[ZMessaging]]
  private lazy val convController       = inject[ConversationController]
  private lazy val accountsController   = inject[UserAccountsController]
  private lazy val convScreenController = inject[IConversationScreenController]
  private lazy val errorsController     = inject[ErrorsController]

  private lazy val isIgnoredConnection = usersController.user(userToConnectId).map(_.connection == ConnectionStatus.IGNORED)

  override protected val Tag: String = PendingConnectRequestFragment.Tag

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    zms.flatMap(_.errors.getErrors).onUi { _.foreach(handleSyncError) }
  }

  private def handleSyncError(err: ErrorData): Unit = err.errType match {
    case ErrorType.CANNOT_CONNECT_USER_WITH_MISSING_LEGAL_HOLD_CONSENT =>
      ViewUtils.showAlertDialog(
        getContext,
        R.string.legal_hold_cannot_connect_alert_title,
        R.string.legal_hold_cannot_connect_alert_message,
        android.R.string.ok,
        R.string.legal_hold_cannot_connect_alert_negative_button,
        new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit = {
            errorsController.dismissSyncError(err.id).map(_ => getActivity.onBackPressed())
          }
        },
        new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit =
            inject[BrowserController].openAboutLegalHold()
        },
        false
      )
    case _ =>
      ()
  }

  override protected lazy val footerCallback = new FooterMenuCallback {
    override def onLeftActionClicked(): Unit =
        isIgnoredConnection.head.map {
            case true  =>
              for {
                _      <- usersController.connectToUser(userToConnectId)
                convId <- accountsController.getConversationId(userToConnectId)
                _      <- convController.selectConv(convId, ConversationChangeRequester.START_CONVERSATION)
              } yield ()
            case false =>
              usersController.cancelConnectionRequest(userToConnectId)
          }.foreach { _ => getActivity.onBackPressed() }

      override def onRightActionClicked(): Unit =
        if (fromParticipants)
        for {
            conv    <- convController.currentConv.head
            remPerm <- removeMemberPermission.head
          } yield
          if (conv.isActive && remPerm)
            convScreenController.showConversationMenu(false, conv.id)
  }

  override protected def initFooterMenu(): Unit = returning(view[FooterMenu](R.id.not_tabbed_footer)) { vh =>
    if (fromParticipants) {
      subs += removeMemberPermission.map { remPerm =>
          getString(if (remPerm)  R.string.glyph__more else R.string.empty_string)
        }.onUi(text => vh.foreach(_.setRightActionText(text)))
    }

      subs += isIgnoredConnection
        .map(ignored => getString(if (ignored) R.string.glyph__plus else R.string.glyph__undo))
        .onUi(text => vh.foreach(_.setLeftActionText(text)))

      subs += isIgnoredConnection
        .map(ignored => getString(if (ignored) R.string.send_connect_request__connect_button__text else R.string.connect_request__cancel_request__label))
        .onUi(text => vh.foreach(_.setLeftActionLabelText(text)))

      vh.foreach(_.setCallback(footerCallback))
  }
}

object PendingConnectRequestFragment {
  val Tag: String = classOf[PendingConnectRequestFragment].getName

  def newInstance(userId: UserId, userRequester: UserRequester): PendingConnectRequestFragment =
    returning(new PendingConnectRequestFragment)(
      _.setArguments(returning(new Bundle) { args =>
        args.putString(UntabbedRequestFragment.ArgumentUserId, userId.str)
        args.putString(UntabbedRequestFragment.ArgumentUserRequester, userRequester.toString)
      })
    )
}
