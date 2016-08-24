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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.TextView;
import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.MessagesList;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
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
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class SendConnectRequestFragment extends BaseFragment<SendConnectRequestFragment.Container> implements UserProfile,
                                                                                                              ConnectStoreObserver,
                                                                                                              AccentColorObserver,
                                                                                                              ParticipantBackbarFragment.Container {
    public static final String TAG = SendConnectRequestFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";


    private String userId;
    private IConnectStore.UserRequester userRequester;

    // Flag true if layout has been set
    private boolean isBelowUserProfile;
    private TextView nameTextView;
    private TextView subHeaderView;
    private View closeButton;
    private ZetaButton connectButton;
    private CommonUsersView commonUsersView;
    private int numberOfCommonUsers;
    private FooterMenu footerMenu;
    private ImageAssetImageView imageAssetImageViewProfile;

    public static SendConnectRequestFragment newInstance(String userId, IConnectStore.UserRequester userRequester) {
        SendConnectRequestFragment newFragment = new SendConnectRequestFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_USER_ID, userId);
        args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
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
        return animation;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup viewContainer, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_send_connect_request, viewContainer, false);

        if (savedInstanceState == null) {
            userId = getArguments().getString(ARGUMENT_USER_ID);
            userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
        } else {
            userId = savedInstanceState.getString(ARGUMENT_USER_ID);
            userRequester = IConnectStore.UserRequester.valueOf(savedInstanceState.getString(ARGUMENT_USER_REQUESTER));
        }
        nameTextView = ViewUtils.getView(rootView, R.id.taet__participants__header);
        subHeaderView = ViewUtils.getView(rootView, R.id.ttv__participants__sub_header);
        closeButton = ViewUtils.getView(rootView, R.id.gtv__participants__close);
        connectButton = ViewUtils.getView(rootView, R.id.zb__send_connect_request__connect_button);
        commonUsersView = ViewUtils.getView(rootView, R.id.ll__send_connect_request__common_users);
        footerMenu = ViewUtils.getView(rootView, R.id.fm__footer);
        imageAssetImageViewProfile = ViewUtils.getView(rootView, R.id.iaiv__send_connect);
        imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);
        imageAssetImageViewProfile.setSaturation(0);

        View backgroundContainer = ViewUtils.getView(rootView, R.id.fl__send_connect_request__background_container);
        backgroundContainer.setClickable(true);
        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
            if (LayoutSpec.isTablet(getActivity())) {

                // Don't set to GONE to keep the header text centered and not spanning entire screen due to backbar
                closeButton.setClickable(false);
                closeButton.setVisibility(View.INVISIBLE);
                getChildFragmentManager().beginTransaction()
                                         .add(R.id.fl__participant__backbar__container,
                                              ParticipantBackbarFragment.newInstance(),
                                              ParticipantBackbarFragment.TAG)
                                         .commit();
            } else if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.AVATAR &&
                       getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.COMMON_USER) {
                backgroundContainer.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        subHeaderView.setVisibility(View.GONE);

        connectButton.setText(getResources().getString(R.string.send_connect_request__connect_button__text));

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().dismissUserProfile();
            }
        });

        commonUsersView.setVisibility(View.GONE);
        footerMenu.setVisibility(View.GONE);

        return rootView;
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
        super.onSaveInstanceState(outState);
    }


    @Override
    public void onStop() {
        KeyboardUtils.hideKeyboard(getActivity());

        getStoreFactory().getConnectStore().removeConnectRequestObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        imageAssetImageViewProfile = null;
        super.onDestroyView();
    }

    private void trackSendConnectRequest() {
        getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.CONNECT_REQUESTS_SENT);

        TrackingUtils.tagSentConnectRequestFromUserProfileEvent(getControllerFactory().getTrackingController(),
                                                                userRequester,
                                                                numberOfCommonUsers);
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
    //  IConnectStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectUserUpdated(final User user, final IConnectStore.UserRequester userRequester) {
        if (this.userRequester != userRequester) {
            return;
        }

        imageAssetImageViewProfile.connectImageAsset(user.getPicture());

        nameTextView.setText(user.getName());
        nameTextView.setText(user.getName());

        getStoreFactory().getConnectStore().loadCommonConnections(user.getCommonConnections());

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (user == null ||
                    getStoreFactory() == null ||
                    getStoreFactory().isTornDown()) {
                    return;
                }
                User me = getStoreFactory().getProfileStore().getSelfUser();
                String myName = me != null ? me.getName() : "";
                String otherName = user.getName();
                String message = getString(R.string.connect__message, otherName, myName);
                IConversation conversation = getStoreFactory().getConnectStore().connectToNewUser(user, message);
                trackSendConnectRequest();
                if (conversation != null) {
                    KeyboardUtils.hideKeyboard(getActivity());
                    getContainer().onConnectRequestWasSentToUser();
                }
            }
        });

        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
            footerMenu.setRightActionText(getString(R.string.glyph__minus));
        }

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                showConnectButtonInsteadOfFooterMenu();
            }

            @Override
            public void onRightActionClicked() {
                if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
                    getContainer().showRemoveConfirmation(user);
                }
            }
        });

        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
            footerMenu.setVisibility(View.VISIBLE);
            connectButton.setVisibility(View.GONE);
        } else {
            footerMenu.setVisibility(View.GONE);
            connectButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {
        if (commonConnections.getTotalCount() == 0) {
            commonUsersView.setVisibility(View.GONE);
        } else {
            commonUsersView.setVisibility(View.VISIBLE);
            if (LayoutSpec.isTablet(getActivity())) {
                ViewUtils.setWidth(imageAssetImageViewProfile,
                                   getResources().getDimensionPixelSize(R.dimen.profile__image__width_small));
                ViewUtils.setHeight(imageAssetImageViewProfile,
                                    getResources().getDimensionPixelSize(R.dimen.profile__image__height_small));
            }
        }
        CommonUsersCallback commonUserOnClickCallback = new CommonUsersCallback() {
            @Override
            public void onCommonUserClicked(View anchor, User user) {
                getContainer().openCommonUserProfile(anchor, user);
            }
        };
        numberOfCommonUsers = commonConnections.getTotalCount();
        commonUsersView.setCommonUsers(commonConnections.getTopConnections(),
                                       commonConnections.getTotalCount(),
                                       commonUserOnClickCallback);
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
        connectButton.setAccentColor(color);
    }

    private void showConnectButtonInsteadOfFooterMenu() {
        if (connectButton.getVisibility() == View.VISIBLE) {
            return;
        }
        footerMenu.setVisibility(View.GONE);
        connectButton.setAlpha(0);
        connectButton.setVisibility(View.VISIBLE);
        ViewUtils.fadeInView(connectButton,
                             getResources().getInteger(R.integer.framework_animation_duration_long));
    }

    public interface Container extends UserProfileContainer {
        void onConnectRequestWasSentToUser();

        void showRemoveConfirmation(User user);
    }
}
