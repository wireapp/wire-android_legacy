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
package com.waz.zclient.newreg.fragments;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.waz.api.AccentColor;
import com.waz.api.ImageAsset;
import com.waz.api.Self;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.events.onboarding.OpenedUsernameFAQEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.BitmapUtils;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.images.ImageAssetImageView;

public class FirstTimeAssignUsernameFragment extends BaseFragment<FirstTimeAssignUsernameFragment.Container> implements OnBackPressedListener {

    public static final String TAG = FirstTimeAssignUsernameFragment.class.getName();
    private static final String ARG_SUGGESTED_USERNAME = "ARG_SUGGESTED_USERNAME";
    private static final String ARG_NAME = "ARG_NAME";

    TypefaceTextView nameTextView;
    TypefaceTextView usernameTextView;
    ImageAssetImageView backgroundImageView;
    Self self;
    String suggestedUsername = "";

    ModelObserver<Self> selfModelObserver = new ModelObserver<Self>() {
        @Override
        public void updated(Self model) {
            ImageAsset imageAsset = model.getPicture();
            backgroundImageView.connectImageAsset(imageAsset);
            self = model;
            if (self.hasSetUsername()) {
                suggestedUsername = self.getUsername();
                usernameTextView.setText(StringUtils.formatHandle(self.getUsername()));
            }
        }
    };

    public static Fragment newInstance(String name, String suggestedUsername) {
        Fragment fragment = new FirstTimeAssignUsernameFragment();
        final Bundle arg = new Bundle();
        arg.putString(ARG_NAME, name);
        arg.putString(ARG_SUGGESTED_USERNAME, suggestedUsername);
        fragment.setArguments(arg);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_username_first_launch, container, false);
        nameTextView = ViewUtils.getView(view, R.id.ttv__name);
        usernameTextView = ViewUtils.getView(view, R.id.ttv__username);
        backgroundImageView = ViewUtils.getView(view, R.id.iaiv__user_photo);
        final ImageView vignetteOverlay = ViewUtils.getView(view, R.id.iv_background_vignette_overlay);
        ZetaButton chooseYourOwnButton = ViewUtils.getView(view, R.id.zb__username_first_assign__choose);
        ZetaButton keepButton = ViewUtils.getView(view, R.id.zb__username_first_assign__keep);
        TypefaceTextView summaryTextView = ViewUtils.getView(view, R.id.ttv__username_first_assign__summary);
        AccentColor accentColor = getControllerFactory().getAccentColorController().getAccentColor();
        final int color = accentColor != null ? accentColor.getColor() : Color.TRANSPARENT;
        final int darkenColor = ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(getResources(), R.dimen.background_solid_black_overlay_opacity),
            Color.BLACK);

        backgroundImageView.setDisplayType(ImageAssetImageView.DisplayType.REGULAR);

        vignetteOverlay.setImageBitmap(BitmapUtils.getVignetteBitmap(getResources()));
        vignetteOverlay.setColorFilter(darkenColor, PorterDuff.Mode.DARKEN);

        selfModelObserver.setAndUpdate(getStoreFactory().zMessagingApiStore().getApi().getSelf());

        chooseYourOwnButton.setIsFilled(true);
        chooseYourOwnButton.setAccentColor(color);
        chooseYourOwnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getContainer().onChooseUsernameChosen();
            }
        });

        suggestedUsername = getArguments().getString(ARG_SUGGESTED_USERNAME, "");
        keepButton.setIsFilled(false);
        keepButton.setAccentColor(color);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            keepButton.setTextColor(getResources().getColor(R.color.white));
        } else {
            keepButton.setTextColor(getResources().getColor(R.color.white, getContext().getTheme()));
        }
        keepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getContainer().onKeepUsernameChosen(suggestedUsername);
            }
        });

        nameTextView.setText(getArguments().getString(ARG_NAME, ""));
        usernameTextView.setText(StringUtils.formatHandle(suggestedUsername));

        if (TextUtils.isEmpty(suggestedUsername)) {
            usernameTextView.setVisibility(View.INVISIBLE);
            keepButton.setVisibility(View.GONE);
        }

        TextViewUtils.linkifyText(summaryTextView, Color.WHITE, com.waz.zclient.ui.R.string.wire__typeface__light, false, new Runnable() {
            @Override
            public void run() {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedUsernameFAQEvent());
                getContainer().onOpenUrl(getString(R.string.usernames__learn_more__link));
            }
        });

        return view;
    }

    @Override
    public boolean onBackPressed() {
        return true;
    }

    public interface Container {
        void onChooseUsernameChosen();
        void onKeepUsernameChosen(String username);
        void onOpenUrl(String url);
    }
}
