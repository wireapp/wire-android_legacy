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
package com.waz.zclient.pages.main.conversation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.model.ConvId;
import com.waz.model.ConversationData;
import com.waz.model.IntegrationId;
import com.waz.model.MessageData;
import com.waz.model.ProviderId;
import com.waz.model.UserId;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.collection.controllers.CollectionController;
import com.waz.zclient.collection.fragments.CollectionFragment;
import com.waz.zclient.controllers.collections.CollectionsObserver;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.DrawingObserver;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.location.LocationObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.integrations.IntegrationDetailsFragment;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.drawing.DrawingFragment;
import com.waz.zclient.pages.main.participants.ParticipantFragment;
import com.waz.zclient.pages.main.participants.SingleParticipantFragment;
import com.waz.zclient.pages.main.participants.TabbedParticipantBodyFragment;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.camera.CameraFragment;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.usersearch.PickUserFragment;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.ConversationFragment;
import com.waz.zclient.views.LoadingIndicatorView;

import java.util.List;

public class ConversationManagerFragment extends BaseFragment<ConversationManagerFragment.Container> implements ParticipantFragment.Container,
                                                                                                                LikesListFragment.Container,
                                                                                                                OnBackPressedListener,
                                                                                                                ConversationScreenControllerObserver,
                                                                                                                DrawingObserver,
                                                                                                                DrawingFragment.Container,
                                                                                                                CameraFragment.Container,
                                                                                                                PickUserFragment.Container,
                                                                                                                PickUserControllerScreenObserver,
                                                                                                                LocationObserver,
                                                                                                                SingleParticipantFragment.Container,
                                                                                                                CollectionsObserver {
    public static final String TAG = ConversationManagerFragment.class.getName();

    private LoadingIndicatorView loadingIndicatorView;

    // doesn't need to be restored
    private boolean groupConversation;
    private IPickUserController.Destination pickUserDestination;

    private final ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation model) {
            groupConversation = model.getType() == IConversation.Type.GROUP;
        }
    };

    public static ConversationManagerFragment newInstance() {
        return new ConversationManagerFragment();
    }


    private final Callback callback = new Callback<ConversationController.ConversationChange>() {
        @Override
        public void callback(ConversationController.ConversationChange change) {
            if (change.noChange()) {
                return;
            }

            if (change.requester() == ConversationChangeRequester.START_CONVERSATION ||
                change.requester() == ConversationChangeRequester.INCOMING_CALL ||
                change.requester() == ConversationChangeRequester.LEAVE_CONVERSATION ||
                change.requester() == ConversationChangeRequester.DELETE_CONVERSATION ||
                change.requester() == ConversationChangeRequester.BLOCK_USER) {
                if (getControllerFactory().getNavigationController().getCurrentRightPage() == Page.CAMERA &&
                    !change.noChange()) {
                    getControllerFactory().getCameraController().closeCamera(CameraContext.MESSAGE);
                }

                getControllerFactory().getConversationScreenController().hideParticipants(false, (change.requester() == ConversationChangeRequester.START_CONVERSATION));

                closeLikesList();
            }

            if (change.toConvId() != null) {
                IConversation iConv = inject(ConversationController.class).iConv(change.toConvId());
                getStoreFactory().participantsStore().setCurrentConversation(iConv);
                conversationModelObserver.setAndUpdate(iConv);
            }

            CollectionController colController = inject(CollectionController.class);
            if (colController != null) {
                colController.closeCollection();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation_manager, container, false);
        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getChildFragmentManager();
            fragmentManager.beginTransaction()
                           .add(R.id.fl__conversation_manager__message_list_container,
                                ConversationFragment.apply(),
                                ConversationFragment.TAG())
                           .commit();
        }

        loadingIndicatorView = ViewUtils.getView(view, R.id.liv__conversation_manager__loading_indicator);

        inject(ConversationController.class).addConvChangedCallback(callback);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getDrawingController().addDrawingObserver(this);
        getControllerFactory().getCameraController().addCameraActionObserver(this);
        getControllerFactory().getPickUserController().addPickUserScreenControllerObserver(this);

        getControllerFactory().getLocationController().addObserver(this);

        final CollectionController colController = inject(CollectionController.class);
        if (colController != null) {
            colController.addObserver(this);
        }

        final ConversationController ctrl = inject(ConversationController.class);

        IConversation curConv = ctrl.iCurrentConv();
        if (curConv != null) {
            getStoreFactory().participantsStore().setCurrentConversation(curConv);
        }
    }

    @Override
    public void onStop() {
        getControllerFactory().getLocationController().removeObserver(this);
        getControllerFactory().getPickUserController().removePickUserScreenControllerObserver(this);
        getControllerFactory().getCameraController().removeCameraActionObserver(this);
        getControllerFactory().getDrawingController().removeDrawingObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        inject(CollectionController.class).removeObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        loadingIndicatorView = null;
        inject(ConversationController.class).removeConvChangedCallback(callback);
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
            getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination());
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
        this.getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT, TAG);

        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                                 R.anim.open_new_conversation__thread_list_out,
                                 R.anim.open_new_conversation__thread_list_in,
                                 R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__conversation_manager__message_list_container,
                     ParticipantFragment.newInstance(IConnectStore.UserRequester.PARTICIPANTS,
                         showDeviceTabIfSingle ? TabbedParticipantBodyFragment.DEVICE_PAGE : TabbedParticipantBodyFragment.USER_PAGE),
                     ParticipantFragment.TAG)
            .addToBackStack(ParticipantFragment.TAG)
            .commit();
    }

    @Override
    public void onHideParticipants(boolean backOrCloseButtonPressed, boolean hideByConversationChange, boolean isSingleConversation) {
        this.getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        getChildFragmentManager().popBackStack(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onShowEditConversationName(boolean show) {

    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {
    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {

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
    public void onShowConversationMenu(boolean inConvList, ConvId convId) {

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

    @Override
    public void onShowIntegrationDetails(ProviderId providerId, IntegrationId integrationId) {
        this.getControllerFactory().getNavigationController().setRightPage(Page.INTEGRATION_DETAILS, TAG);

        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                R.anim.open_new_conversation__thread_list_out,
                R.anim.open_new_conversation__thread_list_in,
                R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__conversation_manager__message_list_container,
                IntegrationDetailsFragment.newInstance(providerId, integrationId),
                IntegrationDetailsFragment.Tag())
            .addToBackStack(IntegrationDetailsFragment.Tag())
            .commit();
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
    public void openCollection() {
        getChildFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                R.anim.slide_out_to_bottom_pick_user,
                R.anim.slide_in_from_bottom_pick_user,
                R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__conversation_manager__message_list_container,
                CollectionFragment.newInstance(),
                CollectionFragment.TAG())
            .addToBackStack(CollectionFragment.TAG())
            .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.COLLECTION, TAG);
    }

    @Override
    public void closeCollection() {
        getChildFragmentManager().popBackStack(CollectionFragment.TAG(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
    }

    @Override
    public void shareCollectionItem(MessageData messageData) {

    }

    @Override
    public void closeCollectionShare() {

    }

    @Override
    public void nextItemRequested() {

    }

    @Override
    public void previousItemRequested() {

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
    public void onBitmapSelected(ImageAsset imageAsset, final boolean imageFromCamera, CameraContext cameraContext) {
        if (cameraContext != CameraContext.MESSAGE) {
            return;
        }

        inject(ConversationController.class).sendMessage(imageAsset);
        getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(null);
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
    public void showIncomingPendingConnectRequest(ConvId conv) {
        // noop
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
    public void onShowPickUser(IPickUserController.Destination destination) {
        if (!(destination.equals(IPickUserController.Destination.CURSOR) ||
              destination.equals(IPickUserController.Destination.PARTICIPANTS))) {
            return;
        }
        pickUserDestination = destination;
        if (LayoutSpec.isPhone(getContext())) {
            KeyboardUtils.hideKeyboard(getActivity());
        }

        getControllerFactory().getNavigationController().setRightPage(Page.PICK_USER_ADD_TO_CONVERSATION, TAG);

//        getChildFragmentManager()
//            .beginTransaction()
//            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
//                                 R.anim.open_new_conversation__thread_list_out,
//                                 R.anim.open_new_conversation__thread_list_in,
//                                 R.anim.slide_out_to_bottom_pick_user)
//            .replace(R.id.fl__conversation_manager__message_list_container,
//                     PickUserFragment.newInstance(true, groupConversation, inject(ConversationController.class).getCurrentConvId().str()),
//                     PickUserFragment.TAG())
//            .addToBackStack(PickUserFragment.TAG())
//            .commit();
    }

    @Override
    public void onHidePickUser(IPickUserController.Destination destination) {
        if (!destination.equals(getCurrentPickerDestination())) {
            return;
        }
        if (IPickUserController.Destination.CURSOR.equals(getCurrentPickerDestination())) {
            getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        } else {
            getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT, TAG);
        }
        getChildFragmentManager().popBackStack(PickUserFragment.TAG(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onShowUserProfile(UserId userId, View anchorView) {
        // noop
    }

    @Override
    public void onHideUserProfile() {
        // noop
    }

    @Override
    public void onShowShareLocation() {
        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.fl__conversation_manager__message_list_container,
                                          LocationFragment.newInstance(),
                                          LocationFragment.TAG)
                                 .addToBackStack(LocationFragment.TAG)
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.SHARE_LOCATION, TAG);
    }

    @Override
    public void onHideShareLocation(MessageContent.Location location) {
        if (location != null) {
            inject(ConversationController.class).sendMessage(location);
        }
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
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
