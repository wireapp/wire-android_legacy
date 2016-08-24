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

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.MessagesList;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.tracking.events.connect.UnblockingEvent;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.views.CommonUsersCallback;
import com.waz.zclient.pages.main.connect.views.CommonUsersView;
import com.waz.zclient.pages.main.participants.ParticipantBackbarFragment;
import com.waz.zclient.pages.main.participants.ProfileAnimation;
import com.waz.zclient.pages.main.participants.ProfileSourceAnimation;
import com.waz.zclient.pages.main.participants.ProfileTabletAnimation;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.ui.animation.fragment.FadeAnimation;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class BlockedUserProfileFragment extends BaseFragment<BlockedUserProfileFragment.Container> implements UserProfile,
                                                                                                              ConnectStoreObserver,
                                                                                                              AccentColorObserver,
                                                                                                              ParticipantBackbarFragment.Container {
    public static final String TAG = BlockedUserProfileFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";
    public static final String STATE_IS_SHOWING_FOOTER_MENU = "STATE_IS_SHOWING_FOOTER_MENU";

    private String userId;
    private IConnectStore.UserRequester userRequester;

    boolean isShowingFooterMenu;
    private boolean isBelowUserProfile;
    private boolean goToConversationWithUser;
    private CommonUsersView commonUsersView;
    private ZetaButton unblockButton;
    private ZetaButton cancelButton;
    private ZetaButton smallUnblockButton;
    private TextView nameTextView;
    private TextView subHeaderTextView;
    private View unblockMenu;
    private FooterMenu footerMenu;
    private View separatorLine;
    private ImageAssetImageView imageAssetImageViewProfile;

    public static BlockedUserProfileFragment newInstance(String userId, IConnectStore.UserRequester userRequester) {
        BlockedUserProfileFragment newFragment = new BlockedUserProfileFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        args.putString(ARGUMENT_USER_ID, userId);
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        Animation animation = super.onCreateAnimation(transit, enter, nextAnim);

        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.AVATAR &&
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.COMMON_USER) {
            int centerX = ViewUtils.getOrientationIndependentDisplayWidth(getActivity()) / 2;
            int centerY = ViewUtils.getOrientationIndependentDisplayHeight(getActivity()) / 2;
            int duration;
            int delay = 0;

            // Fade out animation when starting conversation directly with this user when unblocking
            if (!goToConversationWithUser || enter) {
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
            } else {
                goToConversationWithUser = false;
                duration = getResources().getInteger(R.integer.framework_animation_duration_medium);
                animation = new FadeAnimation(duration, 1, 0);
            }
        }
        return animation;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup viewContainer, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            userId = savedInstanceState.getString(ARGUMENT_USER_ID);
            userRequester = IConnectStore.UserRequester.valueOf(savedInstanceState.getString(ARGUMENT_USER_REQUESTER));
            isShowingFooterMenu = savedInstanceState.getBoolean(STATE_IS_SHOWING_FOOTER_MENU);
        } else {
            userId = getArguments().getString(ARGUMENT_USER_ID);
            userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
            isShowingFooterMenu = true;
        }

        View view = inflater.inflate(R.layout.fragment_blocked_user_profile, viewContainer, false);
        commonUsersView = ViewUtils.getView(view, R.id.ll__send_connect_request__common_users);
        unblockButton = ViewUtils.getView(view, R.id.zb__connect_request__unblock_button);
        cancelButton = ViewUtils.getView(view, R.id.zb__connect_request__ignore_button);
        smallUnblockButton = ViewUtils.getView(view, R.id.zb__connect_request__accept_button);
        nameTextView = ViewUtils.getView(view, R.id.taet__participants__header);
        subHeaderTextView = ViewUtils.getView(view, R.id.ttv__participants__sub_header);
        footerMenu = ViewUtils.getView(view, R.id.fm__footer);
        unblockMenu = ViewUtils.getView(view, R.id.ll__connect_request__accept_menu);
        separatorLine = ViewUtils.getView(view, R.id.v__connect_request__separator_line);
        imageAssetImageViewProfile = ViewUtils.getView(view, R.id.iaiv__blocked_user);
        imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);

        // Close button
        View closeButton = ViewUtils.getView(view, R.id.gtv__participants__close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().dismissUserProfile();
            }
        });

        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS &&
            LayoutSpec.isTablet(getActivity())) {
            // Don't set to GONE to keep the header text centered and not spanning entire screen due to backbar
            closeButton.setClickable(false);
            closeButton.setVisibility(View.INVISIBLE);
            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__participant__backbar__container,
                                          ParticipantBackbarFragment.newInstance(),
                                          ParticipantBackbarFragment.TAG)
                                     .commit();
        }

        // Split Unblock / Cancel menu when opened from group conversation
        cancelButton.setText(getString(R.string.confirmation_menu__cancel));
        smallUnblockButton.setText(getString(R.string.connect_request__unblock__button__text));

        // Hide some views irrelevant for blocking
        footerMenu.setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);
        separatorLine.setVisibility(View.GONE);
        commonUsersView.setVisibility(View.GONE);
        unblockButton.setVisibility(View.GONE);

        View backgroundContainer = ViewUtils.getView(view, R.id.fl__blocked_user__background_container);
        if ((LayoutSpec.isPhone(getActivity()) && getControllerFactory().getNavigationController().getPagerPosition() == NavigationController.FIRST_PAGE) ||
            (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.AVATAR ||
             getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.COMMON_USER)) {
            backgroundContainer.setClickable(true);
        } else {
            backgroundContainer.setBackgroundColor(Color.TRANSPARENT);
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().getConnectStore().addConnectRequestObserver(this);
        getStoreFactory().getConnectStore().loadUser(userId, userRequester);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(ARGUMENT_USER_ID, userId);
        if (userRequester != null) {
            outState.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        }

        // Save if footer menu was visible -> used to toggle unblock & footer menu when profile was opened from group participants
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
        imageAssetImageViewProfile = null;
        commonUsersView = null;
        unblockButton = null;
        cancelButton = null;
        smallUnblockButton = null;
        nameTextView = null;
        footerMenu = null;
        unblockMenu = null;
        separatorLine = null;
        subHeaderTextView = null;
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
    //  ConnectStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectUserUpdated(final User user, IConnectStore.UserRequester userRequester) {
        if (this.userRequester != userRequester ||
            user == null) {
            return;
        }

        imageAssetImageViewProfile.connectImageAsset(user.getPicture());

        // Load common users
        getStoreFactory().getConnectStore().loadCommonConnections(user.getCommonConnections());
        nameTextView.setText(user.getName());
        subHeaderTextView.setText(user.getEmail());

        setFooterMenu(user);
    }

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {
        if (commonConnections.getTotalCount() == 0) {
            commonUsersView.setVisibility(View.GONE);
            FrameLayout.LayoutParams params = ((FrameLayout.LayoutParams) imageAssetImageViewProfile.getLayoutParams());
            params.topMargin = 0;
            params.gravity = Gravity.CENTER;
            imageAssetImageViewProfile.setLayoutParams(params);
        } else {
            commonUsersView.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams params = ((FrameLayout.LayoutParams) imageAssetImageViewProfile.getLayoutParams());
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.profile__image_top_padding);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            imageAssetImageViewProfile.setLayoutParams(params);
        }
        CommonUsersCallback commonUserOnClickCallback = new CommonUsersCallback() {
            @Override
            public void onCommonUserClicked(View anchor, User user) {
                getContainer().openCommonUserProfile(anchor, user);
            }
        };

        commonUsersView.setCommonUsers(commonConnections.getTopConnections(), commonConnections.getTotalCount(), commonUserOnClickCallback);
    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        unblockButton.setAccentColor(color);

        // Split Unblock / Cancel menu when opened from group conversation
        cancelButton.setIsFilled(false);
        cancelButton.setAccentColor(color);

        smallUnblockButton.setAccentColor(color);
        subHeaderTextView.setTextColor(color);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Footer UI
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void setFooterMenu(final User user) {
        unblockMenu.setVisibility(View.GONE);

        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
            setGroupConversationFooterMenu(user);
        } else {
            setRegularFooterMenu(user);
        }
    }

    private void setRegularFooterMenu(final User user) {
        unblockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unblockUser(user);
            }
        });

        unblockButton.setVisibility(View.VISIBLE);
        footerMenu.setVisibility(View.GONE);
        unblockMenu.setVisibility(View.GONE);

    }

    private void setGroupConversationFooterMenu(final User user) {
        unblockButton.setVisibility(View.GONE);
        toggleUnblockAndFooterMenu(isShowingFooterMenu);

        // Hook up callbacks
        footerMenu.setLeftActionLabelText(getString(R.string.connect_request__footer__blocked_label));
        footerMenu.setRightActionText(getString(R.string.glyph__minus));
        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                toggleUnblockAndFooterMenu(false);
            }

            @Override
            public void onRightActionClicked() {
                getContainer().showRemoveConfirmation(user);
            }
        });

        // Split unblock / cancel buttons
        cancelButton.setEnabled(true);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleUnblockAndFooterMenu(true);
            }
        });

        smallUnblockButton.setEnabled(true);
        smallUnblockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unblockUser(user);
            }
        });
    }


    private void toggleUnblockAndFooterMenu(boolean showFooterMenu) {
        if (showFooterMenu) {
            footerMenu.setVisibility(View.VISIBLE);
            unblockMenu.setVisibility(View.GONE);
        } else {
            footerMenu.setVisibility(View.GONE);
            unblockMenu.setVisibility(View.VISIBLE);
        }

        isShowingFooterMenu = showFooterMenu;
    }

    private void unblockUser(User user) {
        goToConversationWithUser = true;
        getControllerFactory().getTrackingController().tagEvent(new UnblockingEvent());
        IConversation conversation = getStoreFactory().getConnectStore().unblockUser(user);
        if (conversation != null) {
            // Note! important to pass conversation returned by unblockUser() instead of user.getConversation()
            getContainer().onUnblockedUser(conversation);
        }
    }

    public interface Container extends UserProfileContainer {
        void onUnblockedUser(IConversation restoredConversationWithUser);
    }
}
