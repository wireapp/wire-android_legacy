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
package com.waz.zclient.pages.main.connect;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.MessagesList;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.OnConversationLoadedListener;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.participants.ProfileAnimation;
import com.waz.zclient.pages.main.participants.ProfileSourceAnimation;
import com.waz.zclient.pages.main.participants.ProfileTabletAnimation;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.UserDetailsView;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class PendingConnectRequestFragment extends BaseFragment<PendingConnectRequestFragment.Container> implements UserProfile,
                                                                                                                    OnConversationLoadedListener,
                                                                                                                    ConnectStoreObserver,
                                                                                                                    AccentColorObserver,
                                                                                                                    UpdateListener {

    public static final String TAG = PendingConnectRequestFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    public static final String ARGUMENT_LOAD_MODE = "ARGUMENT_LOAD_MODE";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";
    public static final String STATE_IS_SHOWING_FOOTER_MENU = "STATE_IS_SHOWING_FOOTER_MENU";


    private String userId;
    private String conversationId;
    private IConversation conversation;
    private ConnectRequestLoadMode loadMode;
    private IConnectStore.UserRequester userRequester;

    private boolean isShowingFooterMenu;

    private boolean isBelowUserProfile;
    private UserDetailsView userDetailsView;
    private ZetaButton ignoreButton;
    private ZetaButton acceptButton;
    private ZetaButton unblockButton;
    private LinearLayout acceptMenu;
    private FooterMenu footerMenu;
    private Toolbar toolbar;
    private TextView displayNameTextView;
    private ImageAssetImageView imageAssetImageViewProfile;

    public static PendingConnectRequestFragment newInstance(String userId,
                                                            String conversationId,
                                                            ConnectRequestLoadMode loadMode,
                                                            IConnectStore.UserRequester userRequester) {
        PendingConnectRequestFragment newFragment = new PendingConnectRequestFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_USER_ID, userId);
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        args.putString(ARGUMENT_LOAD_MODE, loadMode.toString());
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        Animation animation = super.onCreateAnimation(transit, enter, nextAnim);

        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.AVATAR &&
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.COMMON_USER) {
            // No animation when request is shown in conversation list
            IConnectStore.UserRequester userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
            if (userRequester != IConnectStore.UserRequester.CONVERSATION || isBelowUserProfile) {
                int centerX = ViewUtils.getOrientationIndependentDisplayWidth(getActivity()) / 2;
                int centerY = ViewUtils.getOrientationIndependentDisplayHeight(getActivity()) / 2;
                int duration;
                int delay = 0;
                if (isBelowUserProfile) {
                    if (LayoutSpec.isTablet(getActivity())) {
                        animation = new ProfileTabletAnimation(enter,
                                                               getResources().getInteger(R.integer.framework_animation_duration_long),
                                                               -getResources().getDimensionPixelSize(R.dimen.participant_dialog__initial_width));
                    } else {
                        if (enter) {
                            isBelowUserProfile = false;
                            duration = getResources().getInteger(R.integer.reopen_profile_source__animation_duration);
                            delay = getResources().getInteger(R.integer.reopen_profile_source__delay);
                        } else {
                            duration = getResources().getInteger(R.integer.reopen_profile_source__animation_duration);
                        }
                        animation = new ProfileSourceAnimation(enter, duration, delay, centerX, centerY);
                    }
                } else if (nextAnim != 0) {
                    if (LayoutSpec.isTablet(getActivity())) {
                        animation = new ProfileTabletAnimation(enter,
                                                               getResources().getInteger(R.integer.framework_animation_duration_long),
                                                               getResources().getDimensionPixelSize(R.dimen.participant_dialog__initial_width));
                    } else {
                        if (enter) {
                            duration = getResources().getInteger(R.integer.open_profile__animation_duration);
                            delay = getResources().getInteger(R.integer.open_profile__delay);
                        } else {
                            duration = getResources().getInteger(R.integer.close_profile__animation_duration);
                        }
                        animation = new ProfileAnimation(enter, duration, delay, centerX, centerY);
                    }
                }
            }
        }
        return animation;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateToolbarNavigationIcon(newConfig);
        toolbar.setContentInsetsRelative(getResources().getDimensionPixelSize(R.dimen.content__padding_left),
                                         toolbar.getContentInsetEnd());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup viewContainer, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            userId = savedInstanceState.getString(ARGUMENT_USER_ID);
            conversationId = savedInstanceState.getString(ARGUMENT_CONVERSATION_ID);
            loadMode = ConnectRequestLoadMode.valueOf(savedInstanceState.getString(ARGUMENT_LOAD_MODE));
            userRequester = IConnectStore.UserRequester.valueOf(savedInstanceState.getString(ARGUMENT_USER_REQUESTER));
            isShowingFooterMenu = savedInstanceState.getBoolean(STATE_IS_SHOWING_FOOTER_MENU);
        } else {
            userId = getArguments().getString(ARGUMENT_USER_ID);
            conversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);
            loadMode = ConnectRequestLoadMode.valueOf(getArguments().getString(ARGUMENT_LOAD_MODE));
            userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
            isShowingFooterMenu = false;
        }

        View rootView = inflater.inflate(R.layout.fragment_connect_request_pending, viewContainer, false);

        userDetailsView =  ViewUtils.getView(rootView, R.id.udv__pending_connect__user_details);
        ignoreButton = ViewUtils.getView(rootView, R.id.zb__connect_request__ignore_button);
        acceptButton = ViewUtils.getView(rootView, R.id.zb__connect_request__accept_button);
        unblockButton = ViewUtils.getView(rootView, R.id.zb__connect_request__unblock_button);
        acceptMenu = ViewUtils.getView(rootView, R.id.ll__connect_request__accept_menu);
        footerMenu = ViewUtils.getView(rootView, R.id.fm__footer);
        toolbar = ViewUtils.getView(rootView, R.id.t__pending_connect__toolbar);
        displayNameTextView = ViewUtils.getView(rootView, R.id.tv__pending_connect_toolbar__title);
        imageAssetImageViewProfile = ViewUtils.getView(rootView, R.id.iaiv__pending_connect);
        imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);
        imageAssetImageViewProfile.setSaturation(0);

        updateToolbarNavigationIcon();
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (userRequester) {
                    case CONVERSATION:
                        if (LayoutSpec.isTablet(getContext()) && ViewUtils.isInLandscape(getContext())) {
                            return;
                        }
                        getActivity().onBackPressed();
                        KeyboardUtils.closeKeyboardIfShown(getActivity());
                        break;
                    default:
                        getContainer().dismissUserProfile();
                        break;
                }
            }
        });

        View backgroundContainer = ViewUtils.getView(rootView, R.id.ll__pending_connect__background_container);
        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.AVATAR ||
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.COMMON_USER) {
            backgroundContainer.setClickable(true);
        } else {
            backgroundContainer.setBackgroundColor(Color.TRANSPARENT);
        }

        // Hide views until connection status of user is determined
        footerMenu.setVisibility(View.GONE);
        acceptMenu.setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().getConnectStore().addConnectRequestObserver(this);

        switch (loadMode) {
            case LOAD_BY_CONVERSATION_ID:
                getStoreFactory().getConversationStore().loadConversation(conversationId, this);
                break;
            case LOAD_BY_USER_ID:
                getStoreFactory().getConnectStore().loadUser(userId, userRequester);
                break;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(ARGUMENT_USER_ID, userId);
        outState.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        if (loadMode != null) {
            outState.putString(ARGUMENT_LOAD_MODE, loadMode.toString());
        }
        if (userRequester != null) {
            outState.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        }
        // Save if footer menu was visible -> used to toggle accept & footer menu in incoming connect request opened from group participants
        outState.putBoolean(STATE_IS_SHOWING_FOOTER_MENU, isShowingFooterMenu);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {

        getStoreFactory().getConnectStore().removeConnectRequestObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        userDetailsView.recycle();
        imageAssetImageViewProfile = null;
        ignoreButton = null;
        acceptButton = null;
        unblockButton = null;
        acceptMenu = null;
        footerMenu = null;
        displayNameTextView = null;
        if (conversation != null) {
            conversation.removeUpdateListener(this);
            conversation = null;
        }
        super.onDestroyView();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UserProfile
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void isBelowUserProfile(boolean isBelow) {
        isBelowUserProfile = isBelow;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UI
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void setFooterForOutgoingConnectRequest(final User user) {
        // Hide accept / ignore buttons
        acceptMenu.setVisibility(View.GONE);
        ignoreButton.setOnClickListener(null);
        acceptButton.setOnClickListener(null);

        // Show footer
        footerMenu.setVisibility(View.VISIBLE);
        isShowingFooterMenu = true;

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                user.cancelConnection();
            }

            @Override
            public void onRightActionClicked() {
                user.block();
            }
        });
    }


    private void setFooterForIncomingConnectRequest(final User user) {
        isShowingFooterMenu = userRequester == IConnectStore.UserRequester.PARTICIPANTS;

        // Footer menu
        if (isShowingFooterMenu) {
            footerMenu.setRightActionText(getString(R.string.glyph__minus));

            footerMenu.setCallback(new FooterMenuCallback() {
                @Override
                public void onLeftActionClicked() {
                    // Show Accept menu, hide Footer menu
                    toggleAcceptAndFooterMenu(false);
                }

                @Override
                public void onRightActionClicked() {
                    getContainer().showRemoveConfirmation(user);
                }
            });
        }

        // Accept / ignore buttons
        ignoreButton.setEnabled(true);
        ignoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
                    // Hide Accept menu, show Footer menu
                    toggleAcceptAndFooterMenu(true);
                } else {
                    ignoreButton.setEnabled(false);
                    user.ignoreConnection();
                    getContainer().dismissUserProfile();
                }
                getContainer().onIgnoredConnectRequest(conversation);
            }
        });

        acceptButton.setEnabled(true);
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptButton.setEnabled(false);
                IConversation conversation = user.acceptConnection();
                getContainer().onAcceptedConnectRequest(conversation);
            }
        });

        // Init accept and footer menu visibility
        toggleAcceptAndFooterMenu(isShowingFooterMenu);
    }

    private void toggleAcceptAndFooterMenu(boolean showFooterMenu) {
        if (showFooterMenu) {
            acceptMenu.setVisibility(View.GONE);
            footerMenu.setVisibility(View.VISIBLE);
        } else {
            acceptMenu.setVisibility(View.VISIBLE);
            footerMenu.setVisibility(View.GONE);
        }

        isShowingFooterMenu = showFooterMenu;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationLoaded(IConversation conversation) {

        switch (loadMode) {
            case LOAD_BY_CONVERSATION_ID:
                if (this.conversation != null) {
                    this.conversation.removeUpdateListener(this);
                }
                this.conversation = conversation;
                if (conversation != null) {
                    conversation.addUpdateListener(this);
                }

                getStoreFactory().getConnectStore().loadUser(conversation.getOtherParticipant().getId(),
                                                             userRequester);
                break;
        }

        getStoreFactory().getConnectStore().loadMessages(conversation.getMessages());
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConnectStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessagesUpdated(MessagesList messagesList) {

    }

    @Override
    public void onConnectUserUpdated(final User user, IConnectStore.UserRequester userRequester) {
        if (this.userRequester != userRequester ||
            user == null) {
            return;
        }

        switch (loadMode) {
            case LOAD_BY_USER_ID:
                getStoreFactory().getConversationStore().loadConversation(user.getConversation().getId(),
                                                                          this);
                break;
        }

        imageAssetImageViewProfile.connectImageAsset(user.getPicture());
        userDetailsView.setUser(user);

        // Load common users
        getStoreFactory().getConnectStore().loadCommonConnections(user.getCommonConnections());
        displayNameTextView.setText(user.getName());

        switch (user.getConnectionStatus()) {
            case PENDING_FROM_OTHER:
                setFooterForIncomingConnectRequest(user);
                break;
            case IGNORED:
                setFooterForIncomingConnectRequest(user);
                break;
            case PENDING_FROM_USER:
                setFooterForOutgoingConnectRequest(user);
                break;
        }
    }

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {
        // do nothing
    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        ignoreButton.setIsFilled(false);
        ignoreButton.setAccentColor(color);
        acceptButton.setAccentColor(color);

        unblockButton.setIsFilled(false);
        unblockButton.setAccentColor(color);
    }

    private void updateToolbarNavigationIcon() {
        updateToolbarNavigationIcon(null);
    }

    private void updateToolbarNavigationIcon(Configuration newConfig) {
        if (LayoutSpec.isPhone(getContext())) {
            return;
        }
        if (ViewUtils.isInLandscape(getContext()) ||
            (newConfig != null && ViewUtils.isInLandscape(newConfig))) {
            toolbar.setNavigationIcon(null);
        } else {
            switch (userRequester) {
                case CONVERSATION:
                    if (ThemeUtils.isDarkTheme(getContext())) {
                        toolbar.setNavigationIcon(R.drawable.ic_action_menu_light);
                    } else {
                        toolbar.setNavigationIcon(R.drawable.ic_action_menu_dark);
                    }
                    break;
                default:
                    if (ThemeUtils.isDarkTheme(getContext())) {
                        toolbar.setNavigationIcon(R.drawable.action_back_light);
                    } else {
                        toolbar.setNavigationIcon(R.drawable.action_back_dark);
                    }
                    break;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UpdateListener for Conversation
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void updated() {
        getContainer().onConversationUpdated(conversation);
    }

    public interface Container extends UserProfileContainer {
        void onAcceptedConnectRequest(IConversation conversation);

        void onIgnoredConnectRequest(IConversation conversation);

        void onConversationUpdated(IConversation conversation);
    }
}
