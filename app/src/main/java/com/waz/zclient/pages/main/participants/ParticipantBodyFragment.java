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

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.MessagesList;
import com.waz.api.NetworkMode;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.controllers.tracking.events.group.LeaveGroupConversationEvent;
import com.waz.zclient.controllers.tracking.events.group.OpenedGroupActionEvent;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.participants.views.ParticipantsChatheadAdapter;
import com.waz.zclient.pages.main.participants.views.ParticipantsGridView;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class ParticipantBodyFragment extends BaseFragment<ParticipantBodyFragment.Container> implements
                                                                                   ConversationScreenControllerObserver,
                                                                                   ParticipantsStoreObserver,
                                                                                   AccentColorObserver,
                                                                                   AdapterView.OnItemClickListener,
                                                                                   ConnectStoreObserver {
    public static final String TAG = ParticipantBodyFragment.class.getName();
    private static final String ARG_USER_REQUESTER = "ARG_USER_REQUESTER";

    private ParticipantsGridView participantsGridView;
    private ParticipantsChatheadAdapter participantsAdapter;
    private FooterMenu footerMenu;
    private View topBorder;
    private LinearLayout footerWrapper;
    private ZetaButton unblockButton;
    private IConnectStore.UserRequester userRequester;
    private ImageAssetImageView imageAssetImageView;
    private int numberOfColumns;

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        final Fragment parent = getParentFragment();

        // Apply the workaround only if this is a child fragment, and the parent
        // is being removed.
        if (!enter && parent != null && parent.isRemoving()) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            Animation doNothingAnim = new AlphaAnimation(1, 1);
            doNothingAnim.setDuration(ViewUtils.getNextAnimationDuration(parent));
            return doNothingAnim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    public static ParticipantBodyFragment newInstance(IConnectStore.UserRequester userRequester) {
        ParticipantBodyFragment participantBodyFragment = new ParticipantBodyFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER_REQUESTER, userRequester);
        participantBodyFragment.setArguments(args);
        return participantBodyFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        userRequester = (IConnectStore.UserRequester) args.getSerializable(ARG_USER_REQUESTER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_participant, viewGroup, false);

        footerMenu = ViewUtils.getView(view, R.id.fm__participants__footer);
        topBorder = ViewUtils.getView(view, R.id.v_participants__footer__top_border);
        footerWrapper = ViewUtils.getView(view, R.id.ll__participants__footer_wrapper);
        unblockButton = ViewUtils.getView(view, R.id.zb__single_user_participants__unblock_button);
        imageAssetImageView = ViewUtils.getView(view, R.id.iaiv__participant_body);
        imageAssetImageView.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);

        participantsAdapter = new ParticipantsChatheadAdapter();
        participantsGridView = ViewUtils.getView(view, R.id.pgv__participants);
        participantsGridView.setAdapter(participantsAdapter);
        participantsGridView.setOnItemClickListener(this);
        participantsGridView.setSelector(getResources().getDrawable(R.drawable.transparent));
        participantsGridView.setOnScrollListener(participantsGridOnScrollListener);
        numberOfColumns = getResources().getInteger(R.integer.participant_column__count);
        participantsGridView.setNumColumns(numberOfColumns);

        // Hide footer until conversation is loaded
        footerMenu.setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);

        // Toggle color background
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getContainer().onClickedEmptyBackground();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (userRequester == IConnectStore.UserRequester.POPOVER) {
            getStoreFactory().getConnectStore().addConnectRequestObserver(this);
            final User user = getStoreFactory().getSingleParticipantStore().getUser();
            getStoreFactory().getConnectStore().loadUser(user.getId(), userRequester);
        } else {
            getStoreFactory().getParticipantsStore().addParticipantsStoreObserver(this);
        }
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
    }

    @Override
    public void onStop() {
        participantsAdapter.tearDown();
        getStoreFactory().getConnectStore().removeConnectRequestObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getStoreFactory().getParticipantsStore().removeParticipantsStoreObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        imageAssetImageView = null;
        participantsGridView = null;
        participantsAdapter = null;
        footerMenu = null;
        topBorder = null;
        footerWrapper = null;
        participantsGridView = null;
        super.onDestroyView();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        User user = participantsAdapter.getItem(position);
        getControllerFactory().getConversationScreenController().showUser(user);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Conversation Manager Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {
        if (participantsGridView == null) {
            return;
        }
        if (isSingleConversation) {
            // Enable toggling of colour background for 1:1 conversations
            participantsGridView.setCallback(new ParticipantsGridView.Callback() {
                @Override
                public void onClicked() {
                    getContainer().onClickedEmptyBackground();
                }
            });
            return;
        }
        participantsGridView.setCallback(null);
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
        if (topBorder == null) {
            return;
        }
        // Toggle footer border
        if (scrolledToBottom) {
            topBorder.setVisibility(View.INVISIBLE);
        } else {
            topBorder.setVisibility(View.VISIBLE);
        }
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
    public void onShowCommonUser(User user) {

    }

    @Override
    public void onHideCommonUser() {

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
    public void conversationUpdated(final IConversation conversation) {
        footerMenu.setVisibility(View.VISIBLE);

        if (conversation.getType() == IConversation.Type.ONE_TO_ONE) {
            footerMenu.setLeftActionText(getString(R.string.glyph__plus));
            topBorder.setVisibility(View.INVISIBLE);
            footerMenu.setRightActionText(getString(R.string.glyph__more));
            getStoreFactory()
                .getSingleParticipantStore()
                .setUser(conversation.getOtherParticipant());
        } else {
            imageAssetImageView.setVisibility(View.GONE);

            // Check if self user is member for group conversation
            if (conversation.isMemberOfConversation()) {
                footerMenu.setLeftActionText(getString(R.string.glyph__add_people));
                footerMenu.setRightActionText(getString(R.string.glyph__more));
                footerMenu.setLeftActionLabelText(getString(R.string.conversation__action__add_people));
            } else {
                footerMenu.setLeftActionText("");
                footerMenu.setRightActionText("");
                footerMenu.setLeftActionLabelText("");
            }
            if (lastParticipantAboveFooter()) {
                topBorder.setVisibility(View.INVISIBLE);
            } else {
                topBorder.setVisibility(View.VISIBLE);
            }
        }

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                if (userRequester == IConnectStore.UserRequester.POPOVER) {
                    final User user = getStoreFactory().getSingleParticipantStore().getUser();
                    if (user.isMe()) {
                        getControllerFactory().getConversationScreenController().hideParticipants(true, false);

                        // Go to conversation with this user
                        getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(getContainer().getCurrentPickerDestination());
                        getStoreFactory().getConversationStore().setCurrentConversation(user.getConversation(),
                                                                                        ConversationChangeRequester.START_CONVERSATION);
                        return;
                    }
                }
                if (!conversation.isMemberOfConversation()) {
                    return;
                }
                getControllerFactory().getTrackingController().tagEvent(new OpenedGroupActionEvent());
                getControllerFactory().getConversationScreenController().addPeopleToConversation();
            }

            @Override
            public void onRightActionClicked() {
                getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                    @Override
                    public void execute(NetworkMode networkMode) {
                        if (!conversation.isMemberOfConversation()) {
                            return;
                        }
                        if (userRequester == IConnectStore.UserRequester.POPOVER) {
                            User otherUser = conversation.getOtherParticipant();
                            getContainer().toggleBlockUser(otherUser,
                                                           otherUser.getConnectionStatus() != User.ConnectionStatus.BLOCKED);
                        } else {
                            getControllerFactory().getConversationScreenController().showConversationMenu(
                                IConversationScreenController.CONVERSATION_DETAILS,
                                conversation,
                                null);
                        }
                    }

                    @Override
                    public void onNoNetwork() {
                        ViewUtils.showAlertDialog(getActivity(),
                                                  R.string.alert_dialog__no_network__header,
                                                  R.string.leave_conversation_failed__message,
                                                  R.string.alert_dialog__confirmation,
                                                  null, true);
                    }
                });

            }
        });
    }

    @Override
    public void participantsUpdated(final UsersList participants) {
        participantsAdapter.setUsersList(participants, numberOfColumns);

        // Toggle footer border depending on if overlapping with participants
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (topBorder == null) {
                    return;
                }
                if (lastParticipantAboveFooter()) {
                    topBorder.setVisibility(View.INVISIBLE);
                } else {
                    topBorder.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void otherUserUpdated(final User otherUser) {
        if (otherUser == null ||
            getView() == null) {
            return;
        }

        participantsAdapter.setUsersList(null, 1);
        imageAssetImageView.setVisibility(View.VISIBLE);
        imageAssetImageView.connectImageAsset(otherUser.getPicture());

        switch (otherUser.getConnectionStatus()) {
            case BLOCKED:
                footerMenu.setVisibility(View.GONE);
                unblockButton.setVisibility(View.VISIBLE);
                unblockButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        otherUser.unblock();
                    }
                });
                break;
            default:
                unblockButton.setVisibility(View.GONE);
                unblockButton.setOnClickListener(null);
                footerMenu.setVisibility(View.VISIBLE);
                break;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Event listeners
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private AbsListView.OnScrollListener participantsGridOnScrollListener = new AbsListView.OnScrollListener() {

        int currentScrollState = SCROLL_STATE_IDLE;

        @Override
        public void onScrollStateChanged(AbsListView absListView, int i) {
            currentScrollState = i;
        }

        @Override
        public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            if (currentScrollState != SCROLL_STATE_IDLE) {

                boolean scrolledToBottom = false;

                if (firstVisibleItem + visibleItemCount == totalItemCount) {
                    scrolledToBottom = lastParticipantAboveFooter();
                }

                getControllerFactory().getConversationScreenController().onScrollParticipantsList(participantsGridView.computeVerticalScrollOffset(),
                                                                                                  scrolledToBottom);
            }
        }
    };

    private boolean lastParticipantAboveFooter() {
        if (participantsGridView == null) {
            return false;
        }

        if (participantsGridView.getLastVisiblePosition() < participantsGridView.getCount() - 1) {
            return false;
        }

        final int lastViewIndex = participantsGridView.getLastVisiblePosition() - participantsGridView.getFirstVisiblePosition();
        if (lastViewIndex == -1) {
            return true;
        }

        int lastItemBottom = participantsGridView.getChildAt(lastViewIndex).getBottom();
        int footerTop = footerWrapper.getTop();

        return lastItemBottom <= footerTop;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        unblockButton.setAccentColor(color);
    }

    @Override
    public void onConnectUserUpdated(final User user, IConnectStore.UserRequester usertype) {
        if (usertype != userRequester ||
            user == null) {
            return;
        }
        imageAssetImageView.setVisibility(View.VISIBLE);
        imageAssetImageView.connectImageAsset(user.getPicture());

        footerMenu.setVisibility(View.VISIBLE);
        topBorder.setVisibility(View.INVISIBLE);

        final IConversation conversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (conversation.getType() == IConversation.Type.ONE_TO_ONE) {
            if (user.isMe()) {
                footerMenu.setLeftActionText(getString(R.string.glyph__profile));
                footerMenu.setLeftActionLabelText(getString(R.string.popover__action__profile));

                footerMenu.setRightActionText("");
                footerMenu.setRightActionLabelText("");
            } else {
                footerMenu.setLeftActionText(getString(R.string.glyph__add_people));
                footerMenu.setLeftActionLabelText(getString(R.string.conversation__action__create_group));

                footerMenu.setRightActionText(getString(R.string.glyph__block));
                footerMenu.setRightActionLabelText(getString(R.string.popover__action__block));
            }
        } else {
            if (user.isMe()) {
                footerMenu.setLeftActionText(getString(R.string.glyph__profile));
                footerMenu.setLeftActionLabelText(getString(R.string.popover__action__profile));

                footerMenu.setRightActionText(getString(R.string.glyph__minus));
                footerMenu.setRightActionLabelText("");
            } else {
                footerMenu.setLeftActionText(getString(R.string.glyph__conversation));
                footerMenu.setLeftActionLabelText(getString(R.string.popover__action__open));

                footerMenu.setRightActionText(getString(R.string.glyph__minus));
                footerMenu.setRightActionLabelText(getString(R.string.popover__action__remove));
            }
        }

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                if (user.isMe() || conversation.getType() != IConversation.Type.ONE_TO_ONE) {
                    getControllerFactory().getConversationScreenController().hideParticipants(true, false);

                    // Go to conversation with this user
                    getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(getContainer().getCurrentPickerDestination());
                    getStoreFactory().getConversationStore().setCurrentConversation(user.getConversation(),
                                                                                    ConversationChangeRequester.START_CONVERSATION);
                } else {
                    getControllerFactory().getTrackingController().tagEvent(new OpenedGroupActionEvent());
                    getControllerFactory().getConversationScreenController().addPeopleToConversation();
                }
            }

            @Override
            public void onRightActionClicked() {
                if (conversation.getType() == IConversation.Type.ONE_TO_ONE) {
                    if (!user.isMe()) {
                        getContainer().toggleBlockUser(user,
                                                       user.getConnectionStatus() != User.ConnectionStatus.BLOCKED);
                    }
                } else {
                    getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                        @Override
                        public void execute(NetworkMode networkMode) {
                            if (user.isMe()) {
                                showLeaveConfirmation(getStoreFactory().getConversationStore().getCurrentConversation());
                            } else {
                                getContainer().showRemoveConfirmation(user);
                            }
                        }

                        @Override
                        public void onNoNetwork() {
                            if (user.isMe()) {
                                ViewUtils.showAlertDialog(getActivity(),
                                                          R.string.alert_dialog__no_network__header,
                                                          R.string.leave_conversation_failed__message,
                                                          R.string.alert_dialog__confirmation,
                                                          null, true);
                            } else {
                                ViewUtils.showAlertDialog(getActivity(),
                                                          R.string.alert_dialog__no_network__header,
                                                          R.string.remove_from_conversation__no_network__message,
                                                          R.string.alert_dialog__confirmation,
                                                          null, true);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {

    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    private void showLeaveConfirmation(final IConversation conversation) {
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
                if (LayoutSpec.isTablet(getActivity())) {
                    getControllerFactory().getConversationScreenController().hideParticipants(false, true);
                }
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
        String checkboxLabel = getString(R.string.confirmation_menu__delete_conversation__checkbox__label);

        ConfirmationRequest request = new ConfirmationRequest.Builder(IConfirmationController.LEAVE_CONVERSATION)
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withCheckboxLabel(checkboxLabel)
            .withWireTheme(getControllerFactory().getThemeController().getThemeDependentOptionsTheme())
            .withCheckboxSelectedByDefault()
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request, IConfirmationController.PARTICIPANTS);

        getStoreFactory().getMediaStore().playSound(R.raw.alert);
        getControllerFactory().getVibratorController().vibrate(R.array.alert);
    }

    public interface Container {

        void onClickedEmptyBackground();

        void toggleBlockUser(User otherUser, boolean block);

        void showRemoveConfirmation(User user);

        IPickUserController.Destination getCurrentPickerDestination();
    }

}
