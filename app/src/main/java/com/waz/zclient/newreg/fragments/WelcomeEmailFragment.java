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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.waz.api.NetworkMode;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.controllers.tracking.screens.RegistrationScreen;
import com.waz.zclient.core.controllers.tracking.events.registration.ViewTOS;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ViewUtils;

public class WelcomeEmailFragment extends BaseFragment<WelcomeEmailFragment.Container> implements View.OnClickListener {
    public static final String TAG = WelcomeEmailFragment.class.getName();
    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private TextView termsOfServicesTextView;
    private ZetaButton signInZetaButton;
    private ZetaButton createAccountZetaButton;

    public static WelcomeEmailFragment newInstance() {
        return new WelcomeEmailFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_email__welcome, container, false);

        termsOfServicesTextView = ViewUtils.getView(view, R.id.ttv__welcome__terms_of_service);
        signInZetaButton = ViewUtils.getView(view, R.id.zb__welcome__sign_in);
        createAccountZetaButton = ViewUtils.getView(view, R.id.zb__welcome__create_account);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).loadFromSavedInstance(savedInstanceState);
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).onRegistrationScreen(RegistrationScreen.WELCOME);
    }

    @Override
    public void onStart() {
        super.onStart();
        int color = getContainer().getAccentColor();

        TextViewUtils.linkifyText(termsOfServicesTextView, color, false, new Runnable() {
            @Override
            public void run() {
                WelcomeEmailFragment.this.getContainer().onOpenUrlInApp(getString(R.string.url_terms_of_service), true);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ViewTOS(ViewTOS.Source.FROM_JOIN_PAGE));
            }
        });
        createAccountZetaButton.setIsFilled(true);
        createAccountZetaButton.setAccentColor(color);
        signInZetaButton.setIsFilled(false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            signInZetaButton.setAccentColor(getResources().getColor(R.color.text__secondary_dark__40), true);
        } else {
            signInZetaButton.setAccentColor(getResources().getColor(R.color.text__secondary_dark__40, getContext().getTheme()), true);
        }

        signInZetaButton.setOnClickListener(this);
        createAccountZetaButton.setOnClickListener(this);

        getStoreFactory().networkStore(); // pre-initialize network store, to ensure that internet connection check works on button press
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).saveToSavedInstance(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        signInZetaButton.setOnClickListener(null);
        createAccountZetaButton.setOnClickListener(null);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        termsOfServicesTextView = null;
        createAccountZetaButton = null;
        signInZetaButton = null;
        super.onDestroyView();
    }

    private void signIn() {
        getStoreFactory().appEntryStore().setState(AppEntryState.EMAIL_SIGN_IN);
    }

    private void signUp() {
        getStoreFactory().appEntryStore().clearSavedUserInput();
        getStoreFactory().appEntryStore().setState(AppEntryState.EMAIL_REGISTER);
    }

    private void signUpOrSignIn(final boolean signIn) {
        getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                if (signIn) {
                    signIn();
                } else {
                    signUp();
                }
            }

            @Override
            public void onNoNetwork() {
                int title = R.string.alert_dialog__no_network__header;
                int message = signIn ? R.string.sign_in__no_internet__message : R.string.sign_up__no_internet__message;
                int button = R.string.alert_dialog__confirmation;
                ViewUtils.showAlertDialog(getActivity(), title, message, button, null, true);
            }
        });

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.zb__welcome__sign_in:
                signUpOrSignIn(true);
                break;
            case R.id.zb__welcome__create_account:
                signUpOrSignIn(false);
                break;
        }
    }

    public interface Container {

        void onOpenUrlInApp(String url, boolean withCloseButton);

        int getAccentColor();
    }
}
