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
package com.waz.zclient.pages.main.connect;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import com.waz.api.IConversation;
import com.waz.api.User;
import com.waz.model.UserId;
import com.waz.zclient.R;
import com.waz.zclient.common.controllers.UserAccountsController;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.participants.ProfileAnimation;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ContextUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;
import com.waz.zclient.views.menus.FooterMenu;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class SendConnectRequestFragment extends BaseFragment<SendConnectRequestFragment.Container> implements ConnectStoreObserver,
                                                                                                              AccentColorObserver {
    public static final String TAG = SendConnectRequestFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";


    private String userId;
    private IConnectStore.UserRequester userRequester;

    private ZetaButton connectButton;
    private FooterMenu footerMenu;
    private ImageAssetImageView imageAssetImageViewProfile;
    private TypefaceTextView userNameView;
    private TypefaceTextView userUsernameView;

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
            int centerX = ContextUtils.getOrientationIndependentDisplayWidth(getActivity()) / 2;
            int centerY = ContextUtils.getOrientationIndependentDisplayHeight(getActivity()) / 2;
            int duration;
            int delay = 0;

            if (nextAnim != 0) {
                if (enter) {
                    duration = getResources().getInteger(R.integer.open_profile__animation_duration);
                    delay = getResources().getInteger(R.integer.open_profile__delay);
                } else {
                    duration = getResources().getInteger(R.integer.close_profile__animation_duration);
                }

                animation = new ProfileAnimation(enter, duration, delay, centerX, centerY);
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
        connectButton = ViewUtils.getView(rootView, R.id.zb__send_connect_request__connect_button);
        footerMenu = ViewUtils.getView(rootView, R.id.fm__footer);
        imageAssetImageViewProfile = ViewUtils.getView(rootView, R.id.iaiv__send_connect);
        imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);
        imageAssetImageViewProfile.setSaturation(0);
        userNameView = ViewUtils.getView(rootView, R.id.user_name);
        userUsernameView = ViewUtils.getView(rootView, R.id.user_handle);

        View backgroundContainer = ViewUtils.getView(rootView, R.id.background_container);
        backgroundContainer.setClickable(true);
        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS &&
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.AVATAR &&
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() != DialogLaunchMode.COMMON_USER) {
            backgroundContainer.setBackgroundColor(Color.TRANSPARENT);
            userNameView.setPaddingRelative(0, 0, 0, 0);
        } else {
            userNameView.setPaddingRelative(0, ContextUtils.getDimenPx(R.dimen.wire__padding__regular, getContext()), 0, 0);
        }
        connectButton.setText(getResources().getString(R.string.send_connect_request__connect_button__text));

        footerMenu.setVisibility(View.GONE);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        getControllerFactory().getAccentColorController().addAccentColorObserver(this);

        getStoreFactory().connectStore().addConnectRequestObserver(this);
        getStoreFactory().connectStore().loadUser(userId, userRequester);

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

        getStoreFactory().connectStore().removeConnectRequestObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        imageAssetImageViewProfile = null;
        super.onDestroyView();
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

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (user == null ||
                    getStoreFactory() == null ||
                    getStoreFactory().isTornDown()) {
                    return;
                }
                User me = getStoreFactory().profileStore().getSelfUser();
                String myName = me != null ? me.getName() : "";
                String otherName = user.getName();
                String message = getString(R.string.connect__message, otherName, myName);
                IConversation conversation = getStoreFactory().connectStore().connectToNewUser(user, message);
                if (conversation != null) {
                    KeyboardUtils.hideKeyboard(getActivity());
                    getContainer().onConnectRequestWasSentToUser();
                }
            }
        });

        final Boolean permissionToRemove = inject(UserAccountsController.class).hasRemoveConversationMemberPermissionJava(
            inject(ConversationController.class).getCurrentConvId()
        );

        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS && permissionToRemove) {
            footerMenu.setRightActionText(getString(R.string.glyph__minus));
        }

        footerMenu.setCallback(new FooterMenuCallback() {
            @Override
            public void onLeftActionClicked() {
                showConnectButtonInsteadOfFooterMenu();
            }

            @Override
            public void onRightActionClicked() {
                if (userRequester == IConnectStore.UserRequester.PARTICIPANTS && permissionToRemove) {
                    getContainer().showRemoveConfirmation(new UserId(user.getId()));
                }
            }
        });

        userNameView.setText(user.getDisplayName());
        userUsernameView.setText(StringUtils.formatHandle(user.getUsername()));

        if (userRequester == IConnectStore.UserRequester.PARTICIPANTS) {
            footerMenu.setVisibility(View.VISIBLE);
            connectButton.setVisibility(View.GONE);
        } else {
            footerMenu.setVisibility(View.GONE);
            connectButton.setVisibility(View.VISIBLE);
        }
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

        void showRemoveConfirmation(UserId userId);
    }
}
