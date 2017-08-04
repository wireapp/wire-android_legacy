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
package com.waz.zclient.pages.main.participants;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.ThemeController;
import com.waz.zclient.controllers.UserAccountsController;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.events.connect.BlockingEvent;
import com.waz.zclient.controllers.tracking.events.conversation.ArchivedConversationEvent;
import com.waz.zclient.controllers.tracking.events.conversation.DeleteConversationEvent;
import com.waz.zclient.controllers.tracking.events.conversation.UnarchivedConversationEvent;
import com.waz.zclient.controllers.tracking.events.group.AddedMemberToGroupEvent;
import com.waz.zclient.controllers.tracking.events.group.CreatedGroupConversationEvent;
import com.waz.zclient.controllers.tracking.events.group.LeaveGroupConversationEvent;
import com.waz.zclient.controllers.tracking.events.group.RemoveContactEvent;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.attributes.ConversationType;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.OnConversationLoadedListener;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.fragments.PickUserFragment;
import com.waz.zclient.media.SoundController;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment;
import com.waz.zclient.pages.main.connect.ConnectRequestLoadMode;
import com.waz.zclient.pages.main.connect.PendingConnectRequestFragment;
import com.waz.zclient.pages.main.connect.SendConnectRequestFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Linear;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.optionsmenu.OptionsMenu;
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.DefaultPageTransitionAnimation;
import com.waz.zclient.views.LoadingIndicatorView;

import java.util.List;

public class ParticipantFragment extends BaseFragment<ParticipantFragment.Container> implements
                                                                                     ParticipantHeaderFragment.Container,
                                                                                     ParticipantBodyFragment.Container,
                                                                                     TabbedParticipantBodyFragment.Container,
                                                                                     ParticipantsStoreObserver,
                                                                                     SingleParticipantFragment.Container,
                                                                                     SendConnectRequestFragment.Container,
                                                                                     BlockedUserProfileFragment.Container,
                                                                                     PendingConnectRequestFragment.Container,
                                                                                     OptionsMenuFragment.Container,
                                                                                     PickUserFragment.Container,
                                                                                     OnConversationLoadedListener,
                                                                                     ConversationScreenControllerObserver,
                                                                                     ConversationStoreObserver,
                                                                                     OnBackPressedListener,
                                                                                     PickUserControllerScreenObserver,
                                                                                     SingleOtrClientFragment.Container {
    public static final String TAG = ParticipantFragment.class.getName();
    private static final String ARG_USER_REQUESTER = "ARG_USER_REQUESTER";
    private static final String ARG__FIRST__PAGE = "ARG__FIRST__PAGE";

    private View bodyContainer;
    private View participantsContainerView;
    private View pickUserContainerView;
    private LoadingIndicatorView loadingIndicatorView;

    private View conversationSettingsOverlayLayout;
    private IConnectStore.UserRequester userRequester;
    private OptionsMenuControl optionsMenuControl;

    private boolean groupConversation;
    private User otherUser;

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

    public static ParticipantFragment newInstance(IConnectStore.UserRequester userRequester, int firstPage) {
        ParticipantFragment participantFragment = new ParticipantFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER_REQUESTER, userRequester);
        args.putInt(ARG__FIRST__PAGE, firstPage);
        participantFragment.setArguments(args);
        return participantFragment;
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim == 0 ||
            getContainer() == null ||
            getControllerFactory().isTornDown() ||
            LayoutSpec.isTablet(getActivity())) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }

        if (enter) {
            return new DefaultPageTransitionAnimation(0,
                                                      getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                                      enter,
                                                      getResources().getInteger(R.integer.framework_animation_duration_medium),
                                                      getResources().getInteger(R.integer.framework_animation_duration_medium),
                                                      1f);
        }
        return new DefaultPageTransitionAnimation(0,
                                                  getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                                  enter,
                                                  getResources().getInteger(R.integer.framework_animation_duration_medium),
                                                  0,
                                                  1f);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        userRequester = (IConnectStore.UserRequester) args.getSerializable(ARG_USER_REQUESTER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_participant, container, false);

        FragmentManager fragmentManager = getChildFragmentManager();
        optionsMenuControl = new OptionsMenuControl();
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                           .replace(R.id.fl__participant__header__container,
                                ParticipantHeaderFragment.newInstance(userRequester),
                                ParticipantHeaderFragment.TAG)
                           .commit();

            IConversation currentConversation = getStoreFactory() != null &&
                                                !getStoreFactory().isTornDown() ?
                                                getStoreFactory().conversationStore().getCurrentConversation() : null;
            if (currentConversation != null &&
                (currentConversation.getType() == IConversation.Type.ONE_TO_ONE ||
                 userRequester == IConnectStore.UserRequester.POPOVER)) {
                fragmentManager.beginTransaction()
                               .replace(R.id.fl__participant__container,
                                    TabbedParticipantBodyFragment.newInstance(getArguments().getInt(ARG__FIRST__PAGE)),
                                    TabbedParticipantBodyFragment.TAG)
                               .commit();
            } else {
                fragmentManager.beginTransaction()
                               .replace(R.id.fl__participant__container,
                                    ParticipantBodyFragment.newInstance(userRequester),
                                    ParticipantBodyFragment.TAG)
                               .commit();
            }

            getChildFragmentManager().beginTransaction()
                                     .replace(R.id.fl__participant__settings_box,
                                          OptionsMenuFragment.newInstance(false),
                                          OptionsMenuFragment.TAG)
                                     .commit();

        }

        Fragment overlayFragment = fragmentManager.findFragmentById(R.id.fl__participant__overlay);
        if (overlayFragment != null) {
            fragmentManager.beginTransaction()
                           .remove(overlayFragment)
                           .commit();
        }


        bodyContainer = ViewUtils.getView(view, R.id.fl__participant__container);
        loadingIndicatorView = ViewUtils.getView(view, R.id.liv__participants__loading_indicator);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            loadingIndicatorView.setColor(getResources().getColor(R.color.people_picker__loading__color));
        } else {
            loadingIndicatorView.setColor(getResources().getColor(R.color.people_picker__loading__color, getContext().getTheme()));
        }

        participantsContainerView = ViewUtils.getView(view, R.id.ll__participant__container);
        pickUserContainerView = ViewUtils.getView(view, R.id.fl__add_to_conversation__pickuser__container);

        conversationSettingsOverlayLayout = ViewUtils.getView(view, R.id.fl__conversation_actions__sliding_overlay);
        conversationSettingsOverlayLayout.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().participantsStore().addParticipantsStoreObserver(this);
        getStoreFactory().conversationStore().addConversationStoreObserver(this);
        if (userRequester == IConnectStore.UserRequester.POPOVER) {
            final User user = getStoreFactory().singleParticipantStore().getUser();
            getStoreFactory().connectStore().loadUser(user.getId(), userRequester);
        } else {
            getStoreFactory().conversationStore().loadCurrentConversation(this);
        }
        if (LayoutSpec.isPhone(getActivity())) {
            // ConversationScreenController is handled in ParticipantDialogFragment for tablets
            getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        }
        getControllerFactory().getPickUserController().addPickUserScreenControllerObserver(this);
    }

    @Override
    public void onStop() {
        getStoreFactory().participantsStore().setCurrentConversation(null);
        getStoreFactory().conversationStore().removeConversationStoreObserver(this);
        if (LayoutSpec.isPhone(getActivity())) {
            getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        }
        getStoreFactory().participantsStore().removeParticipantsStoreObserver(this);
        getControllerFactory().getPickUserController().removePickUserScreenControllerObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (!getControllerFactory().isTornDown()) {
            getControllerFactory()
                .getSingleImageController()
                .clearReferences();
        }

        bodyContainer = null;
        loadingIndicatorView = null;
        participantsContainerView = null;
        pickUserContainerView = null;
        conversationSettingsOverlayLayout = null;

        super.onDestroyView();
    }

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) { }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) { }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangeRequester) {
        if (toConversation == null) {
            return;
        }
        onConversationLoaded(toConversation);

        if (conversationChangeRequester == ConversationChangeRequester.START_CONVERSATION ||
            conversationChangeRequester == ConversationChangeRequester.START_CONVERSATION_FOR_VIDEO_CALL ||
            conversationChangeRequester == ConversationChangeRequester.START_CONVERSATION_FOR_CALL ||
            conversationChangeRequester == ConversationChangeRequester.START_CONVERSATION_FOR_CAMERA) {
            getChildFragmentManager().popBackStackImmediate(PickUserFragment.TAG(),
                                                            FragmentManager.POP_BACK_STACK_INCLUSIVE);
            getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(
                getCurrentPickerDestination());
        }

    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ParticipantsStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void conversationUpdated(IConversation conversation) {
        // Membership might change when user is removed by another user
        getControllerFactory().getConversationScreenController().setMemberOfConversation(conversation.isMemberOfConversation());
    }

    @Override
    public void participantsUpdated(UsersList participants) {
    }

    @Override
    public void otherUserUpdated(User otherUser) {
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // ConversationSettingBox.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onOptionMenuStateHasChanged(OptionsMenu.State state) {

    }

    @Override
    public void onOptionsItemClicked(IConversation conversation, User user, OptionsMenuItem item) {
        switch (item) {
            case ARCHIVE:
                toggleArchiveConversation(conversation, true);
                break;
            case UNARCHIVE:
                toggleArchiveConversation(conversation, false);
                break;
            case SILENCE:
                conversation.setMuted(true);
                break;
            case UNSILENCE:
                conversation.setMuted(false);
                break;
            case LEAVE:
                showLeaveConfirmation(conversation);
                break;
            case RENAME:
                getControllerFactory().getConversationScreenController().editConversationName(true);
                break;
            case DELETE:
                deleteConversation(conversation);
                break;
            case BLOCK:
                showBlockUserConfirmation(user);
                break;
            case UNBLOCK:
                user.unblock();
                break;
        }

        closeMenu();
    }

    private void showBlockUserConfirmation(final User user) {
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                getStoreFactory().conversationStore().setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER);
                getStoreFactory().connectStore().blockUser(user);
                getControllerFactory().getConversationScreenController().hideUser();
                if (LayoutSpec.isTablet(getActivity())) {
                    getControllerFactory().getConversationScreenController().hideParticipants(false, true);
                }

                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new BlockingEvent(BlockingEvent.ConformationResponse.BLOCK));
            }

            @Override
            public void negativeButtonClicked() {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new BlockingEvent(BlockingEvent.ConformationResponse.CANCEL));
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        };

        String header = getString(R.string.confirmation_menu__block_header);
        String text = getString(R.string.confirmation_menu__block_text_with_name, user.getDisplayName());
        String confirm = getString(R.string.confirmation_menu__confirm_block);
        String cancel = getString(R.string.confirmation_menu__cancel);
        OptionsTheme optionsTheme = ((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme();

        ConfirmationRequest request = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(optionsTheme)
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request, IConfirmationController.PARTICIPANTS);

        SoundController ctrl = inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playAlert();
        }
    }

    public void deleteConversation(final IConversation conversation) {
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
                    ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new DeleteConversationEvent(ConversationType.getValue(conversation),
                                                                                                        DeleteConversationEvent.Context.LIST,
                                                                                                        DeleteConversationEvent.Response.CANCEL));
                    return;
                }
                IConversation currentConversation = getStoreFactory().conversationStore().getCurrentConversation();
                boolean deleteCurrentConversation = conversation != null && currentConversation != null &&
                                                    conversation.getId().equals(currentConversation.getId());
                getStoreFactory().conversationStore().deleteConversation(conversation, checkboxIsSelected);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new DeleteConversationEvent(ConversationType.getValue(
                    conversation),
                                                                                                    DeleteConversationEvent.Context.PARTICIPANTS,
                                                                                                    DeleteConversationEvent.Response.DELETE));
                if (deleteCurrentConversation) {
                    getStoreFactory().conversationStore().setCurrentConversationToNext(ConversationChangeRequester.DELETE_CONVERSATION);
                }
                if (LayoutSpec.isTablet(getActivity())) {
                    getControllerFactory().getConversationScreenController().hideParticipants(false, true);
                }
            }
        };
        String header = getString(R.string.confirmation_menu__meta_delete);
        String text = getString(R.string.confirmation_menu__meta_delete_text);
        String confirm = getString(R.string.confirmation_menu__confirm_delete);
        String cancel = getString(R.string.confirmation_menu__cancel);

        ConfirmationRequest.Builder builder = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme());


        if (conversation.getType() == IConversation.Type.GROUP) {
            builder = builder
                .withCheckboxLabel(getString(R.string.confirmation_menu__delete_conversation__checkbox__label))
                .withCheckboxSelectedByDefault();
        }

        getControllerFactory().getConfirmationController().requestConfirmation(builder.build(), IConfirmationController.PARTICIPANTS);

        SoundController ctrl = inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playAlert();
        }
    }

    public void toggleArchiveConversation(final IConversation conversation, final boolean archive) {
        if (getStoreFactory().conversationStore() != null) {
            getControllerFactory().getNavigationController().setVisiblePage(Page.CONVERSATION_LIST, TAG);
            getControllerFactory().getConversationScreenController().hideParticipants(false, true);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getContainer() == null) {
                        return;
                    }
                    getStoreFactory().conversationStore().archive(conversation, archive);
                    if (getControllerFactory() == null ||
                        getControllerFactory().isTornDown()) {
                        return;
                    }
                    if (archive) {
                        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ArchivedConversationEvent(conversation.getType().toString()));
                    } else {
                        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new UnarchivedConversationEvent(conversation.getType().toString()));
                    }

                }
            }, getResources().getInteger(R.integer.framework_animation_duration_medium));
        }
    }


    @Override
    public void onClickedEmptyBackground() {
        if (!getControllerFactory().getConversationScreenController().isSingleConversation()) {
            return;
        }

        if (LayoutSpec.isTablet(getActivity())) {
            final User user = getStoreFactory().singleParticipantStore()
                                               .getUser();

            if (user == null) {
                return;
            }
            getControllerFactory().getSingleImageController().setViewReferences(bodyContainer);
            getControllerFactory().getSingleImageController().showSingleImage(user);
        }
    }

    @Override
    public void toggleBlockUser(User otherUser, boolean block) { }

    @Override
    public void dismissDialog() {
        getContainer().dismissDialog();
    }


    @Override
    public void onConversationUpdated(IConversation conversation) { }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OnBackPressedListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean onBackPressed() {
        PickUserFragment pickUserFragment = (PickUserFragment) getChildFragmentManager().findFragmentByTag(
            PickUserFragment.TAG());
        if (pickUserFragment != null && pickUserFragment.onBackPressed()) {
            return true;
        }

        SingleOtrClientFragment singleOtrClientFragment = (SingleOtrClientFragment) getChildFragmentManager().findFragmentByTag(
            SingleOtrClientFragment.TAG);
        if (singleOtrClientFragment != null) {
            getControllerFactory().getConversationScreenController().hideOtrClient();
            return true;
        }

        SingleParticipantFragment singleUserFragment = (SingleParticipantFragment) getChildFragmentManager().findFragmentByTag(
            SingleParticipantFragment.TAG);
        if (singleUserFragment != null && singleUserFragment.onBackPressed()) {
            return true;
        }

        if (closeMenu()) {
            return true;
        }

        if (getControllerFactory().getPickUserController().isShowingPickUser(getCurrentPickerDestination())) {
            getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), true);
            return true;
        }

        if (getControllerFactory().getConversationScreenController().isShowingUser()) {
            getControllerFactory().getConversationScreenController().hideUser();
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
    // ConversationManagerScreenControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) { }

    @Override
    public void onHideParticipants(boolean backOrButtonPressed, boolean hideByConversationChange, boolean isSingleConversation) { }

    @Override
    public void onShowEditConversationName(boolean show) {
        if (LayoutSpec.isTablet(getActivity())) {
            return;
        }
        if (show) {
            ViewUtils.fadeOutView(bodyContainer);
        } else {
            ViewUtils.fadeInView(bodyContainer);
        }
    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) { }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) { }

    @Override
    public void onConversationLoaded() { }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OnConversationLoadedListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationLoaded(IConversation conversation) {
        getControllerFactory().getConversationScreenController().setSingleConversation(conversation.getType().equals(
            IConversation.Type.ONE_TO_ONE));
        getControllerFactory().getConversationScreenController().setMemberOfConversation(conversation.isMemberOfConversation());
        getStoreFactory().participantsStore().setCurrentConversation(conversation);
        conversationModelObserver.setAndUpdate(conversation);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationScreenControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAddPeopleToConversation() {
        getControllerFactory().getPickUserController().showPickUser(IPickUserController.Destination.PARTICIPANTS, null);
    }


    @Override
    public void onShowConversationMenu(@IConversationScreenController.ConversationMenuRequester int requester,
                                       IConversation conversation,
                                       View anchorView) {
        if (requester != IConversationScreenController.USER_PROFILE_PARTICIPANTS &&
            requester != IConversationScreenController.CONVERSATION_DETAILS) {
            return;
        }

        optionsMenuControl.createMenu(conversation,
                                      requester,
                                      ((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme());
        optionsMenuControl.open();
    }

    @Override
    public void onShowOtrClient(OtrClient otrClient, User user) {
        getChildFragmentManager().beginTransaction()
                                 .setCustomAnimations(R.anim.open_profile,
                                                      R.anim.close_profile,
                                                      R.anim.open_profile,
                                                      R.anim.close_profile)
                                 .add(R.id.fl__participant__overlay,
                                      SingleOtrClientFragment.newInstance(otrClient, user),
                                      SingleOtrClientFragment.TAG)
                                 .addToBackStack(SingleOtrClientFragment.TAG)
                                 .commit();
    }

    @Override
    public void onShowCurrentOtrClient() {
        getChildFragmentManager().beginTransaction()
                                 .setCustomAnimations(R.anim.open_profile,
                                                      R.anim.close_profile,
                                                      R.anim.open_profile,
                                                      R.anim.close_profile)
                                 .add(R.id.fl__participant__overlay,
                                      SingleOtrClientFragment.newInstance(),
                                      SingleOtrClientFragment.TAG)
                                 .addToBackStack(SingleOtrClientFragment.TAG)
                                 .commit();
    }

    @Override
    public void onHideOtrClient() {
        getChildFragmentManager().popBackStackImmediate();
    }

    @Override
    public void onShowLikesList(Message message) {

    }

    @Override
    public OptionsMenuControl getOptionsMenuControl() {
        return optionsMenuControl;
    }

    private boolean closeMenu() {
        return optionsMenuControl.close();
    }

    @Override
    public void onShowUser(final User user) {
        if (user.isMe()) {
            getStoreFactory().singleParticipantStore().setUser(user);
            openUserProfileFragment(SingleParticipantFragment.newInstance(false,
                                                                          IConnectStore.UserRequester.PARTICIPANTS),
                                    SingleParticipantFragment.TAG);
            if (LayoutSpec.isPhone(getActivity())) {
                getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT_USER_PROFILE, TAG);
            }
            return;
        }
        Boolean isTeamSpace = ((BaseActivity) getActivity()).injectJava(UserAccountsController.class).isTeamAccount();
        Boolean isUserTeamMember = ((BaseActivity) getActivity()).injectJava(UserAccountsController.class).isTeamMember(new UserId(user.getId()));

        if (isTeamSpace && isUserTeamMember) {
            showAcceptedUser(user);
        } else {
            switch (user.getConnectionStatus()) {
                case ACCEPTED:
                    showAcceptedUser(user);
                    break;
                case PENDING_FROM_OTHER:
                case PENDING_FROM_USER:
                case IGNORED:
                    openUserProfileFragment(PendingConnectRequestFragment.newInstance(user.getId(),
                        null,
                        ConnectRequestLoadMode.LOAD_BY_USER_ID,
                        IConnectStore.UserRequester.PARTICIPANTS),
                        PendingConnectRequestFragment.TAG);
                    if (LayoutSpec.isPhone(getActivity())) {
                        getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT_USER_PROFILE, TAG);
                    }
                    break;
                case BLOCKED:
                    openUserProfileFragment(BlockedUserProfileFragment.newInstance(user.getId(),
                        IConnectStore.UserRequester.PARTICIPANTS),
                        BlockedUserProfileFragment.TAG);
                    if (LayoutSpec.isPhone(getActivity())) {
                        getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT_USER_PROFILE, TAG);
                    }
                    break;
                case CANCELLED:
                case UNCONNECTED:
                    openUserProfileFragment(SendConnectRequestFragment.newInstance(user.getId(),
                        IConnectStore.UserRequester.PARTICIPANTS),
                        SendConnectRequestFragment.TAG);
                    getControllerFactory().getNavigationController().setRightPage(Page.SEND_CONNECT_REQUEST, TAG);
                    break;
            }
        }
    }

    private void showAcceptedUser(final User user) {
        getStoreFactory().singleParticipantStore().setUser(user);
        openUserProfileFragment(SingleParticipantFragment.newInstance(false,
            IConnectStore.UserRequester.PARTICIPANTS),
            SingleParticipantFragment.TAG);
        if (LayoutSpec.isPhone(getActivity())) {
            getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT_USER_PROFILE, TAG);
        }
    }

    private void openUserProfileFragment(Fragment fragment, String tag) {
        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.AVATAR &&
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.COMMON_USER) {
            animateParticipantsWithConnectUserProfile(false);
            transaction.setCustomAnimations(R.anim.open_profile,
                                            R.anim.close_profile,
                                            R.anim.open_profile,
                                            R.anim.close_profile);
        } else {
            transaction.setCustomAnimations(R.anim.fade_in,
                                            R.anim.fade_out,
                                            R.anim.fade_in,
                                            R.anim.fade_out);
        }
        transaction.add(R.id.fl__participant__overlay, fragment, tag)
                   .addToBackStack(tag)
                   .commit();
    }

    private void animateParticipantsWithConnectUserProfile(boolean show) {
        if (show) {
            if (LayoutSpec.isTablet(getActivity())) {
                participantsContainerView.animate()
                                         .translationX(0)
                                         .setInterpolator(new Expo.EaseOut())
                                         .setDuration(getResources().getInteger(R.integer.framework_animation_duration_long))
                                         .setStartDelay(0)
                                         .start();
            } else {
                participantsContainerView.animate()
                                         .alpha(1)
                                         .scaleY(1)
                                         .scaleX(1)
                                         .setInterpolator(new Expo.EaseOut())
                                         .setDuration(getResources().getInteger(R.integer.reopen_profile_source__animation_duration))
                                         .setStartDelay(getResources().getInteger(R.integer.reopen_profile_source__delay))
                                         .start();
            }

        } else {
            if (LayoutSpec.isTablet(getActivity())) {
                participantsContainerView.animate()
                                         .translationX(-getResources().getDimensionPixelSize(R.dimen.participant_dialog__initial_width))
                                         .setInterpolator(new Expo.EaseOut())
                                         .setDuration(getResources().getInteger(R.integer.framework_animation_duration_long))
                                         .setStartDelay(0)
                                         .start();
            } else {
                participantsContainerView.animate()
                                         .alpha(0)
                                         .scaleY(2)
                                         .scaleX(2)
                                         .setInterpolator(new Expo.EaseIn())
                                         .setDuration(getResources().getInteger(R.integer.reopen_profile_source__animation_duration))
                                         .setStartDelay(0)
                                         .start();
            }
        }
    }

    @Override
    public void onHideUser() {
        if (!getControllerFactory().getConversationScreenController().isShowingUser()) {
            return;
        }
        getChildFragmentManager().popBackStackImmediate();
        if (LayoutSpec.isPhone(getActivity())) {
            Page rightPage = getControllerFactory().getConversationScreenController().isShowingParticipant() ? Page.PARTICIPANT
                                                                                                             : Page.MESSAGE_STREAM;
            getControllerFactory().getNavigationController().setRightPage(rightPage, TAG);
        }

        animateParticipantsWithConnectUserProfile(true);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UserProfileContainer
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void showRemoveConfirmation(final User user) {
        // Show confirmation dialog before removing user
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                dismissUserProfile();
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        getStoreFactory().conversationStore().getCurrentConversation().removeMember(user);
                        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new RemoveContactEvent(true,
                                                                                                       getParticipantsCount()));
                    }
                });
            }

            @Override
            public void negativeButtonClicked() {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new RemoveContactEvent(false,
                                                                                               getParticipantsCount()));
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        };
        String header = getString(R.string.confirmation_menu__header);
        String text = getString(R.string.confirmation_menu_text_with_name, user.getDisplayName());
        String confirm = getString(R.string.confirmation_menu__confirm_remove);
        String cancel = getString(R.string.confirmation_menu__cancel);

        ConfirmationRequest request = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request, IConfirmationController.PARTICIPANTS);

        SoundController ctrl = inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playAlert();
        }
    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    private int getParticipantsCount() {
        return getStoreFactory().conversationStore().getCurrentConversation().getUsers().size();
    }

    @Override
    public void dismissUserProfile() {
            getControllerFactory().getConversationScreenController().hideUser();
    }

    @Override
    public void dismissSingleUserProfile() {
        dismissUserProfile();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PendingConnectRequestFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAcceptedConnectRequest(final IConversation conversation) {
        getControllerFactory().getConversationScreenController().hideUser();
        getStoreFactory().conversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  BlockedUserProfileFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUnblockedUser(IConversation restoredConversationWithUser) {
        getControllerFactory().getConversationScreenController().hideUser();
        getStoreFactory().conversationStore().setCurrentConversation(restoredConversationWithUser,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SendConnectRequestFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectRequestWasSentToUser() {
        getControllerFactory().getConversationScreenController().hideUser();
    }

    @Override
    public void showIncomingPendingConnectRequest(IConversation conversation) { }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSelectedUsers(List<User> users, ConversationChangeRequester requester) {
        IConversation currentConversation = getStoreFactory().conversationStore().getCurrentConversation();
        if (currentConversation.getType() == IConversation.Type.ONE_TO_ONE) {
            getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), false);
            dismissDialog();
            getStoreFactory().conversationStore().createGroupConversation(users, requester);
            if (!getStoreFactory().networkStore().hasInternetConnection()) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.conversation__create_group_conversation__no_network__title,
                                          R.string.conversation__create_group_conversation__no_network__message,
                                          R.string.conversation__create_group_conversation__no_network__button,
                                          null, true);
            }
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new CreatedGroupConversationEvent(true,
                                                                                                      (users.size() + 1)));
        } else if (currentConversation.getType() == IConversation.Type.GROUP) {
            currentConversation.addMembers(users);
            getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), false);
            if (!getStoreFactory().networkStore().hasInternetConnection()) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.conversation__add_user__no_network__title,
                                          R.string.conversation__add_user__no_network__message,
                                          R.string.conversation__add_user__no_network__button,
                                          null, true);
            }
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new AddedMemberToGroupEvent(getParticipantsCount(), users.size()));
        }
    }

    @Override
    public LoadingIndicatorView getLoadingViewIndicator() {
        return loadingIndicatorView;
    }

    @Override
    public IPickUserController.Destination getCurrentPickerDestination() {
        return IPickUserController.Destination.PARTICIPANTS;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowPickUser(IPickUserController.Destination destination, View anchorView) {
        if (LayoutSpec.isPhone(getActivity())) {
            return;
        }
        if (!getCurrentPickerDestination().equals(destination)) {
            onHidePickUser(getCurrentPickerDestination(), true);
            return;
        }
        FragmentManager fragmentManager = getChildFragmentManager();

        int pickUserAnimation =
            LayoutSpec.isTablet(getActivity()) ? R.anim.fade_in : R.anim.slide_in_from_bottom_pick_user;

        IConversation currentConversation = getStoreFactory() != null &&
            !getStoreFactory().isTornDown() ?
            getStoreFactory().conversationStore().getCurrentConversation() : null;
        String conversationId = currentConversation == null ? null : currentConversation.getId();

        if (!groupConversation && otherUser != null) {
            getControllerFactory().getPickUserController().addUser(otherUser);
        }
        fragmentManager
            .beginTransaction()
            .setCustomAnimations(pickUserAnimation, R.anim.fade_out)
            .add(R.id.fl__add_to_conversation__pickuser__container,
                 PickUserFragment.newInstance(true, groupConversation, conversationId),
                 PickUserFragment.TAG())
            .addToBackStack(PickUserFragment.TAG())
            .commit();

        if (LayoutSpec.isPhone(getActivity())) {
            getControllerFactory().getNavigationController().setRightPage(Page.PICK_USER_ADD_TO_CONVERSATION, TAG);
        }

        final ObjectAnimator hideParticipantsAnimator = ObjectAnimator.ofFloat(participantsContainerView,
                                                                               View.ALPHA,
                                                                               1f,
                                                                               0f);
        hideParticipantsAnimator.setInterpolator(new Quart.EaseOut());
        hideParticipantsAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));
        hideParticipantsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                participantsContainerView.setVisibility(View.GONE);
            }
        });
        hideParticipantsAnimator.start();
    }

    @Override
    public void onHidePickUser(IPickUserController.Destination destination,
                               boolean closeWithoutSelectingPeople) {
        if (LayoutSpec.isPhone(getActivity())) {
            return;
        }
        if (!destination.equals(getCurrentPickerDestination())) {
            return;
        }
        // Workaround for animation bug with nested child fragments
        // Animating fragment container views and then popping stack at end of animation

        int showParticipantsDelay = getResources().getInteger(R.integer.framework_animation_delay_long);
        int hidePickUserAnimDuration = getResources().getInteger(R.integer.framework_animation_duration_medium);
        TimeInterpolator hidePickUserInterpolator = new Expo.EaseIn();
        ObjectAnimator hidePickUserAnimator;
        // Fade animation in participants dialog on tablet
        if (LayoutSpec.isTablet(getActivity())) {
            hidePickUserAnimator = ObjectAnimator.ofFloat(pickUserContainerView,
                                                          View.ALPHA,
                                                          1f,
                                                          0f);
            hidePickUserAnimator.setInterpolator(new Linear.EaseIn());

        } else {
            hidePickUserAnimator = ObjectAnimator.ofFloat(pickUserContainerView,
                                                          View.TRANSLATION_Y,
                                                          0f,
                                                          pickUserContainerView.getMeasuredHeight());
            hidePickUserAnimator.setInterpolator(hidePickUserInterpolator);
            hidePickUserAnimator.setDuration(hidePickUserAnimDuration);
        }
        hidePickUserAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isResumed()) {
                    return;
                }
                getChildFragmentManager().popBackStackImmediate(PickUserFragment.TAG(),
                                                                FragmentManager.POP_BACK_STACK_INCLUSIVE);

                if (LayoutSpec.isTablet(getActivity())) {
                    // Reset for fade animation in participants dialog on tablet
                    pickUserContainerView.setAlpha(1f);
                } else {
                    pickUserContainerView.setTranslationY(0f);
                }
            }
        });

        participantsContainerView.setAlpha(0);
        participantsContainerView.setVisibility(View.VISIBLE);
        ObjectAnimator showParticipantsAnimator = ObjectAnimator.ofFloat(participantsContainerView,
                                                                         View.ALPHA,
                                                                         0f,
                                                                         1f);
        showParticipantsAnimator.setInterpolator(new Quart.EaseOut());
        showParticipantsAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));
        showParticipantsAnimator.setStartDelay(showParticipantsDelay);

        AnimatorSet hideSet = new AnimatorSet();
        hideSet.playTogether(hidePickUserAnimator, showParticipantsAnimator);
        hideSet.start();

        if (LayoutSpec.isPhone(getActivity()) &&
            getControllerFactory().getNavigationController().getPagerPosition() == NavigationController.SECOND_PAGE) {
            // TODO: https://wearezeta.atlassian.net/browse/AN-3081
            if (getControllerFactory().getConversationScreenController().isShowingParticipant()) {
                getControllerFactory().getNavigationController().setRightPage(Page.PARTICIPANT, TAG);
            } else {
                getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
            }
        }
    }

    @Override
    public void onShowUserProfile(User user, View anchorView) { }

    @Override
    public void onHideUserProfile() { }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Confirmation overlays
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void showLeaveConfirmation(final IConversation conversation) {
        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new LeaveGroupConversationEvent(true,
                                                                                                        getStoreFactory().conversationStore().getCurrentConversation().getUsers().size()));

                getStoreFactory().conversationStore().leave(conversation);
                getStoreFactory().conversationStore().setCurrentConversationToNext(
                    ConversationChangeRequester.LEAVE_CONVERSATION);
                if (LayoutSpec.isTablet(getActivity())) {
                    getControllerFactory().getConversationScreenController().hideParticipants(false, true);
                }
            }

            @Override
            public void negativeButtonClicked() {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new LeaveGroupConversationEvent(false,
                                                                                                        getStoreFactory().conversationStore().getCurrentConversation().getUsers().size()));
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {

            }
        };
        String header = getString(R.string.confirmation_menu__meta_remove);
        String text = getString(R.string.confirmation_menu__meta_remove_text);
        String confirm = getString(R.string.confirmation_menu__confirm_leave);
        String cancel = getString(R.string.confirmation_menu__cancel);

        ConfirmationRequest request = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request, IConfirmationController.PARTICIPANTS);

        SoundController ctrl = inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playAlert();
        }
    }

    public interface Container {
        void onOpenUrl(String url);

        void dismissDialog();
    }
}
