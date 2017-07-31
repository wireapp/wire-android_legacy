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

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.controllers.tracking.attributes.RegistrationEventContext;
import com.waz.zclient.core.controllers.tracking.events.registration.CancelledPersonalInvite;
import com.waz.zclient.core.controllers.tracking.events.registration.ConfirmedPersonalInviteEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.ViewTOS;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

public class PhoneInvitationFragment extends BaseFragment<PhoneInvitationFragment.Container> implements View.OnClickListener {
    public static final String TAG = PhoneInvitationFragment.class.getName();


    private static final String ARGS_INVITATION_NAME = "ARGS_INVITATION_NAME";
    private static final String ARGS_INVITATION_PHONE = "ARGS_INVITATION_PHONE";

    private String name;
    private String phone;

    private TextView headerTextView;
    private TextView phoneNumberTextView;
    private ZetaButton registerButton;
    private ZetaButton signUpAlternativeButton;

    public static Fragment newInstance(String name, String phone) {
        Fragment fragment = new PhoneInvitationFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARGS_INVITATION_NAME, name);
        bundle.putString(ARGS_INVITATION_PHONE, phone);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        name = args.getString(ARGS_INVITATION_NAME);
        phone = args.getString(ARGS_INVITATION_PHONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_invite__phone, viewGroup, false);

        int buttonColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            buttonColor = getResources().getColor(R.color.text__primary_dark);
        } else {
            buttonColor = getResources().getColor(R.color.text__primary_dark, getContext().getTheme());
        }

        TextView termsOfServicesTextView = ViewUtils.getView(view, R.id.tv__phone_invite__terms_of_service);
        TextViewUtils.linkifyText(termsOfServicesTextView, buttonColor, false, new Runnable() {
            @Override
            public void run() {
                getContainer().onOpenUrlInApp(getString(R.string.url_terms_of_service), true);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ViewTOS(ViewTOS.Source.FROM_JOIN_PAGE));
            }
        });

        signUpAlternativeButton = ViewUtils.getView(view, R.id.zb__phone_invite__signup_alternative);
        signUpAlternativeButton.setIsFilled(false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            signUpAlternativeButton.setAccentColor(getResources().getColor(R.color.text__secondary_dark__40));
        } else {
            signUpAlternativeButton.setAccentColor(getResources().getColor(R.color.text__secondary_dark__40, getContext().getTheme()));
        }
        if (LayoutSpec.isPhone(getActivity())) {
            signUpAlternativeButton.setText(getString(R.string.invitation_phone__normal_phone_signup_button));
        } else {
            signUpAlternativeButton.setText(getString(R.string.invitation_phone__normal_email_signup_button));
        }

        headerTextView = ViewUtils.getView(view, R.id.ttv_phone_invite__header);
        headerTextView.setText(getResources().getString(R.string.invitation_email__welcome_header, name));

        registerButton = ViewUtils.getView(view, R.id.zb__first_launch__confirm);
        registerButton.setIsFilled(true);
        registerButton.setAccentColor(buttonColor);

        phoneNumberTextView = ViewUtils.getView(view, R.id.tet__phone_invite__number);
        phoneNumberTextView.setText(phone);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        KeyboardUtils.showKeyboard(getActivity());

    }

    @Override
    public void onResume() {
        super.onResume();
        signUpAlternativeButton.setOnClickListener(this);
        registerButton.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        signUpAlternativeButton.setOnClickListener(null);
        registerButton.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.zb__phone_invite__signup_alternative:
                onBackClicked();
                break;
            case R.id.zb__first_launch__confirm:
                onRegisterClicked();
                break;
        }
    }

    private void onBackClicked() {
        if (getActivity() == null ||
            getStoreFactory() == null ||
            getStoreFactory().isTornDown()) {
            return;
        }
        KeyboardUtils.hideKeyboard(getActivity());
        getStoreFactory().appEntryStore().onBackPressed();
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new CancelledPersonalInvite(CancelledPersonalInvite.EventContext.INVITE_PHONE));
    }

    private void onRegisterClicked() {
        if (getActivity() == null ||
            getStoreFactory() == null ||
            getStoreFactory().isTornDown()) {
            return;
        }
        // before loging in show loader and dismiss keyboard
        KeyboardUtils.hideKeyboard(getActivity());
        getContainer().enableProgress(true);

        getStoreFactory().appEntryStore().acceptPhoneInvitation(getControllerFactory().getAccentColorController().getAccentColor());
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ConfirmedPersonalInviteEvent(
            RegistrationEventContext.PERSONAL_INVITE_PHONE));
    }

    public interface Container {
        void onOpenUrlInApp(String url, boolean withCloseButton);
        void enableProgress(boolean enabled);
    }
}
