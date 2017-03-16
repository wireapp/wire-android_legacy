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
package com.waz.zclient.pages.main;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.OtrClient;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.model.MessageData;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.collections.CollectionsObserver;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.DrawingObserver;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.giphy.GiphyObserver;
import com.waz.zclient.controllers.location.LocationObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.controllers.usernames.UsernamesControllerObserver;
import com.waz.zclient.conversation.CollectionController;
import com.waz.zclient.conversation.CollectionFragment;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.events.media.SentPictureEvent;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.ConnectRequestInboxManagerFragment;
import com.waz.zclient.pages.main.connect.ConnectRequestLoadMode;
import com.waz.zclient.pages.main.connect.PendingConnectRequestManagerFragment;
import com.waz.zclient.pages.main.conversation.ConversationFragment;
import com.waz.zclient.pages.main.conversation.LocationFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationlist.ConversationListManagerFragment;
import com.waz.zclient.pages.main.conversationpager.SlidingPaneLayout;
import com.waz.zclient.pages.main.conversationpager.controller.SlidingPaneObserver;
import com.waz.zclient.pages.main.drawing.DrawingFragment;
import com.waz.zclient.pages.main.giphy.GiphySharingPreviewFragment;
import com.waz.zclient.pages.main.participants.TabbedParticipantBodyFragment;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.pages.main.participants.dialog.ParticipantsDialogFragment;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.camera.CameraFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.ArrayList;


public class RootFragment extends BaseFragment<RootFragment.Container> implements
                                                                       ConversationStoreObserver,
                                                                       SlidingPaneObserver,
                                                                       PendingConnectRequestManagerFragment.Container,
                                                                       ConnectRequestInboxManagerFragment.Container,
                                                                       ParticipantsDialogFragment.Container,
                                                                       PagerControllerObserver,
                                                                       ConversationScreenControllerObserver,
                                                                       OnBackPressedListener,
                                                                       CameraFragment.Container,
                                                                       PickUserControllerScreenObserver,
                                                                       BlankProgressFragment.Container,
                                                                       GiphyObserver,
                                                                       DrawingObserver,
                                                                       LocationObserver,
                                                                       ParticipantsStoreObserver,
                                                                       UsernamesControllerObserver,
                                                                       ConversationFragment.Container,
                                                                       ConversationListManagerFragment.Container,
                                                                       CollectionsObserver {
    public static final String TAG = RootFragment.class.getName();
    private static final Interpolator RIGHT_VIEW_ALPHA_INTERPOLATOR = new Quart.EaseOut();
    private View leftView;
    private View rightView;
    private SlidingPaneLayout slidingPaneLayout;
    private Handler mainHandler = new Handler();
    private View backgroundColorRightView;
    private boolean conversationSwapInProgress = false;
    private boolean panelSliding = false;
    private boolean groupConversation;
    private User otherUser;
    private boolean rightSideShouldBeBlank = false;

    private final ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation model) {
            groupConversation = IConversation.Type.GROUP.equals(model.getType());
            if (groupConversation) {
                otherUser = null;
            } else {
                otherUser = model.getOtherParticipant();
            }
        }
    };

    public static Fragment newInstance() {
        return new RootFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_root, container, false);
        slidingPaneLayout = ViewUtils.getView(view, R.id.spl__root);
        slidingPaneLayout.setCloseOnClick(true);

        // get references
        leftView = ViewUtils.getView(view, R.id.fl__root__left_view);
        rightView = ViewUtils.getView(view, R.id.fl__root__right_view);
        backgroundColorRightView = ViewUtils.getView(view, R.id.fl__root_backgroundcolor_view);

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__root__left_view,
                                          ConversationListManagerFragment.newInstance(),
                                          ConversationListManagerFragment.TAG)
                                     .commit();

            getControllerFactory().getNavigationController().setLeftPage(Page.CONVERSATION_LIST,
                                                                         TAG);
        }
        backgroundColorRightView.setBackgroundColor(Color.TRANSPARENT);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Configuration newConfig = getActivity().getResources().getConfiguration();

        boolean isInLandscape = ViewUtils.isInLandscape(newConfig);
        getControllerFactory().getNavigationController().setIsLandscape(isInLandscape);

        slidingPaneLayout.setSideBarWidth(newConfig);
        if (isInLandscape) {
            slidingPaneLayout.setPreservedOpenState(false);
            // left view adjustments
            leftView.setTranslationX(0);

            // right view adjustments
            SlidingPaneLayout.LayoutParams params = (SlidingPaneLayout.LayoutParams) backgroundColorRightView.getLayoutParams();
            params.leftMargin = getResources().getDimensionPixelSize(R.dimen.framework__sidebar_width);

            slidingPaneLayout.openPane();
        } else {

            // left view adjustments
            leftView.setTranslationX(0);

            // right view adjustments
            SlidingPaneLayout.LayoutParams params = (SlidingPaneLayout.LayoutParams) backgroundColorRightView.getLayoutParams();
            params.leftMargin = 0;

            boolean open = !(savedInstanceState != null) ||
                           getControllerFactory().getPickUserController().isShowingPickUser(IPickUserController.Destination.CONVERSATION_LIST);
            slidingPaneLayout.setPreservedOpenState(open);
            if (open) {
                getControllerFactory().getSlidingPaneController().onPanelOpened(leftView);
            } else {
                getControllerFactory().getSlidingPaneController().onPanelClosed(leftView);
            }
        }

        // Hide start ui on screen rotation
        if (savedInstanceState != null) {
            getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(IPickUserController.Destination.CONVERSATION_LIST);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        slidingPaneLayout.setPanelSlideController(getControllerFactory().getSlidingPaneController());
        // Conversation update can trigger sliding pane movement, add as observer before subscribing to conversation store
        getControllerFactory().getSlidingPaneController().addObserver(this);

        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getNavigationController().addPagerControllerObserver(this);
        if (!getControllerFactory().getConversationScreenController().isConversationStreamUiInitialized()) {
            getStoreFactory().getConversationStore().addConversationStoreObserverAndUpdate(this);
        } else {
            getStoreFactory().getConversationStore().addConversationStoreObserver(this);
        }
        getControllerFactory().getCameraController().addCameraActionObserver(this);
        getControllerFactory().getPickUserController().addPickUserScreenControllerObserver(this);
        getControllerFactory().getGiphyController().addObserver(this);
        getControllerFactory().getUsernameController().addUsernamesObserverAndUpdate(this);
        getControllerFactory().getDrawingController().addDrawingObserver(this);
        getStoreFactory().getParticipantsStore().addParticipantsStoreObserver(this);
        getControllerFactory().getLocationController().addObserver(this);
        onPagerEnabledStateHasChanged(getControllerFactory().getNavigationController().isPagerEnabled());
        getCollectionController().addObserver(this);
    }

    @Override
    public void onStop() {
        slidingPaneLayout.setPanelSlideController(null);
        getControllerFactory().getLocationController().removeObserver(this);
        getStoreFactory().getParticipantsStore().removeParticipantsStoreObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getControllerFactory().getSlidingPaneController().removeObserver(this);
        getControllerFactory().getCameraController().removeCameraActionObserver(this);
        getControllerFactory().getUsernameController().removeUsernamesObserver(this);
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        getControllerFactory().getPickUserController().removePickUserScreenControllerObserver(this);
        getControllerFactory().getGiphyController().removeObserver(this);
        getControllerFactory().getDrawingController().removeDrawingObserver(this);
        getCollectionController().removeObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        backgroundColorRightView = null;
        leftView = null;
        rightView = null;
        slidingPaneLayout = null;
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // So far I only know of the camera view that needs onActivityResult
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__camera);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onCurrentConversationHasChanged(final IConversation fromConversation,
                                                final IConversation toConversation,
                                                final ConversationChangeRequester conversationChangerSender) {
        if (toConversation == null) {
            return;
        }

        conversationModelObserver.setAndUpdate(toConversation);
        getStoreFactory().getParticipantsStore().setCurrentConversation(toConversation);

        if (rightSideShouldBeBlank) {
            return;
        }

        getControllerFactory().getBackgroundController().expand(false);

        final IConversation.Type type = toConversation.getType();
        // This must be posted because onCurrentConversationHasChanged()
        // might still be running and iterating over the observers -
        // while the posted call triggers things to register/unregister
        // from the list of observers, causing ConcurrentModificationException
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Page page;
                Fragment fragment;
                String tag;
                switch (type) {
                    case WAIT_FOR_CONNECTION:
                        fragment = PendingConnectRequestManagerFragment.newInstance(null,
                                                                                    toConversation.getId(),
                                                                                    ConnectRequestLoadMode.LOAD_BY_CONVERSATION_ID,
                                                                                    IConnectStore.UserRequester.CONVERSATION);
                        tag = PendingConnectRequestManagerFragment.TAG;
                        page = Page.PENDING_CONNECT_REQUEST_AS_CONVERSATION;
                        break;
                    case INCOMING_CONNECTION:
                        fragment = ConnectRequestInboxManagerFragment.newInstance(toConversation.getId());
                        tag = ConnectRequestInboxManagerFragment.TAG;
                        page = Page.CONNECT_REQUEST_INBOX;
                        break;
                    case GROUP:
                    case ONE_TO_ONE:
                    default:
                        page = Page.MESSAGE_STREAM;
                        fragment = ConversationFragment.newInstance();
                        tag = ConversationFragment.TAG;
                        break;
                }
                openMessageStream(page, fragment, tag);
            }
        });

        if (ViewUtils.isInPortrait(getActivity()) && conversationChangerSender != ConversationChangeRequester.FIRST_LOAD) {
            slidingPaneLayout.closePane();
            getControllerFactory().getSlidingPaneController().onPanelClosed(leftView);
        }
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public void onConversationListUpdated(ConversationsList conversationsList) {
    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    private void openMessageStream(Page page, Fragment fragment, String tag) {
        getControllerFactory().getNavigationController().setRightPage(page, TAG);


        int animIn = R.anim.fragment_animation_swap_conversation_tablet_in;
        int animOut = R.anim.fragment_animation_swap_conversation_tablet_out;

        if (ViewUtils.isInPortrait(getActivity())) {
            animIn = R.anim.fragment_animation_portrait_swap_conversation_tablet_in;
            animOut = R.anim.fragment_animation_swap_conversation_tablet_out;
        }

        getChildFragmentManager()
            .beginTransaction()
            .setCustomAnimations(animIn, animOut)
            .replace(R.id.fl__root__right_view,
                     fragment,
                     tag)
            .commitAllowingStateLoss();
    }

    private CollectionController getCollectionController() {
        return ((BaseActivity) getActivity()).injectJava(CollectionController.class);
    }

    @Override
    public void openCollection() {
        getChildFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_in_from_bottom_pick_user,
                R.anim.slide_out_to_bottom_pick_user,
                R.anim.slide_in_from_bottom_pick_user,
                R.anim.slide_out_to_bottom_pick_user)
            .replace(R.id.fl__root__right_view,
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
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public void dismissInboxFragment() {
        IConversation nextConversation = getStoreFactory().getConversationStore().getNextConversation();
        if (nextConversation == null) {
            return;
        }
        getStoreFactory().getConversationStore().setCurrentConversation(nextConversation,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    @Override
    public void onAcceptedUser(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    @Override
    public void onPanelSlide(View panel, float slideOffset) {
        getControllerFactory().getNavigationController().onPageScrolled(0, 1 - slideOffset, 0);
        float translationX = (slideOffset - 1) * getResources().getDimensionPixelSize(R.dimen.root__parallax_distance);
        leftView.setTranslationX(translationX);
        if (MathUtils.floatEqual(slideOffset, 0f) || MathUtils.floatEqual(slideOffset, 1f)) {
            panelSliding = false;
        } else if (!panelSliding) {
            panelSliding = true;
            KeyboardUtils.hideKeyboard(getActivity());
        }
    }

    @Override
    public void onPanelOpened(View panel) {
        getControllerFactory().getNavigationController().setPagerPosition(0);
        getControllerFactory().getFocusController().restoreToNextFocus(getActivity(),
                                                                       getControllerFactory().getNavigationController().getPagerPosition(),
                                                                       getControllerFactory().getNavigationController().getCurrentPage(),
                                                                       getControllerFactory().getPickUserController().isShowingPickUser(
                                                                           IPickUserController.Destination.CONVERSATION_LIST));
    }

    @Override
    public void onPanelClosed(View panel) {
        getControllerFactory().getNavigationController().setPagerPosition(1);
        if (!conversationSwapInProgress) {
            rightView.setAlpha(1f);
        }
        getControllerFactory().getFocusController().restoreToNextFocus(getActivity(),
                                                                       getControllerFactory().getNavigationController().getPagerPosition(),
                                                                       getControllerFactory().getNavigationController().getCurrentPage(),
                                                                       getControllerFactory().getPickUserController().isShowingPickUser(
                                                                           IPickUserController.Destination.CONVERSATION_LIST));
    }

    @Override
    public void onPagerEnabledStateHasChanged(boolean enabled) {
        slidingPaneLayout.setIsSlidingEnabled(enabled);
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // CameraFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBitmapSelected(ImageAsset imageAsset, boolean imageFromCamera, CameraContext cameraContext) {
        if (cameraContext != CameraContext.MESSAGE) {
            return;
        }
        getControllerFactory().getCameraController().closeCamera(cameraContext);
        getStoreFactory().getConversationStore().sendMessage(imageAsset);

        // Tablet doesn't have keyboard camera interface
        TrackingUtils.onSentPhotoMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                         getStoreFactory().getConversationStore().getCurrentConversation(),
                                         imageFromCamera ? SentPictureEvent.Source.CAMERA
                                                         : SentPictureEvent.Source.GALLERY,
                                         SentPictureEvent.Method.TABLET);
    }

    @Override
    public void onCameraNotAvailable() {

    }

    @Override
    public void onShowDrawing(ImageAsset image, DrawingController.DrawingDestination drawingDestination, IDrawingController.DrawingMethod method) {
        slidingPaneLayout.setVisibility(View.GONE);
        getControllerFactory().getCameraController().closeCamera(CameraContext.MESSAGE);
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__root__sketch,
                                      DrawingFragment.newInstance(image, drawingDestination, method),
                                      DrawingFragment.TAG)
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.DRAWING, TAG);
    }

    @Override
    public void onHideDrawing(DrawingController.DrawingDestination drawingDestination, boolean imageSent) {
        if (LayoutSpec.isTablet(getActivity())) {
            ViewUtils.unlockOrientation(getActivity());
        }
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__sketch);
        if (fragment != null) {
            getChildFragmentManager().beginTransaction().remove(fragment).commit();
        }
        slidingPaneLayout.setVisibility(View.VISIBLE);
        switch (drawingDestination) {
            case CAMERA_PREVIEW_VIEW:
                if (imageSent) {
                    getControllerFactory().getCameraController().closeCamera(CameraContext.MESSAGE);
                    getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
                } else {
                    if (getChildFragmentManager().findFragmentById(R.id.fl__root__camera) == null) {
                        getControllerFactory().getCameraController().openCamera(CameraContext.MESSAGE);
                    }
                    getControllerFactory().getNavigationController().setRightPage(Page.CAMERA, TAG);
                }
                break;
            case SINGLE_IMAGE_VIEW:
            case SKETCH_BUTTON:
                getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
                break;
        }
    }

    @Override
    public void onSearch(String keyword) {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__root__giphy,
                                      GiphySharingPreviewFragment.newInstance(keyword),
                                      GiphySharingPreviewFragment.TAG)
                                 .commit();
    }

    @Override
    public void onRandomSearch() {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__root__giphy,
                                      GiphySharingPreviewFragment.newInstance(),
                                      GiphySharingPreviewFragment.TAG)
                                 .commit();
    }

    @Override
    public void onTrendingSearch() {

    }

    @Override
    public void onCloseGiphy() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__giphy);
        if (fragment != null) {
            getChildFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    @Override
    public void onCancelGiphy() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__giphy);
        if (fragment != null) {
            getChildFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    @Override
    public void onOpenCamera(CameraContext cameraContext) {
        getChildFragmentManager().beginTransaction().add(R.id.fl__root__camera,
                                                         CameraFragment.newInstance(cameraContext),
                                                         CameraFragment.TAG).commit();
    }

    @Override
    public void onCloseCamera(CameraContext cameraContext) {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__camera);
        if (fragment != null) {
            getChildFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabSingle) {
        if (LayoutSpec.isPhone(getActivity()) || anchorView == null) {
            return;
        }

        final Rect outRect = new Rect();
        anchorView.getDrawingRect(outRect);
        final Point locationOnScreen = ViewUtils.getLocationOnScreen(anchorView);
        final Point rootViewLocation = ViewUtils.getLocationOnScreen(ViewUtils.getView(getActivity(),
                                                                                       R.id.fl_main_content));

        locationOnScreen.offset(-rootViewLocation.x, -rootViewLocation.y);

        final int posX = locationOnScreen.x;
        final int posY = locationOnScreen.y;
        if (anchorView instanceof Toolbar) {
            getControllerFactory().getConversationScreenController().setPopoverLaunchedMode(DialogLaunchMode.CONVERSATION_TOOLBAR);
        } else {
            getControllerFactory().getConversationScreenController().setPopoverLaunchedMode(DialogLaunchMode.PARTICIPANT_BUTTON);
        }
        int firstPage = (isSingleConversation && showDeviceTabSingle) ? TabbedParticipantBodyFragment.DEVICE_PAGE : TabbedParticipantBodyFragment.USER_PAGE;
        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.fl__root__participant_container,
                                         ParticipantsDialogFragment.newParticipantButtonInstance(
                                                 posX,
                                                 posY,
                                                 outRect,
                                                 firstPage),
                                          ParticipantsDialogFragment.TAG)
                                 .commit();
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
        if (!conversationSwapInProgress) {
            return;
        }

        int startDelay = getResources().getInteger(R.integer.framework_animation_delay_short);
        rightView.animate()
                 .alpha(1f)
                 .setInterpolator(RIGHT_VIEW_ALPHA_INTERPOLATOR)
                 .setDuration(getResources().getInteger(R.integer.framework_animation_duration_long))
                 .withEndAction(new Runnable() {
                     @Override
                     public void run() {
                         conversationSwapInProgress = false;
                     }
                 })
                 .start();

        slidingPaneLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (slidingPaneLayout == null) {
                    return;
                }
                slidingPaneLayout.closePane();
            }
        }, startDelay);
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

    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantsDialogFragment.TAG);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__right_view);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__camera);
        if (fragment instanceof CameraFragment) {
            //TODO: https://wearezeta.atlassian.net/browse/AN-2311 Refactor camera into one view
            getControllerFactory().getCameraController().closeCamera(((CameraFragment) fragment).getCameraContext());
            return true;
        } else if (fragment instanceof OnBackPressedListener) {
            ((OnBackPressedListener) fragment).onBackPressed();
            return true;
        }

        fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__giphy);
        if (fragment instanceof GiphySharingPreviewFragment) {
            if (!((GiphySharingPreviewFragment) fragment).onBackPressed()) {
                getControllerFactory().getGiphyController().cancel();
            }
            return true;
        }

        fragment = getChildFragmentManager().findFragmentById(R.id.fl__root__sketch);
        if (fragment instanceof OnBackPressedListener) {
            ((OnBackPressedListener) fragment).onBackPressed();
            return true;
        }

        if (getControllerFactory().getPickUserController().isShowingPickUser(IPickUserController.Destination.CONVERSATION_LIST)) {
            getControllerFactory().getPickUserController().hidePickUser(IPickUserController.Destination.CONVERSATION_LIST, true);
            return true;
        }

        if (slidingPaneLayout != null && !slidingPaneLayout.isOpen()) {
            slidingPaneLayout.openPane();
            return true;
        }

        return false;
    }

    @Override
    public void onShowPickUser(IPickUserController.Destination destination, View anchorView) {
        if (LayoutSpec.isPhone(getActivity()) || anchorView == null || !destination.equals(IPickUserController.Destination.CURSOR)) {
            return;
        }

        final Rect outRect = new Rect();
        anchorView.getDrawingRect(outRect);
        final Point locationOnScreen = ViewUtils.getLocationOnScreen(anchorView);
        final Point rootViewLocation = ViewUtils.getLocationOnScreen(ViewUtils.getView(getActivity(), R.id.fl_main_content));

        locationOnScreen.offset(-rootViewLocation.x, -rootViewLocation.y);

        final int posX = locationOnScreen.x;
        final int posY = locationOnScreen.y;
        if (!groupConversation && otherUser != null) {
            getControllerFactory().getPickUserController().addUser(otherUser);
        }
        getControllerFactory().getConversationScreenController().setPopoverLaunchedMode(DialogLaunchMode.PARTICIPANT_BUTTON);
        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.fl__root__participant_container,
                                          ParticipantsDialogFragment.newStartUiInstance(
                                              posX,
                                              posY,
                                              outRect,
                                              groupConversation),
                                          ParticipantsDialogFragment.TAG)
                                 .commit();
    }

    @Override
    public void onHidePickUser(IPickUserController.Destination destination, boolean closeWithoutSelectingPeople) {
        getChildFragmentManager().popBackStack(ParticipantsDialogFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onShowUserProfile(User user, View anchorView) {
        if (LayoutSpec.isPhone(getActivity())) {
            return;
        }

        final Rect outRect = new Rect();
        anchorView.getDrawingRect(outRect);

        final Point locationOnScreen = ViewUtils.getLocationOnScreen(anchorView);
        final Point rootViewLocation = ViewUtils.getLocationOnScreen(ViewUtils.getView(getActivity(),
                                                                                       R.id.fl_main_content));

        locationOnScreen.offset(-rootViewLocation.x, -rootViewLocation.y);

        final int posX = locationOnScreen.x;
        final int posY = locationOnScreen.y;

        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.fl__root__participant_container,
                                          ParticipantsDialogFragment.newAvatarPopoverInstance(posX,
                                                                                              posY,
                                                                                              outRect,
                                                                                              user.getId()),
                                          ParticipantsDialogFragment.TAG)
                                 .commit();
    }

    @Override
    public void onHideUserProfile() {

    }

    @Override
    public void onAcceptedConnectRequest(IConversation conversation) {

    }

    @Override
    public void onAcceptedPendingOutgoingConnectRequest(IConversation conversation) {

    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void dismissUserProfile() {

    }

    @Override
    public void dismissSingleUserProfile() {

    }

    @Override
    public void showRemoveConfirmation(User user) {

    }

    // ParticipantsStoreObserver

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

    @Override
    public void onShowShareLocation() {
        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.fl__root__camera,
                                          LocationFragment.newInstance(),
                                          LocationFragment.TAG)
                                 .addToBackStack(LocationFragment.TAG)
                                 .commit();
    }

    @Override
    public void onHideShareLocation(MessageContent.Location location) {
        if (location != null) {
            getStoreFactory().getConversationStore().sendMessage(location);
        }
        getChildFragmentManager().popBackStack(LocationFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onValidUsernameGenerated(String name, String generatedUsername) {
        rightSideShouldBeBlank = true;
        openMessageStream(Page.NONE, BlankFragment.newInstance(), BlankFragment.TAG);
    }

    @Override
    public void onUsernameAttemptsExhausted(String name) {
    }

    @Override
    public void onCloseFirstAssignUsernameScreen() {
        rightSideShouldBeBlank = false;
        conversationModelObserver.forceUpdate();
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
