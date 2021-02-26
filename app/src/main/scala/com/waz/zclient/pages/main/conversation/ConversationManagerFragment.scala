/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.fragment.app.{Fragment, FragmentManager}
import com.waz.api.MessageContent
import com.waz.model.{MessageContent => _, _}
import com.waz.service.assets.{Content, ContentForUpload}
import com.waz.service.tracking.GroupConversationEvent
import com.waz.threading.Threading
import com.waz.zclient.camera.CameraFragment
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.collection.fragments.CollectionFragment
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.controllers.camera.{CameraActionObserver, ICameraController}
import com.waz.zclient.controllers.collections.CollectionsObserver
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW
import com.waz.zclient.controllers.location.{ILocationController, LocationObserver}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.creation.{CreateConversationController, CreateConversationManagerFragment}
import com.waz.zclient.conversation.{ConversationController, LikesAndReadsFragment}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.drawing.DrawingFragment
import com.waz.zclient.giphy.GiphySharingPreviewFragment
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.ParticipantsController.ParticipantRequest
import com.waz.zclient.participants.fragments.ParticipantFragment
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.views.ConversationFragment
import com.waz.zclient.{FragmentHelper, R}
import com.waz.threading.Threading._
import com.waz.zclient.convExport.ExportController
import com.waz.zclient.convExport.fragments.{ExportConfigurationFragment, ExportFragment}

class ConversationManagerFragment extends FragmentHelper
  with ConversationScreenControllerObserver
  with LocationObserver
  with CollectionsObserver
  with CameraActionObserver {

  import Threading.Implicits.Ui

  private lazy val convController         = inject[ConversationController]
  private lazy val usersController        = inject[UsersController]
  private lazy val collectionController   = inject[CollectionController]
  private lazy val navigationController   = inject[INavigationController]
  private lazy val cameraController       = inject[ICameraController]
  private lazy val convScreenController   = inject[IConversationScreenController]
  private lazy val screenController       = inject[ScreenController]
  private lazy val locationController     = inject[ILocationController]
  private lazy val createConvController   = inject[CreateConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val keyboard               = inject[KeyboardController]
  private lazy val exportController       = inject[ExportController]

  private var subs = Set.empty[com.wire.signals.Subscription]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_conversation_manager, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    if (savedInstanceState == null) {
      import ConversationFragment._
      getChildFragmentManager
        .beginTransaction
        .add(R.id.fl__conversation_manager__message_list_container, newInstance(), TAG)
        .commit
    }

    import ConversationChangeRequester._
    subs += convController.convChanged.onUi { change =>
      if (
        change.requester == START_CONVERSATION ||
        change.requester == INCOMING_CALL ||
        change.requester == LEAVE_CONVERSATION ||
        change.requester == DELETE_CONVERSATION ||
        change.requester == BLOCK_USER ||
        change.requester == CONVERSATION_LIST
      ) {

        if ((navigationController.getCurrentRightPage == Page.CAMERA) && !change.noChange)
          cameraController.closeCamera(CameraContext.MESSAGE)

        screenController.showMessageDetails ! None

        participantsController.onLeaveParticipants ! false
      } else if (!change.noChange) {
        collectionController.closeCollection()
      }
    }

    subs += screenController.showMessageDetails.onUi {
      case Some(ScreenController.MessageDetailsParams(_, tab)) =>
        showFragment(LikesAndReadsFragment.newInstance(tab), LikesAndReadsFragment.Tag)
      case None =>
        getChildFragmentManager.popBackStack(LikesAndReadsFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    subs += participantsController.onShowParticipants.onUi { page =>
      participantsController.otherParticipantId.head.map {
        case Some(userId) =>
          openParticipantFragment(userId, Left(page))
        case None =>
          // opens a group participant fragment
          keyboard.hideKeyboardIfVisible()
          navigationController.setRightPage(Page.PARTICIPANT, ConversationManagerFragment.Tag)
          showFragment(ParticipantFragment.newInstance(page), ParticipantFragment.TAG)
      }
    }

    subs += participantsController.onShowParticipantsWithUserId.onUi { p =>
      openParticipantFragment(p.userId, Right(p))
    }

    subs += exportController.onShowExport.onUi { p =>
      showFragment(ExportFragment.newInstance(Some(ExportConfigurationFragment.Tag)), ExportFragment.TAG)
    }

    subs += participantsController.onLeaveParticipants.onUi { withAnimations =>
      navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)

      if (withAnimations)
        getChildFragmentManager.popBackStack(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      else {
        FragmentHelper.allowAnimations = false
        getChildFragmentManager.popBackStackImmediate(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        FragmentHelper.allowAnimations = true
      }
    }

    subs += createConvController.onShowCreateConversation.onUi {
      case true =>
        keyboard.hideKeyboardIfVisible()
        navigationController.setRightPage(Page.PICK_USER_ADD_TO_CONVERSATION, ConversationManagerFragment.Tag)
        convController.currentConvOtherMembers.head.map { members =>
          createConvController.setCreateConversation(members.keySet, GroupConversationEvent.ConversationDetails)
          import CreateConversationManagerFragment._
          showFragment(newInstance, Tag)
        }
      case false =>
        import CreateConversationManagerFragment._
        navigationController.setRightPage(Page.MESSAGE_STREAM, Tag)
        getChildFragmentManager.popBackStack()
    }

    subs += screenController.showGiphy.onUi { searchTerm =>
      import GiphySharingPreviewFragment._
      showFragment(newInstance(searchTerm), Tag, Page.GIPHY)
    }
    subs += screenController.hideGiphy.onUi(_ => hideFragment(GiphySharingPreviewFragment.Tag))

    subs += screenController.showSketch.onUi { sketch =>
      import DrawingFragment._
      showFragment(newInstance(sketch), Tag, Page.DRAWING)
    }
    subs += screenController.hideSketch.onUi { dest =>
      hideFragment(DrawingFragment.Tag)
      if (dest == CAMERA_PREVIEW_VIEW) cameraController.closeCamera(CameraContext.MESSAGE)
    }
  }

  override def onStart(): Unit = {
    super.onStart()
    convScreenController.addConversationControllerObservers(this)
    cameraController.addCameraActionObserver(this)
    locationController.addObserver(this)
    collectionController.addObserver(this)
  }

  override def onStop(): Unit = {
    locationController.removeObserver(this)
    cameraController.removeCameraActionObserver(this)
    convScreenController.removeConversationControllerObservers(this)
    collectionController.removeObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroyView()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    val fragment = getChildFragmentManager.findFragmentByTag(CameraFragment.Tag)
    if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data)
  }

  override def onBackPressed(): Boolean = {
    val fragment = getChildFragmentManager.findFragmentById(R.id.fl__conversation_manager__message_list_container)
    fragment match {
      case f: FragmentHelper if f.onBackPressed() => true
      case _ => false
    }
  }

  override def openCollection(): Unit =
    showFragment(CollectionFragment.newInstance(), CollectionFragment.TAG, Page.COLLECTION)

  override def closeCollection(): Unit = {
    getChildFragmentManager.popBackStack(CollectionFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
  }

  override def onBitmapSelected(content: Content, cameraContext: CameraContext): Unit =
    if (cameraContext == CameraContext.MESSAGE) {
      val convController = inject[ConversationController]
      convController.rotateImageIfNeeded(content).foreach { photo =>
        convController.sendAssetMessage(ContentForUpload(s"photo_${AESKey().str}", photo))
        cameraController.closeCamera(CameraContext.MESSAGE)
      }
  }

  override def onOpenCamera(cameraContext: CameraContext): Unit =
    if (cameraContext == CameraContext.MESSAGE)
      showFragment(CameraFragment.newInstance(CameraContext.MESSAGE), CameraFragment.Tag, Page.CAMERA)

  override def onCloseCamera(cameraContext: CameraContext): Unit =
    if (cameraContext == CameraContext.MESSAGE) hideFragment(CameraFragment.Tag)

  override def onShowShareLocation(): Unit =
    showFragment(LocationFragment.newInstance, LocationFragment.TAG, Page.SHARE_LOCATION)

  override def onHideShareLocation(location: MessageContent.Location): Unit = {
    if (location != null) convController.sendMessage(location)
    hideFragment(LocationFragment.TAG)
  }

  private def openParticipantFragment(userId: UserId, request: Either[Option[String], ParticipantRequest]): Unit =
    usersController.syncUserAndCheckIfDeleted(userId).foreach {
      case (Some(user), None) =>
        ContextUtils.showToast(getString(R.string.participant_was_removed_from_team, user.name.str))
      case (None, None) =>
        warn(l"Trying to show a non-existing user with id $userId")
      case _ =>
        keyboard.hideKeyboardIfVisible()
        navigationController.setRightPage(Page.PARTICIPANT, ConversationManagerFragment.Tag)
        request match {
          case Right(p)   =>
            participantsController.selectedParticipant ! Some(p.userId)
            showFragment(ParticipantFragment.newInstance(p.userId, p.fromDeepLink), ParticipantFragment.TAG)
          case Left(page) =>
            participantsController.selectedParticipant ! None
            showFragment(ParticipantFragment.newInstance(page), ParticipantFragment.TAG)
        }
    }

  private def showFragment(fragment: Fragment, tag: String): Unit =
    showFragment(fragment, tag, None)

  private def showFragment(fragment: Fragment, tag: String, page: Page): Unit =
    showFragment(fragment, tag, Some(page))

  private def showFragment(fragment: Fragment, tag: String, page: Option[Page]): Unit = {
    page.foreach(navigationController.setRightPage(_, ConversationManagerFragment.Tag))
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

  private def hideFragment(tag: String): Unit = {
    navigationController.setRightPage(Page.MESSAGE_STREAM, ConversationManagerFragment.Tag)
    getChildFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  }

  override def onHideUser(): Unit = {}

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit = {}

  override def onHideOtrClient(): Unit = {}

  override def onCameraNotAvailable(): Unit = {}

  override def onMoveToFolder(convId: ConvId): Unit = {
    //no-op
  }
}

object ConversationManagerFragment {

  val Tag: String = classOf[ConversationManagerFragment].getName

  def newInstance = new ConversationManagerFragment
}
