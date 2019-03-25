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
package com.waz.zclient.pages.main

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.content.UserPreferences.CrashesAndAnalyticsRequestShown
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.model.{ErrorData, Uid}
import com.waz.service.{AccountManager, GlobalModule, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
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
import com.waz.zclient.conversation.{ConversationController, ImageFragment}
import com.waz.zclient.deeplinks.{DeepLink, DeepLinkService}
import com.waz.zclient.giphy.GiphySharingPreviewFragment
import com.waz.zclient.log.LogUI
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversationlist.ConfirmationFragment
import com.waz.zclient.pages.main.conversationpager.ConversationPagerFragment
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.tracking.GlobalTrackingController.analyticsPrefKey
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.menus.ConfirmationMenu
import DeepLink.{logTag => _, _}
import DeepLinkService._

import scala.collection.mutable
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

  lazy val consentDialogFuture = for {
    true <- inject[GlobalModule].prefs(GlobalPreferences.ShowMarketingConsentDialog).apply()
    am   <- am.head
    showAnalyticsPopup <- am.userPrefs(CrashesAndAnalyticsRequestShown).apply().map {
      previouslyShown =>
        !previouslyShown && BuildConfig.SUBMIT_CRASH_REPORTS
    }
    // Show "Help make wire better" popup
    _ <- if (!showAnalyticsPopup) Future.successful({}) else
      showConfirmationDialog(
        getString(R.string.crashes_and_analytics_request_title),
        getString(R.string.crashes_and_analytics_request_body),
        R.string.crashes_and_analytics_request_agree,
        R.string.crashes_and_analytics_request_no
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
    _ <- if (!askMarketingConsentAgain) Future.successful({}) else
      showConfirmationDialogWithNeutralButton(
        R.string.receive_news_and_offers_request_title,
        R.string.receive_news_and_offers_request_body,
        R.string.app_entry_dialog_privacy_policy,
        R.string.app_entry_dialog_accept,
        R.string.app_entry_dialog_not_now
      ).map { confirmed =>
        am.setMarketingConsent(confirmed)
        if (confirmed.isEmpty) inject[BrowserController].openUrl(getString(R.string.url_privacy_policy))
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
    zms.flatMap(_.errors.getErrors).onUi { _.foreach(handleSyncError) }
  }

  private val subs = mutable.HashSet.empty[Subscription]

  override def onStart(): Unit = {
    super.onStart()
    singleImageController.addSingleImageObserver(this)
    confirmationController.addConfirmationObserver(this)
    collectionController.addObserver(this)

    consentDialogFuture

    subs += deepLinkService.deepLink.onUi {
      case Some(OpenDeepLink(UserToken(userId), UserTokenInfo(connected, currentTeamMember))) =>
        pickUserController.hideUserProfile()
        if (connected || currentTeamMember) {
          CancellableFuture.delay(750.millis).map { _ =>
            userAccountsController.getOrCreateAndOpenConvFor(userId)
              .foreach { _ =>
                participantsController.onShowParticipantsWithUserId ! userId
              }
          }
        } else {
          CancellableFuture.delay(getInt(R.integer.framework_animation_duration_medium).millis).map { _ =>
            navigationController.setVisiblePage(Page.CONVERSATION_LIST, MainPhoneFragment.Tag)
            participantsController.onShowAnimations ! true
            pickUserController.showUserProfile(userId)
          }
        }

      case Some(OpenDeepLink(ConversationToken(convId), _)) =>
        conversationController.switchConversation(convId)

      case _ =>
    }
  }

  override def onStop(): Unit = {
    subs.foreach(_.unsubscribe())
    subs.clear()
    singleImageController.removeSingleImageObserver(this)
    confirmationController.removeConfirmationObserver(this)
    collectionController.removeObserver(this)
    super.onStop()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    withChildFragment(R.id.fl_fragment_main_content)(_.onActivityResult(requestCode, resultCode, data))
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

