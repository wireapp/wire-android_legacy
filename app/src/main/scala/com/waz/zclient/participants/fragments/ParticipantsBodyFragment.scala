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

package com.waz.zclient.participants.fragments

import android.os.Bundle
import android.support.v7.widget.{GridLayoutManager, RecyclerView}
import android.view.animation.{AlphaAnimation, Animation}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.LinearLayout
import com.waz.api.{IConversation, NetworkMode, User, UsersList}
import com.waz.model.ConvId
import com.waz.model.ConversationData.ConversationType
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{SoundController, ThemeController, UserAccountsController}
import com.waz.zclient.controllers.accentcolor.AccentColorObserver
import com.waz.zclient.controllers.confirmation.{ConfirmationRequest, IConfirmationController, TwoButtonConfirmationCallback}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.connect.{ConnectStoreObserver, IConnectStore}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.core.stores.network.NetworkAction
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver
import com.waz.zclient.integrations.IntegrationDetailsController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.ParticipantsChatheadAdapter
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.{LayoutSpec, ViewUtils}
import com.waz.zclient.views.images.ImageAssetImageView
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future


class ParticipantBodyFragment extends BaseFragment[ParticipantBodyFragment.Container] with FragmentHelper
  with ParticipantsStoreObserver with AccentColorObserver with ConnectStoreObserver {
  private var participantsView: RecyclerView = _
  private var participantsAdapter: ParticipantsChatheadAdapter = _
  private var footerMenu: FooterMenu = _
  private var topBorder: View = _
  private var footerWrapper: LinearLayout = _
  private var unblockButton: ZetaButton = _
  private var userRequester: IConnectStore.UserRequester = _
  private var imageAssetImageView: ImageAssetImageView = _

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val convScreenController = inject[IConversationScreenController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val pickUserController = inject[IPickUserController]
  private lazy val themeController = inject[ThemeController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val integrationDetailsController = inject[IntegrationDetailsController]

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    val parent = getParentFragment
    // Apply the workaround only if this is a child fragment, and the parent is being removed.
    if (!enter && parent != null && parent.isRemoving) {
      // This is a workaround for the bug where child fragments disappear when
      // the parent is removed (as all children are first removed from the parent)
      // See https://code.google.com/p/android/issues/detail?id=55228
      returning( new AlphaAnimation(1, 1) ) {
        _.setDuration(ViewUtils.getNextAnimationDuration(parent))
      }
    } else super.onCreateAnimation(transit, enter, nextAnim)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    userRequester = getArguments.getSerializable(ParticipantBodyFragment.ARG_USER_REQUESTER).asInstanceOf[IConnectStore.UserRequester]
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_group_participant, viewGroup, false)
    footerMenu = ViewUtils.getView(view, R.id.fm__participants__footer)
    topBorder = ViewUtils.getView(view, R.id.v_participants__footer__top_border)
    footerWrapper = ViewUtils.getView(view, R.id.ll__participants__footer_wrapper)
    unblockButton = ViewUtils.getView(view, R.id.zb__single_user_participants__unblock_button)
    imageAssetImageView = ViewUtils.getView(view, R.id.iaiv__participant_body)
    imageAssetImageView.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE)

    implicit val ctx = getContext

    val numberOfColumns = getResources.getInteger(R.integer.participant_column__count)
    participantsAdapter = new ParticipantsChatheadAdapter(numberOfColumns)
    participantsView = ViewUtils.getView(view, R.id.pgv__participants)

    participantsView.setAdapter(participantsAdapter)

    import Threading.Implicits.Ui
    participantsAdapter.onClick.onUi { userId =>
      zms.map(_.usersStorage).head.flatMap(_.get(userId)).flatMap {
        case Some(userData) =>
          (userData.providerId, userData.integrationId) match {
            case (Some(pId), Some(iId)) =>
              convController.currentConv.head.map { conv =>
                integrationDetailsController.setRemoving(conv.id, userId)
                getControllerFactory.getConversationScreenController.showIntegrationDetails(pId, iId)
              }
            case _ =>
              val user = getStoreFactory.pickUserStore.getUser(userId.str)
              getControllerFactory.getConversationScreenController.showUser(user)
              Future.successful(())
          }
        case _ =>
          Future.successful(())
      }
    }

    val layoutManager = new GridLayoutManager(ctx, numberOfColumns)

    layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      override def getSpanSize(position: Int): Int = participantsAdapter.getSpanSize(position)
    })

    participantsView.setLayoutManager(layoutManager)

    // Hide footer until conversation is loaded
    footerMenu.setVisibility(View.GONE)
    unblockButton.setVisibility(View.GONE)
    // Toggle color background
    view.setOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        getContainer.onClickedEmptyBackground()
      }
    })

    view
  }

  override def onStart(): Unit = {
    super.onStart()
    if (userRequester == IConnectStore.UserRequester.POPOVER) {
      getStoreFactory.connectStore.addConnectRequestObserver(this)
      getStoreFactory.connectStore.loadUser(
        getStoreFactory.singleParticipantStore.getUser.getId,
        userRequester
      )
    } else getStoreFactory.participantsStore.addParticipantsStoreObserver(this)

    getControllerFactory.getAccentColorController.addAccentColorObserver(this)
  }

  override def onStop(): Unit = {
    getStoreFactory.connectStore.removeConnectRequestObserver(this)
    getStoreFactory.participantsStore.removeParticipantsStoreObserver(this)
    getControllerFactory.getAccentColorController.removeAccentColorObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    imageAssetImageView = null
    participantsView = null
    participantsAdapter = null
    footerMenu = null
    topBorder = null
    footerWrapper = null
    super.onDestroyView()
  }

  override def conversationUpdated(conv: IConversation): Unit =
      convController.isGroup(ConvId(conv.getId)).zip(convController.isWithBot(ConvId(conv.getId))).foreach { case (isGroup, isWithBot) =>
      footerMenu.setVisibility(View.VISIBLE)
      if (!isGroup && !isWithBot) {
        footerMenu.setLeftActionText(getString(R.string.glyph__plus))
        if (conv.getType == ConversationType.OneToOne)
          getStoreFactory.singleParticipantStore.setUser(conv.getOtherParticipant)
        else {
          import Threading.Implicits.Ui
          convController.loadMembers(ConvId(conv.getId)).map(_.filterNot(_.isSelf).head).foreach { userData =>
            val user = getStoreFactory.zMessagingApiStore.getApi.getUser(userData.id.str)
            getStoreFactory.singleParticipantStore.setUser(user)
          }
        }
      } else {
        imageAssetImageView.setVisibility(View.GONE)

        // Check if self user is member for group conversation and has permission to add
        if (conv.isMemberOfConversation &&
            userAccountsController.hasAddConversationMemberPermission(new ConvId(conv.getId))
        ) {
          footerMenu.setLeftActionText(getString(R.string.glyph__add_people))
          footerMenu.setLeftActionLabelText(getString(R.string.conversation__action__add_people))
        } else {
          footerMenu.setLeftActionText("")
          footerMenu.setLeftActionLabelText("")
        }
      }

      footerMenu.setRightActionText(getString(R.string.glyph__more))

      footerMenu.setCallback(new FooterMenuCallback() {
        override def onLeftActionClicked(): Unit = {
          if (userRequester == IConnectStore.UserRequester.POPOVER) {
            val user = getStoreFactory.singleParticipantStore.getUser
            if (user.isMe) {
              convScreenController.hideParticipants(true, false)
              // Go to conversation with this user
              pickUserController.hidePickUserWithoutAnimations(getContainer.getCurrentPickerDestination)
              convController.selectConv(new ConvId(user.getConversation.getId), ConversationChangeRequester.CONVERSATION_LIST)
              return
            }
          }

          if (conv.isMemberOfConversation && userAccountsController.hasAddConversationMemberPermission(new ConvId(conv.getId)))
            convScreenController.addPeopleToConversation()
        }

        override def onRightActionClicked(): Unit = getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new NetworkAction() {
          override def execute(networkMode: NetworkMode): Unit = if (conv.isMemberOfConversation) {
            if (userRequester == IConnectStore.UserRequester.POPOVER && conv.getType == ConversationType.OneToOne) {
              val otherUser = conv.getOtherParticipant
              getContainer.toggleBlockUser(otherUser, otherUser.getConnectionStatus != User.ConnectionStatus.BLOCKED)
            } else convScreenController.showConversationMenu(IConversationScreenController.CONVERSATION_DETAILS, new ConvId(conv.getId))
          }

          override def onNoNetwork(): Unit = ViewUtils.showAlertDialog(getActivity,
            R.string.alert_dialog__no_network__header, R.string.leave_conversation_failed__message,
            R.string.alert_dialog__confirmation, null, true
          )
        })

      })
    }(Threading.Ui)

  override def participantsUpdated(participants: UsersList): Unit =
    participantsAdapter.notifyDataSetChanged()

  override def otherUserUpdated(otherUser: User): Unit = if (Option(otherUser).isDefined && Option(getView).isDefined) {
    participantsAdapter.notifyDataSetChanged()
    imageAssetImageView.setVisibility(View.VISIBLE)
    imageAssetImageView.connectImageAsset(otherUser.getPicture)
    otherUser.getConnectionStatus match {
      case User.ConnectionStatus.BLOCKED =>
        footerMenu.setVisibility(View.GONE)
        unblockButton.setVisibility(View.VISIBLE)
        unblockButton.setOnClickListener(new View.OnClickListener() {
          override def onClick(v: View): Unit = otherUser.unblock
        })
      case _ =>
        footerMenu.setVisibility(View.VISIBLE)
        unblockButton.setVisibility(View.GONE)
        unblockButton.setOnClickListener(null)
    }
  }

  override def onAccentColorHasChanged(sender: Any, color: Int): Unit = unblockButton.setAccentColor(color)

  override def onConnectUserUpdated(user: User, userType: IConnectStore.UserRequester): Unit = if (userType == userRequester && Option(user).isDefined) {
    imageAssetImageView.setVisibility(View.VISIBLE)
    imageAssetImageView.connectImageAsset(user.getPicture)
    footerMenu.setVisibility(View.VISIBLE)

    convController.currentConv.head.foreach { conv =>
      convController.isGroup(conv).foreach {
        case false if user.isMe =>
          footerMenu.setLeftActionText(getString(R.string.glyph__people))
          footerMenu.setLeftActionLabelText(getString(R.string.popover__action__profile))
          footerMenu.setRightActionText("")
          footerMenu.setRightActionLabelText("")
        case false =>
          footerMenu.setLeftActionText(getString(R.string.glyph__add_people))
          footerMenu.setLeftActionLabelText(getString(R.string.conversation__action__create_group))
          footerMenu.setRightActionText(getString(R.string.glyph__block))
          footerMenu.setRightActionLabelText(getString(R.string.popover__action__block))
        case true if user.isMe =>
          footerMenu.setLeftActionText(getString(R.string.glyph__people))
          footerMenu.setLeftActionLabelText(getString(R.string.popover__action__profile))
          footerMenu.setRightActionText(getString(R.string.glyph__minus))
          footerMenu.setRightActionLabelText("")
        case true =>
          footerMenu.setLeftActionText(getString(R.string.glyph__conversation))
          footerMenu.setLeftActionLabelText(getString(R.string.popover__action__open))
          footerMenu.setRightActionText(getString(R.string.glyph__minus))
          footerMenu.setRightActionLabelText(getString(R.string.popover__action__remove))
      }(Threading.Ui)

      footerMenu.setCallback(new FooterMenuCallback() {
        override def onLeftActionClicked(): Unit =
          (if (user.isMe) Future.successful(true) else convController.isGroup(conv)).foreach {
            case true =>
              convScreenController.hideParticipants(true, false)
              pickUserController.hidePickUserWithoutAnimations(getContainer.getCurrentPickerDestination)
              convController.selectConv(new ConvId(user.getConversation.getId), ConversationChangeRequester.CONVERSATION_LIST)
            case false =>
              convScreenController.addPeopleToConversation()
          }(Threading.Ui)


        override def onRightActionClicked(): Unit = convController.isGroup(conv).foreach {
          case true if !user.isMe => getContainer.toggleBlockUser(user, user.getConnectionStatus != User.ConnectionStatus.BLOCKED)
          case true => getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(new NetworkAction() {
            override def execute(networkMode: NetworkMode): Unit =
              if (user.isMe) showLeaveConfirmation(conv.id)
              else getContainer.showRemoveConfirmation(user)

            override def onNoNetwork(): Unit = ViewUtils.showAlertDialog(
              getActivity,
              R.string.alert_dialog__no_network__header,
              if (user.isMe) R.string.leave_conversation_failed__message
              else R.string.remove_from_conversation__no_network__message,
              R.string.alert_dialog__confirmation, null, true)
          })
          case false =>
        }(Threading.Ui)
      })
    }(Threading.Ui)
  }

  override def onInviteRequestSent(conversation: IConversation): Unit = {}

  private def showLeaveConfirmation(convId: ConvId) = {
    val callback = new TwoButtonConfirmationCallback() {
      override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit =
        if (
          Option(getStoreFactory).isDefined && Option(getControllerFactory).isDefined &&
          !getStoreFactory.isTornDown && !getControllerFactory.isTornDown
        ) {
          convController.leave(convId)
          if (LayoutSpec.isTablet(getActivity)) convScreenController.hideParticipants(false, true)
        }

      override def negativeButtonClicked(): Unit = {}

      override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {}
    }

    val header = getString(R.string.confirmation_menu__meta_remove)
    val text = getString(R.string.confirmation_menu__meta_remove_text)
    val confirm = getString(R.string.confirmation_menu__confirm_leave)
    val cancel = getString(R.string.confirmation_menu__cancel)
    val checkboxLabel = getString(R.string.confirmation_menu__delete_conversation__checkbox__label)
    val request = new ConfirmationRequest.Builder().withHeader(header)
      .withMessage(text)
      .withPositiveButton(confirm)
      .withNegativeButton(cancel)
      .withConfirmationCallback(callback)
      .withCheckboxLabel(checkboxLabel)
      .withWireTheme(themeController.getThemeDependentOptionsTheme)
      .withCheckboxSelectedByDefault
      .build
    confirmationController.requestConfirmation(request, IConfirmationController.PARTICIPANTS)
    val ctrl = inject[SoundController]
    if (Option(ctrl).isDefined) ctrl.playAlert()
  }

}

object ParticipantBodyFragment {
  val TAG: String = classOf[ParticipantBodyFragment].getName
  private val ARG_USER_REQUESTER = "ARG_USER_REQUESTER"

  def newInstance(userRequester: IConnectStore.UserRequester): ParticipantBodyFragment =
    returning(new ParticipantBodyFragment) {
      _.setArguments(returning(new Bundle){
        _.putSerializable(ARG_USER_REQUESTER, userRequester)
      })
    }

  trait Container {
    def onClickedEmptyBackground(): Unit

    def toggleBlockUser(otherUser: User, block: Boolean): Unit

    def showRemoveConfirmation(user: User): Unit

    def getCurrentPickerDestination: IPickUserController.Destination
  }

}
