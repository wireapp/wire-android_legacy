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
package com.waz.zclient.pages.main

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.{Build, Bundle}
import android.provider.MediaStore
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.fragment.app.FragmentManager
import com.waz.content.UserPreferences.CrashesAndAnalyticsRequestShown
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.model.{ErrorData, Uid}
import com.waz.permissions.PermissionsService
import com.waz.service.tracking.GroupConversationEvent
import com.waz.service.{AccountManager, GlobalModule, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.collection.fragments.CollectionFragment
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{BrowserController, UserAccountsController}
import com.waz.zclient.controllers.collections.CollectionsObserver
import com.waz.zclient.controllers.confirmation.{ConfirmationObserver, ConfirmationRequest, IConfirmationController}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.controllers.singleimage.{ISingleImageController, SingleImageObserver}
import com.waz.zclient.conversation.creation.{CreateConversationController, CreateConversationManagerFragment}
import com.waz.zclient.conversation.{ConversationController, ImageFragment}
import com.waz.zclient.deeplinks.DeepLink.{logTag => _, _}
import com.waz.zclient.deeplinks.DeepLinkService
import com.waz.zclient.deeplinks.DeepLinkService._
import com.waz.zclient.giphy.GiphySharingPreviewFragment
import com.waz.zclient.log.LogUI
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversationlist.ConfirmationFragment
import com.waz.zclient.pages.main.conversationpager.ConversationPagerFragment
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.ParticipantsController.ParticipantRequest
import com.waz.zclient.feature.shortcuts.Shortcuts
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.service.tracking.TrackingService.analyticsPrefKey
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.menus.ConfirmationMenu

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._

class MainPhoneFragment extends FragmentHelper
  with OnBackPressedListener
  with ConversationPagerFragment.Container
  with SingleImageObserver
  with ConfirmationObserver
  with CollectionsObserver
  with ConfirmationFragment.Container {

  import MainPhoneFragment._
  import Threading.Implicits.Ui

  import scala.collection.JavaConverters._

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val am  = inject[Signal[AccountManager]]

  private lazy val usersController        = inject[UsersController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val accentColorController  = inject[AccentColorController]
  private lazy val collectionController   = inject[CollectionController]
  private lazy val browserController      = inject[BrowserController]
  private lazy val errorsController       = inject[ErrorsController]
  private lazy val navigationController   = inject[INavigationController]
  private lazy val singleImageController  = inject[ISingleImageController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val keyboardController     = inject[KeyboardController]
  private lazy val deepLinkService        = inject[DeepLinkService]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val pickUserController     = inject[IPickUserController]

  private lazy val confirmationMenu = returning(view[ConfirmationMenu](R.id.cm__confirm_action_light)) { vh =>
    accentColorController.accentColor.map(_.color).onUi(color => vh.foreach(_.setButtonColor(color)))
  }

  private lazy val consentDialog = for {
    true                     <- inject[GlobalModule].prefs(GlobalPreferences.ShowMarketingConsentDialog).apply()
    am                       <- am.head
    showAnalyticsPopup       <- am.userPrefs(CrashesAndAnalyticsRequestShown).apply().map {
                                  previouslyShown => !previouslyShown && BuildConfig.SUBMIT_CRASH_REPORTS
                                }
    color                    <- accentColorController.accentColor.head
                             // Show "Help make wire better" popup
    _                        <- if (!showAnalyticsPopup) Future.successful({}) else
                             showConfirmationDialog(
                               getString(R.string.crashes_and_analytics_request_title),
                               getString(R.string.crashes_and_analytics_request_body),
                               R.string.crashes_and_analytics_request_agree,
                               R.string.crashes_and_analytics_request_no,
                               color
                             ).flatMap { resp =>
                               zms.head.flatMap { zms =>
                                 for {
                                   _ <- zms.userPrefs(CrashesAndAnalyticsRequestShown) := true
                                   _ <- zms.prefs(analyticsPrefKey) := resp //we override whatever the global value is on asking the user again
                                   _ <- if (resp) inject[GlobalTrackingController].optIn() else Future.successful(())
                                 } yield {}
                               }
                             }
    askMarketingConsentAgain <- am.userPrefs(UserPreferences.AskMarketingConsentAgain).apply()
                             // Show marketing consent popup
    _                        <- if (!askMarketingConsentAgain) Future.successful({}) else
                             showConfirmationDialog(
                               getString(R.string.receive_news_and_offers_request_title),
                               getString(R.string.receive_news_and_offers_request_body),
                               R.string.app_entry_dialog_accept,
                               R.string.app_entry_dialog_not_now,
                               Some(R.string.app_entry_dialog_privacy_policy),
                               color
                             ).map { confirmed =>
                               am.setMarketingConsent(confirmed)
                               if (confirmed.isEmpty) inject[BrowserController].openPrivacyPolicy()
                             }
  } yield {}

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (savedInstanceState == null)
      getChildFragmentManager
        .beginTransaction
        .replace(R.id.fl_fragment_main_content, ConversationPagerFragment.newInstance, ConversationPagerFragment.TAG)
        .commit

    inflater.inflate(R.layout.fragment_main, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    confirmationMenu.foreach(_.setVisibility(View.GONE))
    zms.flatMap(_.errors.getErrors).onUi {
      _.foreach(handleSyncError)
    }

    deepLinkService.deepLink.collect { case Some(result) => result } onUi {
      case OpenDeepLink(UserToken(userId), UserTokenInfo(connected, currentTeamMember, self)) =>
        participantsController.onLeaveParticipants ! true

        if (self) {
          startActivity(Intents.OpenSettingsIntent(getContext))
        } else if (connected || currentTeamMember) {
          CancellableFuture.delay(750.millis).map { _ =>
            userAccountsController.getOrCreateAndOpenConvFor(userId)
              .foreach { _ =>
                participantsController.onShowParticipantsWithUserId ! ParticipantRequest(userId, fromDeepLink = true)
              }
          }
        } else {
          CancellableFuture.delay(getInt(R.integer.framework_animation_duration_medium).millis).map { _ =>
            navigationController.setVisiblePage(Page.CONVERSATION_LIST, MainPhoneFragment.Tag)
            pickUserController.showUserProfile(userId, true)
          }
        }
        deepLinkService.deepLink ! None

      case OpenDeepLink(ConversationToken(convId), _) =>
        pickUserController.hideUserProfile()
        participantsController.onLeaveParticipants ! true
        participantsController.selectedParticipant ! None

        CancellableFuture.delay(750.millis).map { _ =>
          conversationController.switchConversation(convId)
        }
        deepLinkService.deepLink ! None

      case DoNotOpenDeepLink(Conversation, reason) =>
        verbose(l"do not open, conversation deep link error. Reason: $reason")
        showErrorDialog(R.string.deep_link_conversation_error_title, R.string.deep_link_conversation_error_message)
        deepLinkService.deepLink ! None

      case DoNotOpenDeepLink(User, reason) =>
        verbose(l"do not open, user deep link error. Reason: $reason")
        showErrorDialog(R.string.deep_link_user_error_title, R.string.deep_link_user_error_message)
        deepLinkService.deepLink ! None

      case OpenDeepLink(CustomBackendToken(_), _) | DoNotOpenDeepLink(Access, _) =>
        verbose(l"do not open, Access, user logged in")
        showErrorDialog(
          R.string.custom_backend_dialog_logged_in_error_title,
          R.string.custom_backend_dialog_logged_in_error_message)
        deepLinkService.deepLink ! None

      case _ =>
    }
  }

  private def initShortcutDestinations(): Unit = {
    //Only for internal builds for now, will be for production when approved by QA.
    if (Build.VERSION.SDK_INT > 25 && BuildConfig.FLAVOR.equals("internal")) {
      val shortcuts = new Shortcuts()
      val shortcutManager = getActivity.getSystemService(classOf[ShortcutManager])

      val intent = MainActivity.newIntent(getActivity)
      val newMessageShortcutInfo = shortcuts.newMessageShortcut(getActivity, intent)
      val sharePhotoShortcutInfo = shortcuts.sharePhotoShortcut(getActivity, intent)
      val groupConversationShortcutInfo = shortcuts.groupConversationShortcut(getActivity, intent)

      shortcutManager.setDynamicShortcuts(
        List(newMessageShortcutInfo, sharePhotoShortcutInfo, groupConversationShortcutInfo).asJava)
    }
  }

  private def checkShortcutActions(): Unit = {
    getActivity.getIntent.getAction match {
      case Shortcuts.NEW_GROUP_CONVERSATION => newGroupConversation()
      case Shortcuts.SHARE_PHOTO            => openGalleryPicker()
      case Shortcuts.NEW_MESSAGE            => goToConversation()
      case _  =>
    }
  }

  private def newGroupConversation() = {
    inject[CreateConversationController].setCreateConversation(from = GroupConversationEvent.StartUi)
    getFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_in_from_left)
      .replace(R.id.fl_fragment_main_content, CreateConversationManagerFragment.newInstance, CreateConversationManagerFragment.Tag)
      .addToBackStack(CreateConversationManagerFragment.Tag)
      .commit()
    resetAction()
  }

  private def goToConversation(): Unit = {
    val intent = new Intent(getActivity, classOf[ShareActivity])
      .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent.setAction(Intent.ACTION_SEND)
    intent.setType(ShareActivity.MessageIntentType)
    startActivity(intent)
    resetAction()
  }

  private def openGalleryPicker() =
    inject[PermissionsService].requestAllPermissions(ListSet(android.Manifest.permission.READ_EXTERNAL_STORAGE)).map {
      case true =>
        val galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, Shortcuts.SHARE_PHOTO_REQUEST_CODE)
        resetAction()
      case _    =>
    }(Threading.Ui)

  private def goToShareImage(data: Intent) = {
    val intent = new Intent(getActivity, classOf[ShareActivity])
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent.setAction(Intent.ACTION_SEND)
    intent.putExtra(Intent.EXTRA_STREAM, data.getData)
    intent.setType("image/jpeg")
    startActivity(intent)
  }

  private def resetAction() =
    getActivity.getIntent.setAction("")

  override def onStart(): Unit = {
    super.onStart()
    singleImageController.addSingleImageObserver(this)
    confirmationController.addConfirmationObserver(this)
    collectionController.addObserver(this)
    initShortcutDestinations()
    consentDialog
  }

  override def onResume(): Unit = {
    super.onResume()
    checkShortcutActions()
  }

  override def onStop(): Unit = {
    singleImageController.removeSingleImageObserver(this)
    confirmationController.removeConfirmationObserver(this)
    collectionController.removeObserver(this)
    super.onStop()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == Shortcuts.SHARE_PHOTO_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK) {
        goToShareImage(data)
      }
    } else {
      withChildFragment(R.id.fl_fragment_main_content)(_.onActivityResult(requestCode, resultCode, data))
    }
  }

  override def onBackPressed(): Boolean = confirmationMenu flatMap { confirmationMenu =>
    if (confirmationMenu.getVisibility == View.VISIBLE) {
      confirmationMenu.animateToShow(false)
      return true
    }

    val backStackSize = getChildFragmentManager.getBackStackEntryCount
    lazy val topFragment = getChildFragmentManager.findFragmentByTag(getChildFragmentManager.getBackStackEntryAt(backStackSize - 1).getName)
    val mainContentFragment = getChildFragmentManager.findFragmentById(R.id.fl_fragment_main_content)
    val overlayContentFragment = getChildFragmentManager.findFragmentById(R.id.fl__overlay_container)

    if (backStackSize > 0) {
      Option(topFragment) collect {
        case f : GiphySharingPreviewFragment =>
          f.onBackPressed() || getChildFragmentManager.popBackStackImmediate(GiphySharingPreviewFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        case f : ImageFragment =>
          f.onBackPressed() || getChildFragmentManager.popBackStackImmediate(ImageFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        case f : CollectionFragment => f.onBackPressed()
        case f : ConfirmationFragment => f.onBackPressed()
      }
    } else {
      // Back press is first delivered to the notification fragment, and if it's not consumed there,
      // it's then delivered to the main content.
      Option(mainContentFragment) collect {
        case f : OnBackPressedListener if f.onBackPressed() => true
      } orElse (Option(overlayContentFragment) collect {
        case f : OnBackPressedListener if f.onBackPressed() => true
      })
    }

  } getOrElse getChildFragmentManager.popBackStackImmediate

  override def onOpenUrl(url: String): Unit = browserController.openUrl(url)

  override def onShowSingleImage(messageId: String): Unit = {
    keyboardController.hideKeyboardIfVisible()
    getChildFragmentManager
      .beginTransaction
      .add(R.id.fl__overlay_container, ImageFragment.newInstance(messageId), ImageFragment.Tag)
      .addToBackStack(ImageFragment.Tag)
      .commit
    navigationController.setRightPage(Page.SINGLE_MESSAGE, Tag)
  }

  override def onHideSingleImage(): Unit = ()

  override def openCollection(): Unit = ()

  override def closeCollection(): Unit = ()

  override def onRequestConfirmation(confirmationRequest: ConfirmationRequest, requester: Int): Unit = {
    confirmationMenu.foreach(_.onRequestConfirmation(confirmationRequest))
  }

  private def handleSyncError(error: ErrorData): Unit = {
    import ConfirmationFragment._
    import com.waz.api.ErrorType._

    def getGroupErrorMessage: Future[String] = {
      error.errType match {
        case CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION =>
          if (error.users.size == 1)
            usersController.user(error.users.head).head
              .map(getString(R.string.in_app_notification__sync_error__add_user__body, _))
          else
            Future.successful(getString(R.string.in_app_notification__sync_error__add_multiple_user__body))
        case CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER =>
          conversationController.conversationData(error.convId.get).head
            .map(data => getString(R.string.in_app_notification__sync_error__create_group_convo__body, data.get.displayName))
        case _ =>
          Future.successful(getString(R.string.in_app_notification__sync_error__unknown__body))
      }
    }

    error.errType match {
      case CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION |
           CANNOT_ADD_USER_TO_FULL_CONVERSATION |
           CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER =>
        getGroupErrorMessage foreach { errorMsg =>
          getChildFragmentManager
            .beginTransaction
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
            .replace(
              R.id.fl_dialog_container,
              newMessageOnlyInstance(
                getResources.getString(R.string.in_app_notification__sync_error__create_group_convo__title),
                errorMsg,
                getResources.getString(R.string.in_app_notification__sync_error__create_convo__button),
                error.id.str
              ),
              TAG
            )
            .addToBackStack(TAG)
            .commit
        }
      case CANNOT_ADD_USER_TO_FULL_CALL |
           CANNOT_CALL_CONVERSATION_WITH_TOO_MANY_MEMBERS |
           CANNOT_SEND_VIDEO |
           PLAYBACK_FAILURE =>
       LogUI.error(l"Unexpected error ${error.errType}")
      case CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION |
           RECORDING_FAILURE |
           CANNOT_SEND_ASSET_FILE_NOT_FOUND |
           CANNOT_SEND_ASSET_TOO_LARGE => // Handled in ConversationFragment
      case CANNOT_DELETE_GROUP_CONVERSATION => //Handled in ConversationListManagerFragment
      case _ =>
        LogUI.error(l"Unexpected error ${error.errType}")
    }
  }

  override def onDialogConfirm(dialogId: String): Unit = closeConfirmationDialog(dialogId)

  override def onDialogCancel(dialogId: String): Unit = closeConfirmationDialog(dialogId)

  private def closeConfirmationDialog(dialogId: String): Unit = {
    getChildFragmentManager.popBackStackImmediate(ConfirmationFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    dismissError(dialogId)
  }

  private def dismissError(errorId: String) = errorsController.dismissSyncError(Uid(errorId))
}

object MainPhoneFragment {
  val Tag: String = classOf[MainPhoneFragment].getName
}
