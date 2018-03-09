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
  *//**
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
package com.waz.zclient.pages.main.conversation

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.{Fragment, FragmentManager}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.api._
import com.waz.model.{MessageContent => _, _}
import com.waz.service.ZMessaging
import com.waz.service.tracking.GroupConversationEvent
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.collection.fragments.CollectionFragment
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.collections.CollectionsObserver
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingDestination
import com.waz.zclient.controllers.drawing.{DrawingObserver, IDrawingController}
import com.waz.zclient.controllers.location.{ILocationController, LocationObserver}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{NewConversationController, NewConversationFragment, NewConversationPickFragment}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.UserProfileContainer
import com.waz.zclient.pages.main.conversation.ConversationManagerFragment._
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.pages.main.drawing.DrawingFragment
import com.waz.zclient.pages.main.pickuser.controller.{IPickUserController, PickUserControllerScreenObserver}
import com.waz.zclient.pages.main.profile.camera.{CameraContext, CameraFragment}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.fragments.ParticipantFragment
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.views.ConversationFragment
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}

class ConversationManagerFragment extends BaseFragment[Container] with FragmentHelper
  with LikesListFragment.Container
  with ConversationScreenControllerObserver
  with DrawingObserver
  with DrawingFragment.Container
  with CameraFragment.Container
  with PickUserControllerScreenObserver
  with LocationObserver
  with CollectionsObserver
  with UserProfileContainer {

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val convController         = inject[ConversationController]
  private lazy val collectionController   = inject[CollectionController]
  private lazy val navigationController   = inject[INavigationController]
  private lazy val cameraController       = inject[ICameraController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val drawingController      = inject[IDrawingController]
  private lazy val locationController     = inject[ILocationController]
  private lazy val pickUserController     = inject[IPickUserController]
  private lazy val newConvController      = inject[NewConversationController]
  private lazy val participantsController = inject[ParticipantsController]

  private var pickUserDestination: IPickUserController.Destination = null

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    import ConversationChangeRequester._
    convController.convChanged.onUi { change =>
      if ((change.requester == START_CONVERSATION) ||
        (change.requester == INCOMING_CALL) ||
        (change.requester == LEAVE_CONVERSATION) ||
        (change.requester == DELETE_CONVERSATION) ||
        (change.requester == BLOCK_USER)) {

        if ((navigationController.getCurrentRightPage == Page.CAMERA) && !change.noChange)
          cameraController.closeCamera(CameraContext.MESSAGE)

        closeLikesList()
      } else if (!change.noChange) {
        collectionController.closeCollection()
      }
    }

    participantsController.onShowParticipants.onUi { childTag =>
      KeyboardUtils.hideKeyboard(getActivity)
      navigationController.setRightPage(Page.PARTICIPANT, ConversationManagerFragment.Tag)
      showFragment(ParticipantFragment.newInstance(childTag), ParticipantFragment.TAG)
    }

    participantsController.onHideParticipants.onUi { _ =>
      navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
      getChildFragmentManager.popBackStack(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_conversation_manager, container, false)
    if (savedInstanceState == null) {
      import ConversationFragment._
      getChildFragmentManager
        .beginTransaction
        .add(R.id.fl__conversation_manager__message_list_container, newInstance(), TAG)
        .commit
    }
    view
  }

  override def onStart(): Unit = {
    super.onStart()
    screenController.addConversationControllerObservers(this)
    drawingController.addDrawingObserver(this)
    cameraController.addCameraActionObserver(this)
    pickUserController.addPickUserScreenControllerObserver(this)
    locationController.addObserver(this)
    collectionController.addObserver(this)
  }

  override def onStop(): Unit = {
    locationController.removeObserver(this)
    pickUserController.removePickUserScreenControllerObserver(this)
    cameraController.removeCameraActionObserver(this)
    drawingController.removeDrawingObserver(this)
    screenController.removeConversationControllerObservers(this)
    collectionController.removeObserver(this)
    super.onStop()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    val fragment = getChildFragmentManager.findFragmentByTag(CameraFragment.TAG)
    if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data)
  }

  override def onBackPressed(): Boolean = {
    val fragment = getChildFragmentManager.findFragmentById(R.id.fl__conversation_manager__message_list_container)
    fragment match {
      case f: OnBackPressedListener if f.onBackPressed => true
      case f: NewConversationPickFragment =>
        f.onBackPressed()
        pickUserController.hidePickUser(pickUserDestination)
        true
      case f: NewConversationFragment =>
        f.onBackPressed()
        pickUserController.hidePickUser(pickUserDestination)
        true
      case _: LikesListFragment =>
        getChildFragmentManager.popBackStack(LikesListFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        true
      case _ =>
        false
    }
  }

  override def onShowLikesList(message: Message): Unit = showFragment(LikesListFragment.newInstance(message), LikesListFragment.TAG)

  override def onShowDrawing(image: ImageAsset, drawingDestination: IDrawingController.DrawingDestination, method: IDrawingController.DrawingMethod): Unit = {
    navigationController.setRightPage(Page.DRAWING, ConversationManagerFragment.Tag)
    showFragment(DrawingFragment.newInstance(image, drawingDestination, method), DrawingFragment.TAG)
  }

  override def onHideDrawing(drawingDestination: IDrawingController.DrawingDestination, imageSent: Boolean): Unit = drawingDestination match {
    case DrawingDestination.CAMERA_PREVIEW_VIEW =>
      getChildFragmentManager.popBackStack(DrawingFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      cameraController.closeCamera(CameraContext.MESSAGE)
      navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
    case _ =>
      getChildFragmentManager.popBackStack(DrawingFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
  }

  override def openCollection(): Unit = {
    navigationController.setRightPage(Page.COLLECTION, ConversationManagerFragment.Tag)
    showFragment(CollectionFragment.newInstance(), CollectionFragment.TAG)
  }

  override def closeCollection(): Unit = {
    getChildFragmentManager.popBackStack(CollectionFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
  }

  override def dismissUserProfile(): Unit = dismissSingleUserProfile()

  override def dismissSingleUserProfile(): Unit = {
    getChildFragmentManager.popBackStackImmediate
    navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
  }

  override def onBitmapSelected(imageAsset: ImageAsset, imageFromCamera: Boolean, cameraContext: CameraContext): Unit = {
    if (cameraContext ne CameraContext.MESSAGE) return
    inject[ConversationController].sendMessage(imageAsset)
    getStoreFactory.networkStore.doIfHasInternetOrNotifyUser(null)
    cameraController.closeCamera(CameraContext.MESSAGE)
  }

  override def onOpenCamera(cameraContext: CameraContext): Unit = {
    if (cameraContext == CameraContext.MESSAGE) {
      navigationController.setRightPage(Page.CAMERA, ConversationManagerFragment.Tag)
      showFragment(CameraFragment.newInstance(CameraContext.MESSAGE), CameraFragment.TAG)
    }
  }

  override def onCloseCamera(cameraContext: CameraContext): Unit = {
    if (cameraContext == CameraContext.MESSAGE) {
      getChildFragmentManager.popBackStackImmediate
      navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
    }
  }

  override def onShowPickUser(destination: IPickUserController.Destination): Unit =
    if (destination == IPickUserController.Destination.CURSOR || destination == IPickUserController.Destination.PARTICIPANTS) {
      pickUserDestination = destination
      KeyboardUtils.hideKeyboard(getActivity)
      navigationController.setRightPage(Page.PICK_USER_ADD_TO_CONVERSATION, ConversationManagerFragment.Tag)

      import com.waz.threading.Threading.Implicits.Ui
      convController.currentConvIsGroup.head.flatMap {
        case true =>
          convController.currentConvId.head.map { cId =>
            newConvController.setAddToConversation(cId)
            showFragment(new NewConversationPickFragment, AddOrCreateTag)
          }
        case false =>
          convController.currentConvMembers.head.map { members =>
            newConvController.setCreateConversation(members, GroupConversationEvent.ConversationDetails)
            showFragment(new NewConversationFragment, AddOrCreateTag)
          }
      }
    }

  override def onHidePickUser(destination: IPickUserController.Destination): Unit = {
    if (destination == pickUserDestination) {
      val page = if (IPickUserController.Destination.CURSOR == pickUserDestination) Page.MESSAGE_STREAM else Page.PARTICIPANT
      navigationController.setRightPage(page, ConversationManagerFragment.Tag)
      getChildFragmentManager.popBackStack(AddOrCreateTag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }

  override def onShowShareLocation(): Unit = {
    showFragment(LocationFragment.newInstance, LocationFragment.TAG)
    navigationController.setRightPage(Page.SHARE_LOCATION, ConversationManagerFragment.Tag)
  }

  override def onHideShareLocation(location: MessageContent.Location): Unit = {
    if (location != null)
      convController.sendMessage(location)
    navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
    getChildFragmentManager.popBackStack(LocationFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  }

  override def closeLikesList(): Unit = {
    val fragment = getChildFragmentManager.findFragmentById(R.id.fl__conversation_manager__message_list_container)
    if (fragment.isInstanceOf[LikesListFragment])
      getChildFragmentManager.popBackStack(LikesListFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  }

  private def showFragment(fragment: Fragment, tag: String): Unit = {
    getChildFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
      .replace(R.id.fl__conversation_manager__message_list_container, fragment, tag)
      .addToBackStack(tag)
      .commit
  }

  override def onShowEditConversationName(show: Boolean): Unit = {}

  override def onHideUser(): Unit = {}

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit = {}

  override def onHideOtrClient(): Unit = {}

  override def showRemoveConfirmation(userId: UserId): Unit = {}

  override def onCameraNotAvailable(): Unit = {}

  override def onShowUserProfile(userId: UserId): Unit = {}

  override def onHideUserProfile(): Unit = {}
}

object ConversationManagerFragment {

  val AddOrCreateTag = "AddingToOrCreatingConversation"

  val Tag: String = classOf[ConversationManagerFragment].getName

  def newInstance = new ConversationManagerFragment

  trait Container {
    def onOpenUrl(url: String): Unit
  }

}
