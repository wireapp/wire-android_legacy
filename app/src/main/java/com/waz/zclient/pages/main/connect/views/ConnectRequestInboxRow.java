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
package com.waz.zclient.pages.main.connect.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.ContactDetails;
import com.waz.api.IConversation;
import com.waz.api.User;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.connect.ConnectActionsCallback;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.views.images.ImageAssetImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConnectRequestInboxRow extends FrameLayout {
    public static final String TAG = ConnectRequestInboxRow.class.getName();

    // Model this view is bound to
    private User user;
    private ConnectActionsCallback connectActionCallback;
    private CommonUsersCallback commonUsersCallback;
    private ZetaButton ignoreButton;
    private ZetaButton acceptButton;
    private TextView displayNameTextView;
    private TextView usernameTextView;
    private TextView userInfoTextView;
    private LinearLayout acceptMenu;
    private ValueAnimator rowHeightAnimator;
    private ImageAssetImageView imageAssetImageViewProfile;

    private final ModelObserver<ContactDetails> contactDetailsModelObserver = new ModelObserver<ContactDetails>() {
        @Override
        public void updated(ContactDetails contactDetails) {
            if (user == null) {
                return;
            }
            String userInfo;
            if (user.getDisplayName().equals(contactDetails.getDisplayName())) {
                // User's Wire name is same as in address book
                userInfo = getContext().getString(R.string.content__message__connect_request__user_info, "");
            } else {
                userInfo = getContext().getResources().getString(R.string.content__message__connect_request__user_info,
                                                                      StringUtils.formatUsername(contactDetails.getDisplayName()));
            }
            userInfoTextView.setText(userInfo);
        }
    };

    private final ModelObserver<User> userModelObserver = new ModelObserver<User>() {
        @Override
        public void updated(User user) {
            if (imageAssetImageViewProfile != null) {
                imageAssetImageViewProfile.connectImageAsset(user.getPicture());
            }
            displayNameTextView.setText(getContext().getString(R.string.connect_request__inbox__header, user.getName()));
            TextViewUtils.boldText(displayNameTextView);

            if (!user.isContact()) {
                userInfoTextView.setText("");
                contactDetailsModelObserver.pauseListening();
            } else {
                contactDetailsModelObserver.setAndUpdate(user.getFirstContact());
            }

            // Toggle accept / ignore buttons
            if (user.getConnectionStatus() == User.ConnectionStatus.PENDING_FROM_OTHER ||
                user.getConnectionStatus() == User.ConnectionStatus.IGNORED) {
                usernameTextView.setText(StringUtils.formatUsername(user.getUsername()));
                acceptMenu.setVisibility(View.VISIBLE);

                ignoreButton.setEnabled(true);
                ignoreButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ignoreButton.setEnabled(false);
                        animateIgnore(ConnectRequestInboxRow.this.user);
                    }
                });

                acceptButton.setEnabled(true);
                acceptButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        acceptButton.setEnabled(false);
                        animateAccept(ConnectRequestInboxRow.this.user);
                    }
                });
            } else {
                acceptMenu.setVisibility(View.GONE);
                ignoreButton.setOnClickListener(null);
                acceptButton.setOnClickListener(null);
            }
        }
    };

    public ConnectRequestInboxRow(Context context) {
        this(context, null);
    }

    public ConnectRequestInboxRow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConnectRequestInboxRow(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        View container = LayoutInflater.from(getContext()).inflate(R.layout.fragment_connect_request_pending_inbox, this, true);
        ignoreButton = ViewUtils.getView(container, R.id.zb__connect_request__ignore_button);
        acceptButton = ViewUtils.getView(container, R.id.zb__connect_request__accept_button);
        displayNameTextView = ViewUtils.getView(container, R.id.taet__participants__header);
        usernameTextView = ViewUtils.getView(container, R.id.ttv__participants__user_name);
        userInfoTextView = ViewUtils.getView(container, R.id.ttv__participants__user_info);
        acceptMenu = ViewUtils.getView(container, R.id.ll__connect_request__accept_menu);

        if (LayoutSpec.isTablet(getContext())) {
            View mainContainerView = ViewUtils.getView(container,
                                                       R.id.ll__connect_request__main_container);
            ViewUtils.setWidth(mainContainerView,
                               getResources().getDimensionPixelSize(R.dimen.connect_request__inbox__max_width));
        }

        imageAssetImageViewProfile = ViewUtils.getView(container, R.id.iaiv__pending_connect);
        if (imageAssetImageViewProfile != null) {
            imageAssetImageViewProfile.setDisplayType(ImageAssetImageView.DisplayType.CIRCLE);
        }

        // Hide close button
        View closeButton = ViewUtils.getView(container, R.id.gtv__participants__close);
        if (closeButton != null) {
            closeButton.setVisibility(GONE);
        }
        // Hide dummy view
        View dummyView = ViewUtils.getView(container, R.id.v__participants_header__dummy_view);
        if (dummyView != null) {
            dummyView.setVisibility(GONE);
        }

        // Hide accept menu, check later when user loaded
        acceptMenu.setVisibility(View.GONE);
    }

    public void setConnectActionCallback(ConnectActionsCallback callback) {
        connectActionCallback = callback;
    }

    public void setCommonUsersCallback(CommonUsersCallback callback) {
        commonUsersCallback = callback;
    }

    public void setAccentColor(int accentColor) {
        // Accept / ignore buttons
        ignoreButton.setIsFilled(false);
        ignoreButton.setAccentColor(accentColor);
        ignoreButton.setTextColor(accentColor);
        acceptButton.setAccentColor(accentColor);
    }

    public void loadUser(User user) {

        this.user = user;
        userModelObserver.setAndUpdate(user);
        for (View v : Arrays.asList(ignoreButton,
                                    displayNameTextView, acceptButton, usernameTextView, acceptMenu)) {
            if (v == null) {
                continue;
            }
            v.setAlpha(1f);
            v.setTranslationX(0f);
        }
        if (LayoutSpec.isTablet(getContext())) {
            if (rowHeightAnimator != null) {
                rowHeightAnimator.cancel();
                rowHeightAnimator = null;
            }
            ViewGroup.LayoutParams params = getLayoutParams();
            if (params != null) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                setLayoutParams(params);
            }
        }
    }

    private void animateAccept(final User user) {
        final int animationDuration = getResources().getInteger(R.integer.framework_animation_duration_medium);
        final List<Animator> animatorList = new ArrayList<>();

        final int translationDestination = -displayNameTextView.getWidth();
        for (View v : Arrays.asList(displayNameTextView)) {
            if (v == null) {
                continue;
            }
            ObjectAnimator animator = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, translationDestination);
            animator.setInterpolator(new Expo.EaseIn());
            animatorList.add(animator);
        }

        for (View v : Arrays.asList(ignoreButton, acceptButton, usernameTextView, acceptMenu)) {
            if (v == null) {
                continue;
            }
            ObjectAnimator animator = ObjectAnimator.ofFloat(v, View.ALPHA, 0f);
            animator.setInterpolator(new Quart.EaseOut());
            animatorList.add(animator);
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scaleOutIfNecessary(new Runnable() {
                    @Override
                    public void run() {
                        final IConversation conversation = user.acceptConnection();
                        if (connectActionCallback != null) {
                            connectActionCallback.onAccepted(conversation);
                        }
                    }
                });
            }
        });
        animatorSet.setDuration(animationDuration);
        animatorSet.playTogether(animatorList);
        animatorSet.start();
    }

    private void scaleOutIfNecessary(final Runnable callback) {
        if (LayoutSpec.isPhone(getContext())) {
            post(callback);
        } else {
            rowHeightAnimator = ValueAnimator.ofInt(getHeight(), 1);
            rowHeightAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));
            rowHeightAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(final ValueAnimator valueAnimator) {
                    ViewGroup.LayoutParams layoutParams = getLayoutParams();
                    layoutParams.height = (Integer) valueAnimator.getAnimatedValue();
                    setLayoutParams(layoutParams);
                }
            });
            rowHeightAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    rowHeightAnimator = null;
                    post(callback);
                }
            });
            rowHeightAnimator.start();
        }
    }

    private void animateIgnore(final User user) {
        final int animationDuration = getResources().getInteger(R.integer.framework_animation_duration_medium);
        final List<Animator> animatorList = new ArrayList<>();

        for (View v : Arrays.asList(ignoreButton,
                                    displayNameTextView, acceptButton, usernameTextView, acceptMenu)) {
            if (v == null) {
                continue;
            }
            ObjectAnimator animator = ObjectAnimator.ofFloat(v, View.ALPHA, 0f);
            animator.setInterpolator(new Quart.EaseOut());
            animatorList.add(animator);
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scaleOutIfNecessary(new Runnable() {
                    @Override
                    public void run() {
                        if (connectActionCallback != null) {
                            user.ignoreConnection();
                            connectActionCallback.onIgnored(user);
                        }
                    }
                });
            }
        });
        animatorSet.setDuration(animationDuration);
        animatorSet.playTogether(animatorList);
        animatorSet.start();

    }
}
