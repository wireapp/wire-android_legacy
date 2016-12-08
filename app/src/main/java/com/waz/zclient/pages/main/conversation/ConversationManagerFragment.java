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
package com.waz.zclient.pages.main.conversation;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.MessageContent;
import com.waz.api.OtrClient;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.api.Verification;
import com.waz.api.Message;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.DrawingObserver;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.location.LocationObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.events.group.AddedMemberToGroupEvent;
import com.waz.zclient.controllers.tracking.events.group.CreatedGroupConversationEvent;
import com.waz.zclient.controllers.tracking.events.peoplepicker.PeoplePickerResultsUsed;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.controllers.tracking.events.media.SentPictureEvent;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.drawing.DrawingFragment;
import com.waz.zclient.pages.main.participants.ParticipantFragment;
import com.waz.zclient.pages.main.participants.SingleParticipantFragment;
import com.waz.zclient.pages.main.participants.TabbedParticipantBodyFragment;
import com.waz.zclient.pages.main.pickuser.PickUserFragment;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.camera.CameraFragment;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import java.util.ArrayList;
import java.util.List;

public class ConversationManagerFragment extends BaseFragment<ConversationManagerFragment.Container> implements ConversationFragment.Container,
                                                                                                                ParticipantFragment.Container,
                                                                                                                LikesListFragment.Container,
                                                                                                                OnBackPressedListener,
                                                                                                                ConversationScreenControllerObserver,
                                                                                                                DrawingObserver,
                                                                                                                ConversationStoreObserver,
                                                                                                                DrawingFragment.Container,
                                                                                                                CameraFragment.Container,
                                                                                                                PickUserFragment.Container,
                                                                                                                PickUserControllerScreenObserver,
                                                                                                                ParticipantsStoreObserver,
                                                                                                                LocationObserver,
                                                                                                                SingleParticipantFragment.Container {
    public static final String TAG = ConversationManagerFragment.class.getName();
    public static final float PARALLAX_FACTOR = .4f;

    private LoadingIndicatorView loadingIndicatorView;

    // doesn't need to be restored
    private int headerHeight;
    private boolean groupConversation;
    private User otherUser;
    private IPickUserController.Destination pickUserDestination;

    private final ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation model) {
            groupConversation = model.getType() == IConversation.Type.GROUP;
            otherUser = groupConversation ? null : model.getOtherParticipant();
        }
    };

    public static ConversationManagerFragment newInstance() {
        return new ConversationManagerFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation_manager, container, false);
        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getChildFragmentManager();
            fragmentManager.beginTransaction()
                           .add(R.id.fl__conversation_manager__message_list_container,
                                ConversationFragment.newInstance(),
                                ConversationFragment.TAG)
                           .commit();
        }

        loadingIndicatorView = ViewUtils.getView(view, R.id.liv__conversation_manager__loading_indicator);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().getConversationStore().addConversationStoreObserver(this);
        getStoreFactory().getParticipantsStore().addParticipantsStoreObserver(this);
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getDrawingController().addDrawingObserver(this);
        getControllerFactory().getCameraController().addCameraActionObserver(this);
        getControllerFactory().getPickUserController().addPickUserScreenControllerObserver(this);
        IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (currentConversation != null) {
            getStoreFactory().getParticipantsStore().setCurrentConversation(currentConversation);
        }
        getControllerFactory().getLocationController().addObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getLocationController().removeObserver(this);
        getControllerFactory().getPickUserController().removePickUserScreenControllerObserver(this);
        getControllerFactory().getCameraController().removeCameraActionObserver(this);
        getControllerFactory().getDrawingController().removeDrawingObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getStoreFactory().getParticipantsStore().removeParticipantsStoreObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        loadingIndicatorView = null;
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Fragment fragment =  getChildFragmentManager().findFragmentByTag(CameraFragment.TAG);
        if (fragment != null) {
            fragment.onActivityResult(requestCode,
                                      resultCode,
                                      data);
        }
    }


    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__conversation_manager__message_list_container);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        if (fragment instanceof ParticipantFragment) {
            getControllerFactory().getConversationScreenController().hideParticipants(true, false);
            return true;
        }

        if (fragment instanceof PickUserFragment) {
            getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), true);
            return true;
        }

        if (fragment instanceof LikesListFragment) {
            getChildFragmentManager().popBackStack(LikesListFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return true;
        }

        if (getControllerFactory().getConversationScreenController().isShowingParticipant()) {
            getControllerFactory().getConversationScreenController().hideParticipants(true, false);
            return true;
        }

        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Conversation Controller Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {
        if (LayoutSpec.isPhone(getContext())) {
            KeyboardUtils.hideKeyboard(getActivity());
        }
        this.getControllerFactory().getOnboardingController().incrementParticipantsShowCount();
        this.getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT, TAG);

        getStoreFactory().getInAppNotificationStore().setUserLookingAtParticipants(true);

        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                                 R.anim.open_new_conversation__thread_list_out,
                                 R.anim.open_new_conversation__thread_list_in,
                                 R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__conversation_manager__message_list_container,
                     ParticipantFragment.newInstance(IConnectStore.UserRequester.PARTICIPANTS,
                                                     TabbedParticipantBodyFragment.USER_PAGE),
                     ParticipantFragment.TAG)
            .addToBackStack(ParticipantFragment.TAG)
            .commit();
    }

    @Override
    public void onHideParticipants(boolean backOrCloseButtonPressed, boolean hideByConversationChange, boolean isSingleConversation) {
        this.getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        getStoreFactory().getInAppNotificationStore().setUserLookingAtParticipants(false);
        getChildFragmentManager().popBackStack(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onShowEditConversationName(boolean show) {

    }

    @Override
    public void setListOffset(int offset) {
        if (getContainer() == null || getControllerFactory().getNavigationController().getCurrentRightPage() == Page.PARTICIPANT) {
            return;
        }
        float fac = (PARALLAX_FACTOR * offset) / headerHeight;
        getControllerFactory().getNavigationController().setScreenOffsetYFactor(-fac);
    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {
        headerHeight = participantHeaderHeight;
    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {

    }

    @Override
    public void onConversationLoaded() {

    }

    @Override
    public void onShowUser(User user) {
        if (LayoutSpec.isPhone(getContext())) {
            KeyboardUtils.hideKeyboard(getActivity());
        }
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
        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                                 R.anim.open_new_conversation__thread_list_out,
                                 R.anim.open_new_conversation__thread_list_in,
                                 R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__conversation_manager__message_list_container,
                     LikesListFragment.newInstance(message),
                     LikesListFragment.TAG)
            .addToBackStack(LikesListFragment.TAG)
            .commit();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {

    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangerSender) {
        if (conversationChangerSender == ConversationChangeRequester.START_CONVERSATION ||
            conversationChangerSender == ConversationChangeRequester.INCOMING_CALL ||
            conversationChangerSender == ConversationChangeRequester.LEAVE_CONVERSATION ||
            conversationChangerSender == ConversationChangeRequester.DELETE_CONVERSATION ||
            conversationChangerSender == ConversationChangeRequester.BLOCK_USER) {
            if (getControllerFactory().getNavigationController().getCurrentRightPage() == Page.CAMERA &&
                !fromConversation.getId().equals(toConversation.getId())) {
                getControllerFactory().getCameraController().closeCamera(CameraContext.MESSAGE);
            }

            getControllerFactory().getConversationScreenController().hideParticipants(false, (conversationChangerSender == ConversationChangeRequester.START_CONVERSATION));

            closeLikesList();
        }
        if (toConversation != null) {
            getStoreFactory().getParticipantsStore().setCurrentConversation(toConversation);
            conversationModelObserver.setAndUpdate(toConversation);
        }
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public void onVerificationStateChanged(String conversationId,
                                           Verification previousVerification,
                                           Verification currentVerification) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Camera callbacks
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowDrawing(ImageAsset image, DrawingController.DrawingDestination drawingDestination, IDrawingController.DrawingMethod method) {
        getChildFragmentManager().beginTransaction()
                                 .setCustomAnimations(R.anim.camera__from__profile__transition,
                                                      R.anim.profile_fade_out_form,
                                                      R.anim.profile_fade_in_form,
                                         R.anim.profile_fade_out_form)
                                 .replace(R.id.fl__conversation_manager__message_list_container,
                                          DrawingFragment.newInstance(image, drawingDestination, method),
                                          DrawingFragment.TAG)
                                 .addToBackStack(DrawingFragment.TAG)
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.DRAWING, TAG);
    }

    @Override
    public void onHideDrawing(DrawingController.DrawingDestination drawingDestination, boolean imageSent) {

        switch (drawingDestination) {
            case CAMERA_PREVIEW_VIEW:
                getChildFragmentManager().popBackStack(DrawingFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getControllerFactory().getCameraController().closeCamera(CameraContext.MESSAGE);
                getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
                break;
            case SINGLE_IMAGE_VIEW:
            case SKETCH_BUTTON:
                getChildFragmentManager().popBackStack(DrawingFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
                break;
        }
    }

    @Override
    public void dismissUserProfile() {
        dismissSingleUserProfile();
    }

    @Override
    public void dismissSingleUserProfile() {
        getChildFragmentManager().popBackStackImmediate();
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
    }

    @Override
    public void showRemoveConfirmation(User user) {

    }

    @Override
    public void onBitmapSelected(ImageAsset imageAsset, boolean imageFromCamera, CameraContext cameraContext) {
        if (cameraContext != CameraContext.MESSAGE) {
            return;
        }
        getStoreFactory().getConversationStore().sendMessage(imageAsset);
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(null);

        // Photo sent via contacts quick menu
        TrackingUtils.onSentPhotoMessage(getControllerFactory().getTrackingController(),
                                         getStoreFactory().getConversationStore().getCurrentConversation(),
                                         imageFromCamera ? SentPictureEvent.Source.CAMERA
                                                         : SentPictureEvent.Source.GALLERY,
                                         SentPictureEvent.Method.FULL_SCREEN);

        getControllerFactory().getCameraController().closeCamera(CameraContext.MESSAGE);
    }

    @Override
    public void onCameraNotAvailable() {

    }

    @Override
    public void onOpenCamera(CameraContext cameraContext) {
        if (cameraContext != CameraContext.MESSAGE) {
            return;
        }
        getChildFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.camera__from__message_stream_transition,
                        R.anim.message_stream__to__camera_transition,
                        R.anim.message_stream__from__camera_transition,
                        R.anim.camera__to__message_stream__transition)
                .replace(R.id.fl__conversation_manager__message_list_container,
                         CameraFragment.newInstance(CameraContext.MESSAGE),
                         CameraFragment.TAG)
                .addToBackStack(CameraFragment.TAG)
                .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.CAMERA, TAG);
    }

    @Override
    public void onCloseCamera(CameraContext cameraContext) {
        if (cameraContext != CameraContext.MESSAGE) {
            return;
        }
        getChildFragmentManager().popBackStackImmediate();
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public void dismissDialog() {

    }

    @Override
    public void showIncomingPendingConnectRequest(IConversation conversation) {
        // noop
    }

    @Override
    public void onSelectedUsers(List<User> users, ConversationChangeRequester requester) {
        // TODO https://wearezeta.atlassian.net/browse/AN-3730
        getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), false);
        getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);

        IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (currentConversation.getType() == IConversation.Type.ONE_TO_ONE) {
            getStoreFactory().getConversationStore().createGroupConversation(users, requester);
            if (!getStoreFactory().getNetworkStore().hasInternetConnection()) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.conversation__create_group_conversation__no_network__title,
                                          R.string.conversation__create_group_conversation__no_network__message,
                                          R.string.conversation__create_group_conversation__no_network__button,
                                          null, true);
            }
            getControllerFactory().getTrackingController().tagEvent(new CreatedGroupConversationEvent(true, (users.size() + 1)));
            getControllerFactory().getTrackingController()
                                  .onPeoplePickerResultsUsed(users.size(),
                                                             PeoplePickerResultsUsed.Usage.CREATE_GROUP_CONVERSATION);
        } else if (currentConversation.getType() == IConversation.Type.GROUP) {
            currentConversation.addMembers(users);
            if (!getStoreFactory().getNetworkStore().hasInternetConnection()) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.conversation__add_user__no_network__title,
                                          R.string.conversation__add_user__no_network__message,
                                          R.string.conversation__add_user__no_network__button,
                                          null, true);
            }
            getControllerFactory().getTrackingController().tagEvent(new AddedMemberToGroupEvent(getParticipantsCount(), users.size()));
            getControllerFactory().getTrackingController().onPeoplePickerResultsUsed(users.size(),
                                                                                     PeoplePickerResultsUsed.Usage.ADD_MEMBERS_TO_EXISTING_CONVERSATION);
        }
        getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.USERS_ADDED_TO_CONVERSATIONS);
    }

    @Override
    public LoadingIndicatorView getLoadingViewIndicator() {
        return loadingIndicatorView;
    }

    @Override
    public IPickUserController.Destination getCurrentPickerDestination() {
        return pickUserDestination;
    }

    @Override
    public void onShowPickUser(IPickUserController.Destination destination, View anchorView) {
        if (!(destination.equals(IPickUserController.Destination.CURSOR) ||
              destination.equals(IPickUserController.Destination.PARTICIPANTS))) {
            return;
        }
        pickUserDestination = destination;
        if (LayoutSpec.isPhone(getContext())) {
            KeyboardUtils.hideKeyboard(getActivity());
        }

        getControllerFactory().getNavigationController().setRightPage(Page.PICK_USER_ADD_TO_CONVERSATION, TAG);
        if (!groupConversation && otherUser != null) {
            getControllerFactory().getPickUserController().addUser(otherUser);
        }
        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                                 R.anim.open_new_conversation__thread_list_out,
                                 R.anim.open_new_conversation__thread_list_in,
                                 R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__conversation_manager__message_list_container,
                     PickUserFragment.newInstance(true, groupConversation),
                     PickUserFragment.TAG)
            .addToBackStack(PickUserFragment.TAG)
            .commit();
    }

    @Override
    public void onHidePickUser(IPickUserController.Destination destination, boolean closeWithoutSelectingPeople) {
        if (!destination.equals(getCurrentPickerDestination())) {
            return;
        }
        if (IPickUserController.Destination.CURSOR.equals(getCurrentPickerDestination())) {
            getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        } else {
            getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT, TAG);
        }
        getChildFragmentManager().popBackStack(PickUserFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onShowUserProfile(User user, View anchorView) {
        // noop
    }

    @Override
    public void onHideUserProfile() {
        // noop
    }

    // ParticipantStoreObserver

    @Override
    public void conversationUpdated(IConversation conversation) {

    }

    @Override
    public void participantsUpdated(UsersList participants) {
        ArrayList<String> participantIds = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            participantIds.add(participants.get(i).getId());
        }

        // Exclude existing participants of conversation when adding people
        getStoreFactory().getPickUserStore().setExcludedUsers(participantIds.toArray(new String[participantIds.size()]));
    }

    @Override
    public void otherUserUpdated(User otherUser) {
        getStoreFactory().getPickUserStore().setExcludedUsers(new String[] {});
    }

    private int getParticipantsCount() {
        return getStoreFactory().getConversationStore().getCurrentConversation().getUsers().size();
    }

    @Override
    public void onShowShareLocation() {
        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.fl__conversation_manager__message_list_container,
                                          LocationFragment.newInstance(),
                                          LocationFragment.TAG)
                                 .addToBackStack(LocationFragment.TAG)
                                 .commit();
        getStoreFactory().getInAppNotificationStore().setUserSendingPicture(true);
        getControllerFactory().getNavigationController().setRightPage(Page.SHARE_LOCATION, TAG);
    }

    @Override
    public void onHideShareLocation(MessageContent.Location location) {
        if (location != null) {
            getStoreFactory().getConversationStore().sendMessage(location);
        }
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        getStoreFactory().getInAppNotificationStore().setUserSendingPicture(false);
        getChildFragmentManager().popBackStack(LocationFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void closeLikesList() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__conversation_manager__message_list_container);
        if (fragment instanceof LikesListFragment) {
            getChildFragmentManager().popBackStack(LikesListFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
