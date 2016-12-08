/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.conversationlist;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.waz.api.ConversationsList;
import com.waz.api.ErrorType;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.api.Verification;
import com.waz.zclient.LaunchActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.events.connect.BlockingEvent;
import com.waz.zclient.controllers.tracking.events.conversation.ArchivedConversationEvent;
import com.waz.zclient.controllers.tracking.events.conversation.DeleteConversationEvent;
import com.waz.zclient.controllers.tracking.events.conversation.UnarchivedConversationEvent;
import com.waz.zclient.controllers.tracking.events.group.CreatedGroupConversationEvent;
import com.waz.zclient.controllers.tracking.events.group.LeaveGroupConversationEvent;
import com.waz.zclient.controllers.tracking.events.peoplepicker.PeoplePickerResultsUsed;
import com.waz.zclient.core.controllers.tracking.attributes.ConversationType;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.InboxLoadRequester;
import com.waz.zclient.core.stores.conversation.OnInboxLoadedListener;
import com.waz.zclient.core.stores.inappnotification.InAppNotificationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.KnockingEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment;
import com.waz.zclient.pages.main.connect.ConnectRequestLoadMode;
import com.waz.zclient.pages.main.connect.PendingConnectRequestManagerFragment;
import com.waz.zclient.pages.main.connect.SendConnectRequestFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.participants.OptionsMenuControl;
import com.waz.zclient.pages.main.participants.OptionsMenuFragment;
import com.waz.zclient.pages.main.participants.dialog.ParticipantsDialogFragment;
import com.waz.zclient.pages.main.pickuser.PickUserFragment;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.camera.CameraFragment;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.optionsmenu.OptionsMenu;
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import com.waz.zclient.views.menus.ConfirmationMenu;

import java.util.List;

public class ConversationListManagerFragment extends BaseFragment<ConversationListManagerFragment.Container> implements
                                                                                                   PickUserControllerScreenObserver,
                                                                                                   ConversationStoreObserver,
                                                                                                   OnInboxLoadedListener,
                                                                                                   OnBackPressedListener,
                                                                                                   ConversationListFragment.Container,
                                                                                                   OptionsMenuFragment.Container,
                                                                                                   PickUserFragment.Container,
                                                                                                   CameraFragment.Container,
                                                                                                   SendConnectRequestFragment.Container,
                                                                                                   BlockedUserProfileFragment.Container,
                                                                                                   ParticipantsDialogFragment.Container,
                                                                                                   PendingConnectRequestManagerFragment.Container,
                                                                                                   ConversationScreenControllerObserver,
                                                                                                   NavigationControllerObserver,
                                                                                                   ConfirmationObserver,
                                                                                                   AccentColorObserver,
                                                                                                   InAppNotificationStoreObserver {
    public static final String TAG = ConversationListManagerFragment.class.getName();

    private LoadingIndicatorView startuiLoadingIndicatorView;
    private LoadingIndicatorView listLoadingIndicatorView;
    private FrameLayout mainContainer;
    private OptionsMenuControl optionsMenuControl;
    private ConfirmationMenu confirmationMenu;

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    public static Fragment newInstance() {
        Fragment fragment = new ConversationListManagerFragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation_list_manager, container, false);

        mainContainer = ViewUtils.getView(view, R.id.fl__conversation_list_main);
        startuiLoadingIndicatorView = ViewUtils.getView(view, R.id.liv__conversations__loading_indicator);
        listLoadingIndicatorView = ViewUtils.getView(view, R.id.lbv__conversation_list__loading_indicator);

        startuiLoadingIndicatorView.setColor(getResources().getColor(R.color.people_picker__loading__color));
        listLoadingIndicatorView.setColor(getControllerFactory().getAccentColorController().getColor());

        confirmationMenu = ViewUtils.getView(view, R.id.cm__confirm_action_light);
        confirmationMenu.setVisibility(View.GONE);
        confirmationMenu.resetFullScreenPadding();
        optionsMenuControl = new OptionsMenuControl();

        if (savedInstanceState == null) {
            // When re-starting app to open into specific page, child fragments may exist despite savedInstanceState == null
            if (getControllerFactory().getPickUserController().isShowingUserProfile()) {
                getControllerFactory().getPickUserController().hideUserProfile();
            }

            if (getControllerFactory().getPickUserController().isShowingPickUser(
                IPickUserController.Destination.CONVERSATION_LIST)) {
                getControllerFactory().getPickUserController().hidePickUser(IPickUserController.Destination.CONVERSATION_LIST, false);

                Fragment pickUserFragment = getChildFragmentManager().findFragmentByTag(PickUserFragment.TAG);
                if (pickUserFragment != null) {
                    getChildFragmentManager().popBackStack(PickUserFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
            }

            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__conversation_list_main,
                                          ConversationListFragment.newInstance(
                                              ConversationListFragment.Mode.NORMAL),
                                          ConversationListFragment.TAG)
                                     .add(R.id.fl__conversation_list__settings_box,
                                          OptionsMenuFragment.newInstance(true),
                                          OptionsMenuFragment.TAG)
                                     .commit();
        }
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().getInAppNotificationStore().addInAppNotificationObserver(this);
        getControllerFactory().getCameraController().addCameraActionObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getPickUserController().addPickUserScreenControllerObserver(this);
        getStoreFactory().getConversationStore().addConversationStoreObserverAndUpdate(this);
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);
        getControllerFactory().getConfirmationController().addConfirmationObserver(this);

        String page = getActivity().getIntent().getStringExtra(LaunchActivity.APP_PAGE);
        if (page == null) {
            return;
        }
        getActivity().setIntent(IntentUtils.resetAppPage(getActivity().getIntent()));
        switch (page) {
            case IntentUtils.LOCALYTICS_DEEPLINK_SEARCH:
                getControllerFactory().getPickUserController().showPickUser(IPickUserController.Destination.CONVERSATION_LIST, null);
                break;
            case IntentUtils.LOCALYTICS_DEEPLINK_SETTINGS:
                startActivity(ZetaPreferencesActivity.getDefaultIntent(getActivity()));
                break;
            case IntentUtils.LOCALYTICS_DEEPLINK_PROFILE:
                 getControllerFactory().getPickUserController().showUserProfile(getStoreFactory().getProfileStore().getSelfUser(),
                                                                               ViewUtils.getView(getActivity(),
                                                                                                 R.id.gtv__list_actions__settings));
                break;
        }
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        loadPage();
    }

    @Override
    public void onStop() {
        getStoreFactory().getInAppNotificationStore().removeInAppNotificationObserver(this);
        getControllerFactory().getCameraController().removeCameraActionObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        getControllerFactory().getPickUserController().removePickUserScreenControllerObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        getControllerFactory().getConfirmationController().removeConfirmationObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mainContainer = null;
        startuiLoadingIndicatorView = null;
        listLoadingIndicatorView = null;
        confirmationMenu = null;
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__left__camera);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onConversationListUpdated(@NonNull final ConversationsList conversationsList) {
        if (!Page.CONVERSATION_LIST.equals(getControllerFactory().getNavigationController().getCurrentPage())) {
            return;
        }
        if (conversationsList.size() > 0) {
            listLoadingIndicatorView.setType(LoadingIndicatorView.INFINITE_LOADING_BAR);
            getControllerFactory().getNavigationController().setPagerEnabled(true);
        } else {
            listLoadingIndicatorView.setType(LoadingIndicatorView.SPINNER);
            getControllerFactory().getNavigationController().setPagerEnabled(false);
        }
    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangerSender) {
        switch (conversationChangerSender) {
            case START_CONVERSATION:
            case START_CONVERSATION_FOR_CALL:
            case START_CONVERSATION_FOR_VIDEO_CALL:
            case START_CONVERSATION_FOR_CAMERA:
            case NOTIFICATION:
            case SHARING:
                stripToConversationList();
                break;
            case INCOMING_CALL:
                stripToConversationList();
                animateOnIncomingCall();
                break;
        }
    }

    private void stripToConversationList() {
        if (LayoutSpec.isTablet(getActivity())) {
            return;
        }
        // Hide possibly open self profile
        getControllerFactory().getPickUserController().hideUserProfile();
        // Hide possibly open start ui
        getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);
        if (!getControllerFactory().getPickUserController()
                                   .hidePickUser(getCurrentPickerDestination(), false)) {
            getControllerFactory().getNavigationController().setLeftPage(Page.CONVERSATION_LIST, TAG);
        }
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {
        if (syncState == null) {
            syncState = SyncState.FAILED;
        }
        switch (syncState) {
            case SYNCING:
            case WAITING:
                listLoadingIndicatorView.show();
                return;
            case COMPLETED:
                listLoadingIndicatorView.hide();
                getControllerFactory().getLoadTimeLoggerController().loginSuccess();
                break;
            case FAILED:
            default:
                listLoadingIndicatorView.hide();
        }
    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public void onVerificationStateChanged(String conversationId,
                                           Verification previousVerification,
                                           Verification currentVerification) {

    }

    private void animateOnIncomingCall() {
        int duration = getResources().getInteger(R.integer.calling_animation_duration_medium);
        int resetDelay = getResources().getInteger(R.integer.calling_animation_duration_long);

        final View rootView = getView();
        if (rootView != null) {
            rootView.animate()
                    .alpha(0)
                    .setInterpolator(new Quart.EaseOut())
                    .setDuration(duration)
                    .start();
        }

        // Reset to showing conversation list
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);
                getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(
                    getCurrentPickerDestination());
                if (rootView != null) {
                    rootView.setAlpha(1);
                }
            }
        }, resetDelay);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  NavigationControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPageVisible(Page page) {
        if (page == Page.CONVERSATION_LIST) {
            boolean enablePager = getStoreFactory().getConversationStore().getCurrentConversation() != null;
            getControllerFactory().getNavigationController().setPagerEnabled(enablePager);
        }
    }

    @Override
    public void onPageStateHasChanged(Page page) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void showIncomingPendingConnectRequest(IConversation conversation) {
        getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);
        getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), false);

        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.INBOX);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSelectedUsers(List<User> users, ConversationChangeRequester requester) {
        // TODO https://wearezeta.atlassian.net/browse/AN-3730
        getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), true);

        if (users.size() == 1) {
            User user = users.get(0);
            IConversation conversation = user.getConversation();
            if (conversation != null) {
                getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                                requester);
            }
            getControllerFactory().getTrackingController().onPeoplePickerResultsUsed(1,
                                                                                     PeoplePickerResultsUsed.Usage.NAVIGATE_TO_EXISTING_CONVERSATION);
        } else {
            getStoreFactory().getConversationStore().createGroupConversation(users, requester);
            getControllerFactory().getTrackingController().tagEvent(new CreatedGroupConversationEvent(false,
                                                                                                      (users.size() + 1)));
            getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.GROUP_CONVERSATIONS_STARTED);
            getControllerFactory().getTrackingController().onPeoplePickerResultsUsed(users.size(),
                                                                                     PeoplePickerResultsUsed.Usage.CREATE_GROUP_CONVERSATION);
        }
    }

    @Override
    public LoadingIndicatorView getLoadingViewIndicator() {
        return startuiLoadingIndicatorView;
    }

    @Override
    public IPickUserController.Destination getCurrentPickerDestination() {
        return IPickUserController.Destination.CONVERSATION_LIST;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OnBackPressedListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean onBackPressed() {
        if (closeMenu()) {
            return true;
        }

        PickUserFragment pickUserFragment = (PickUserFragment) getChildFragmentManager().findFragmentByTag(
            PickUserFragment.TAG);
        if (pickUserFragment != null &&
            pickUserFragment.onBackPressed()) {
            return true;
        }

        if (getControllerFactory().getPickUserController().isShowingPickUser(getCurrentPickerDestination())) {
            getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);
            getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), true);
            return true;
        }

        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserControllerScreenObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowPickUser(IPickUserController.Destination destination, View anchorView) {
        if (!getCurrentPickerDestination().equals(destination)) {
            onHidePickUser(getCurrentPickerDestination(), true);
            return;
        }

        Page page = getControllerFactory().getNavigationController().getCurrentLeftPage();
        switch (page) {
            // TODO: START is set as left page on tablet, fix
            case START:
            case CONVERSATION_LIST:
                Fragment fragment = getChildFragmentManager().findFragmentByTag(PickUserFragment.TAG);
                if (fragment == null ||
                    !(fragment instanceof PickUserFragment)) {
                    getControllerFactory().getOnboardingController().incrementPeoplePickerShowCount();

                    PickUserFragment pickUserFragment = PickUserFragment.newInstance(false);
                    getChildFragmentManager()
                        .beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                                             R.anim.open_new_conversation__thread_list_out,
                                             R.anim.open_new_conversation__thread_list_in,
                                             R.anim.slide_out_to_bottom_pick_user)
                        .replace(R.id.fl__conversation_list_main, pickUserFragment, PickUserFragment.TAG)
                        .addToBackStack(PickUserFragment.TAG)
                        .commit();

                    getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.OPENED_SEARCH);
                }
                break;
            case PICK_USER:
                break;
        }

        getControllerFactory().getNavigationController().setLeftPage(Page.PICK_USER, TAG);
    }

    @Override
    public void onHidePickUser(IPickUserController.Destination destination, boolean closeWithoutSelectingPeople) {
        if (!destination.equals(getCurrentPickerDestination())) {
            return;
        }

        Page page = getControllerFactory().getNavigationController().getCurrentLeftPage();
        switch (page) {
            case SEND_CONNECT_REQUEST:
            case BLOCK_USER:
            case PENDING_CONNECT_REQUEST:
                getControllerFactory().getPickUserController().hideUserProfile();
            case PICK_USER:
                ConversationListFragment conversationListFragment = (ConversationListFragment) getChildFragmentManager().findFragmentByTag(
                    ConversationListFragment.TAG);
                if (conversationListFragment != null) {
                    conversationListFragment.setScrollToConversation(!closeWithoutSelectingPeople);
                }

                getChildFragmentManager().popBackStackImmediate(PickUserFragment.TAG,
                                                                FragmentManager.POP_BACK_STACK_INCLUSIVE);

                KeyboardUtils.hideKeyboard(getActivity());
                break;
        }

        getControllerFactory().getNavigationController().setLeftPage(Page.CONVERSATION_LIST, TAG);
        getControllerFactory().getFocusController().setFocus(IFocusController.CONVERSATION_CURSOR);
    }

    private void togglePeoplePicker(boolean show) {
        if (show) {
            mainContainer.animate()
                         .alpha(1)
                         .scaleY(1)
                         .scaleX(1)
                         .setInterpolator(new Expo.EaseOut())
                         .setDuration(getResources().getInteger(R.integer.reopen_profile_source__animation_duration))
                         .setStartDelay(getResources().getInteger(R.integer.reopen_profile_source__delay))
                         .start();

        } else {
            mainContainer.animate()
                         .alpha(0)
                         .scaleY(2)
                         .scaleX(2)
                         .setInterpolator(new Expo.EaseIn())
                         .setDuration(getResources().getInteger(R.integer.reopen_profile_source__animation_duration))
                         .setStartDelay(0)
                         .start();
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UserProfileContainer
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void dismissUserProfile() {
        getControllerFactory().getPickUserController().hideUserProfile();
    }

    @Override
    public void dismissSingleUserProfile() {
        dismissUserProfile();
    }

    @Override
    public void showRemoveConfirmation(User user) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SendConnectRequestFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectRequestWasSentToUser() {
        getControllerFactory().getPickUserController().hideUserProfile();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PendingConnectRequestManagerFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAcceptedConnectRequest(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    @Override
    public void onIgnoredConnectRequest(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversationToNext(ConversationChangeRequester.CONNECT_REQUEST_IGNORED);
    }

    @Override
    public void onAcceptedPendingOutgoingConnectRequest(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.CONNECT_REQUEST_ACCEPTED);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  BlockedUserProfileFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUnblockedUser(IConversation restoredConversationWithUser) {
        getControllerFactory().getPickUserController().hideUserProfile();
        getStoreFactory().getConversationStore().setCurrentConversation(restoredConversationWithUser,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserControllerScreenObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowUserProfile(User user, View anchorView) {
        if (getControllerFactory().getPickUserController().isShowingUserProfile()) {
            return;
        }
        if (LayoutSpec.isPhone(getActivity())) {
            openUserProfileFragmentPhone(user);
        }
    }

    private void openUserProfileFragmentPhone(User user) {
        if (user == null) {
            return;
        }
        Fragment fragment;
        String tag;
        switch (user.getConnectionStatus()) {
            case CANCELLED:
            case UNCONNECTED:
                if (user.isConnected()) {
                    return;
                }
                fragment = SendConnectRequestFragment.newInstance(user.getId(), IConnectStore.UserRequester.SEARCH);
                tag = SendConnectRequestFragment.TAG;
                getControllerFactory().getNavigationController().setLeftPage(Page.SEND_CONNECT_REQUEST, TAG);
                break;
            case PENDING_FROM_OTHER:
            case PENDING_FROM_USER:
            case IGNORED:
                fragment = PendingConnectRequestManagerFragment.newInstance(user.getId(),
                                                                            null,
                                                                            ConnectRequestLoadMode.LOAD_BY_USER_ID,
                                                                            IConnectStore.UserRequester.SEARCH);
                tag = PendingConnectRequestManagerFragment.TAG;
                getControllerFactory().getNavigationController().setLeftPage(Page.PENDING_CONNECT_REQUEST, TAG);
                break;
            case BLOCKED:
                fragment = BlockedUserProfileFragment.newInstance(user.getId(), IConnectStore.UserRequester.SEARCH);
                tag = BlockedUserProfileFragment.TAG;
                getControllerFactory().getNavigationController().setLeftPage(Page.PENDING_CONNECT_REQUEST, TAG);
                break;
            default:
                return;
        }

        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.fragment_animation__send_connect_request__fade_in,
                                 R.anim.fragment_animation__send_connect_request__zoom_exit,
                                 R.anim.fragment_animation__send_connect_request__zoom_enter,
                                 R.anim.fragment_animation__send_connect_request__fade_out)
            .replace(R.id.fl__conversation_list__profile_overlay,
                     fragment,
                     tag)
            .addToBackStack(tag)
            .commit();

        togglePeoplePicker(false);
    }


    @Override
    public void onHideUserProfile() {
        // Profiles are handled in dialog on tablet
        if (LayoutSpec.isTablet(getActivity())) {
            return;
        }

        getControllerFactory().getNavigationController().setLeftPage(Page.PICK_USER, TAG);
        getChildFragmentManager().popBackStackImmediate();
        togglePeoplePicker(true);
    }

    /**
     * Decides based on state returned from PickUserController whether to show START UI or CONVERSATIONLIST
     */
    private void loadPage() {
        Page page = getControllerFactory().getNavigationController().getCurrentLeftPage();

        switch (page) {
            // TODO: START is set as left page on tablet, fix
            case START:
            case CONVERSATION_LIST:
                getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);
                break;
            case PICK_USER:
                getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(true);
                getControllerFactory().getPickUserController().showPickUser(IPickUserController.Destination.CONVERSATION_LIST, null);
                break;
            case BLOCK_USER:
            case PENDING_CONNECT_REQUEST:
            case SEND_CONNECT_REQUEST:
                togglePeoplePicker(false);
                break;
            case COMMON_USER_PROFILE:
                togglePeoplePicker(false);
                break;
        }
    }

    @Override
    public void onOpenCamera(CameraContext cameraContext) {

    }

    @Override
    public void onCloseCamera(CameraContext cameraContext) {

    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  CameraFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBitmapSelected(final ImageAsset imageAsset,
                                 final boolean imageFromCamera,
                                 CameraContext cameraContext) {
    }

    @Override
    public void onCameraNotAvailable() {

    }

    private void showNoNetworkError() {
        ViewUtils.showAlertDialog(getActivity(),
                                  R.string.alert_dialog__no_network__header,
                                  R.string.profile_pic__no_network__message,
                                  R.string.alert_dialog__confirmation,
                                  null, true);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        listLoadingIndicatorView.setColor(color);
        confirmationMenu.setButtonColor(color);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConfirmationObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onRequestConfirmation(ConfirmationRequest confirmationRequest,  @IConfirmationController.ConfirmationMenuRequester int requester) {
        if (LayoutSpec.isPhone(getActivity()) ||
            requester != IConfirmationController.CONVERSATION_LIST) {
            return;
        }
        confirmationMenu.onRequestConfirmation(confirmationRequest);
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SlidingListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onOptionMenuStateHasChanged(OptionsMenu.State state) {
        switch (state) {
            case OPEN:
                break;
            case OPENING:
                if (LayoutSpec.isPhone(getActivity())) {
                    getControllerFactory().getNavigationController().setLeftPage(Page.CONVERSATION_MENU_OVER_CONVERSATION_LIST,
                                                                                 TAG);
                }
                break;
            case CLOSING:
                break;
            case CLOSED:
                if (LayoutSpec.isPhone(getActivity())) {
                    getControllerFactory().getNavigationController().setLeftPage(Page.CONVERSATION_LIST, TAG);
                }
                break;
        }
    }

    @Override
    public void onOptionsItemClicked(IConversation conversation, User user, OptionsMenuItem item) {
        switch (item) {
            case ARCHIVE:
                getStoreFactory().getConversationStore().archive(conversation, true);
                getControllerFactory().getTrackingController().tagEvent(new ArchivedConversationEvent(conversation.getType().toString()));
                break;
            case UNARCHIVE:
                getStoreFactory().getConversationStore().archive(conversation, false);
                getControllerFactory().getTrackingController().tagEvent(new UnarchivedConversationEvent(conversation.getType().toString()));
                break;
            case SILENCE:
                conversation.setMuted(true);
                break;
            case UNSILENCE:
                conversation.setMuted(false);
                break;
            case LEAVE:
                leaveConversation(conversation);
                break;
            case DELETE:
                deleteConversation(conversation);
                break;
            case BLOCK:
                showBlockConfirmation(user);
                break;
            case UNBLOCK:
                user.unblock();
                break;
            case CALL:
                callConversation(conversation);
                break;
            case PICTURE:
                sendPictureToConversation(conversation);
                break;
        }

        closeMenu();
    }

    @Override
    public OptionsMenuControl getOptionsMenuControl() {
        return optionsMenuControl;
    }

    private boolean closeMenu() {
        return optionsMenuControl.close();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationScreenControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {

    }

    @Override
    public void onHideParticipants(boolean backOrButtonPressed,
                                   boolean hideByConversationChange,
                                   boolean isSingleConversation) {

    }

    @Override
    public void onShowEditConversationName(boolean show) {

    }

    @Override
    public void setListOffset(int offset) {

    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {

    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {

    }

    @Override
    public void onConversationLoaded() {

    }

    @Override
    public void onShowUser(User user) {

    }

    @Override
    public void onHideUser() {

    }

    @Override
    public void onAddPeopleToConversation() {

    }

    @Override
    public void onShowConversationMenu(@IConversationScreenController.ConversationMenuRequester int requester,
                                       IConversation conversation,
                                       View anchorView) {
        if ((requester != IConversationScreenController.CONVERSATION_LIST_SWIPE &&
            requester != IConversationScreenController.CONVERSATION_LIST_LONG_PRESS) ||
            getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return;
        }

        optionsMenuControl.setTitle(conversation.getName());
        optionsMenuControl.createMenu(conversation,
                                      requester,
                                      getControllerFactory().getThemeController().getOptionsDarkTheme());
        optionsMenuControl.open();
    }

    @Override
    public void onShowOtrClient(OtrClient otrClient, User user) {

    }

    @Override
    public void onShowCurrentOtrClient() {

    }

    @Override
    public void onHideOtrClient() {

    }

    @Override
    public void onShowLikesList(Message message) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationListFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    public void leaveConversation(final IConversation conversation) {
        closeMenu();
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                if (getStoreFactory() == null ||
                    getControllerFactory() == null ||
                    getStoreFactory().isTornDown() ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                getControllerFactory().getTrackingController().tagEvent(new LeaveGroupConversationEvent(true,
                                                                                                        getStoreFactory().getConversationStore().getCurrentConversation().getUsers().size()));

                getStoreFactory().getConversationStore().leave(conversation);
                getStoreFactory().getConversationStore().setCurrentConversationToNext(
                    ConversationChangeRequester.LEAVE_CONVERSATION);
            }

            @Override
            public void negativeButtonClicked() {
                if (getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                getControllerFactory().getTrackingController().tagEvent(new LeaveGroupConversationEvent(false,
                                                                                                        getStoreFactory().getConversationStore().getCurrentConversation().getUsers().size()));
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        };
        String header = getString(R.string.confirmation_menu__meta_remove);
        String text = getString(R.string.confirmation_menu__meta_remove_text);
        String confirm = getString(R.string.confirmation_menu__confirm_leave);
        String cancel = getString(R.string.confirmation_menu__cancel);


        ConfirmationRequest request = new ConfirmationRequest.Builder(IConfirmationController.LEAVE_CONVERSATION)
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(getControllerFactory().getThemeController().getOptionsDarkTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request,
                                                                               IConfirmationController.CONVERSATION_LIST);

        getStoreFactory().getMediaStore().playSound(R.raw.alert);
        getControllerFactory().getVibratorController().vibrate(R.array.alert);
    }

    public void callConversation(final IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation, ConversationChangeRequester.CONVERSATION_LIST);
        getControllerFactory().getCallingController().startCall(false);
    }

    public void sendPictureToConversation(final IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation, ConversationChangeRequester.CONVERSATION_LIST);
        getControllerFactory().getCameraController().openCamera(CameraContext.MESSAGE);
    }

    public void deleteConversation(final IConversation conversation) {
        closeMenu();
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {

            }

            @Override
            public void negativeButtonClicked() {
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {
                if (getStoreFactory() == null ||
                    getStoreFactory().isTornDown() ||
                    getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }

                if (!confirmed) {
                    getControllerFactory().getTrackingController().tagEvent(new DeleteConversationEvent(ConversationType.getValue(conversation),
                                                                                                        DeleteConversationEvent.Context.LIST,
                                                                                                        DeleteConversationEvent.Response.CANCEL));
                    return;
                }


                boolean deleteCurrentConversation = conversation.getId().equals(getStoreFactory().getConversationStore().getCurrentConversation().getId());
                getStoreFactory().getConversationStore().deleteConversation(conversation, checkboxIsSelected);
                getControllerFactory().getTrackingController().tagEvent(new DeleteConversationEvent(ConversationType.getValue(
                    conversation),
                                                                                                    DeleteConversationEvent.Context.LIST,
                                                                                                    DeleteConversationEvent.Response.DELETE));

                if (deleteCurrentConversation) {
                    getStoreFactory().getConversationStore().setCurrentConversationToNext(ConversationChangeRequester.DELETE_CONVERSATION);
                }
            }
        };
        String header = getString(R.string.confirmation_menu__meta_delete);
        String text = getString(R.string.confirmation_menu__meta_delete_text);
        String confirm = getString(R.string.confirmation_menu__confirm_delete);
        String cancel = getString(R.string.confirmation_menu__cancel);
        String checkboxLabel = "";
        if (conversation.getType() == IConversation.Type.GROUP) {
            checkboxLabel = getString(R.string.confirmation_menu__delete_conversation__checkbox__label);
        }

        ConfirmationRequest request = new ConfirmationRequest.Builder(IConfirmationController.DELETE_CONVERSATION)
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withCheckboxLabel(checkboxLabel)
            .withWireTheme(getControllerFactory().getThemeController().getOptionsDarkTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request,
                                                                               IConfirmationController.CONVERSATION_LIST);

        getStoreFactory().getMediaStore().playSound(R.raw.alert);
        getControllerFactory().getVibratorController().vibrate(R.array.alert);
    }


    private void showBlockConfirmation(final User user) {
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                if (getStoreFactory() == null ||
                    getControllerFactory() == null ||
                    getStoreFactory().isTornDown() ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                boolean blockingCurrentConversation = user.getConversation().getId().equals(getStoreFactory().getConversationStore().getCurrentConversation().getId());
                getStoreFactory().getConnectStore().blockUser(user);
                if (blockingCurrentConversation) {
                    getStoreFactory().getConversationStore().setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER);
                }
                getControllerFactory().getTrackingController().tagEvent(new BlockingEvent(BlockingEvent.ConformationResponse.BLOCK));
            }

            @Override
            public void negativeButtonClicked() {
                if (getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                getControllerFactory().getTrackingController().tagEvent(new BlockingEvent(BlockingEvent.ConformationResponse.CANCEL));
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        };
        String header = getString(R.string.confirmation_menu__block_header);
        String text = getString(R.string.confirmation_menu__block_text_with_name, user.getDisplayName());
        String confirm = getString(R.string.confirmation_menu__confirm_block);
        String cancel = getString(R.string.confirmation_menu__cancel);

        ConfirmationRequest request = new ConfirmationRequest.Builder(IConfirmationController.BLOCK_CONNECTED)
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(getControllerFactory().getThemeController().getOptionsDarkTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request,
                                                                               IConfirmationController.CONVERSATION_LIST);

        getStoreFactory().getMediaStore().playSound(R.raw.alert);
        getControllerFactory().getVibratorController().vibrate(R.array.alert);
    }

    @Override
    public void onConnectRequestInboxConversationsLoaded(List<IConversation> conversations,
                                                         InboxLoadRequester inboxLoadRequester) {

    }

    @Override
    public void onIncomingMessage(Message message) {

    }

    @Override
    public void onIncomingKnock(KnockingEvent knock) {

    }

    @Override
    public void onSyncError(ErrorsList.ErrorDescription errorDescription) {
        if (errorDescription.getType() != ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION) {
            return;
        }

        if (getControllerFactory().getNavigationController().getCurrentPage() == Page.MESSAGE_STREAM) {
            return;
        }

        Toast.makeText(getActivity(), "New to unverified - will dismiss!", Toast.LENGTH_SHORT).show();
        errorDescription.dismiss();
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
