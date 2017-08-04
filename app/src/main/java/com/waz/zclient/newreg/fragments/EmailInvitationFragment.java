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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.controllers.tracking.attributes.RegistrationEventContext;
import com.waz.zclient.core.controllers.tracking.events.registration.CancelledPersonalInvite;
import com.waz.zclient.core.controllers.tracking.events.registration.ConfirmedPersonalInviteEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.ViewTOS;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.profile.validator.PasswordValidator;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

public class EmailInvitationFragment extends BaseFragment<EmailInvitationFragment.Container> implements TextWatcher,
                                                                                                        View.OnClickListener {
    public static final String TAG = EmailInvitationFragment.class.getName();


    private static final String ARGS_INVITATION_NAME = "ARGS_INVITATION_NAME";
    private static final String ARGS_INVITATION_EMAIL = "ARGS_INVITATION_EMAIL";

    private String name;
    private String email;

    private TextView headerTextView;
    private TextView messageTextView;
    private EditText passwordEditText;
    private TextView emailTextView;
    private ZetaButton registerButton;
    private ZetaButton signUpAlternativeButton;

    public static Fragment newInstance(String name, String email) {
        Fragment fragment = new EmailInvitationFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARGS_INVITATION_NAME, name);
        bundle.putString(ARGS_INVITATION_EMAIL, email);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        name = args.getString(ARGS_INVITATION_NAME);
        email = args.getString(ARGS_INVITATION_EMAIL);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_invite__email, viewGroup, false);

        int buttonColor;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            buttonColor = getResources().getColor(R.color.text__primary_dark);
        } else {
            buttonColor = getResources().getColor(R.color.text__primary_dark, getContext().getTheme());
        }

        TextView termsOfServicesTextView = ViewUtils.getView(view, R.id.tv__email_invite__terms_of_service);
        TextViewUtils.linkifyText(termsOfServicesTextView, buttonColor, false, new Runnable() {
            @Override
            public void run() {
                if (getContainer() == null ||
                    getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                getContainer().onOpenUrlInApp(getString(R.string.url_terms_of_service), true);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ViewTOS(ViewTOS.Source.FROM_JOIN_PAGE));
            }
        });

        signUpAlternativeButton = ViewUtils.getView(view, R.id.zb__email_invite__signup_alternative);
        signUpAlternativeButton.setIsFilled(false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            signUpAlternativeButton.setAccentColor(getResources().getColor(R.color.text__secondary_dark__40));
        } else {
            signUpAlternativeButton.setAccentColor(getResources().getColor(R.color.text__secondary_dark__40, getContext().getTheme()));
        }
        if (LayoutSpec.isPhone(getActivity())) {
            signUpAlternativeButton.setText(getString(R.string.invitation_email__normal_phone_signup_button));
        } else {
            signUpAlternativeButton.setText(getString(R.string.invitation_email__normal_email_signup_button));
        }

        headerTextView = ViewUtils.getView(view, R.id.ttv_email_invite__header);
        headerTextView.setText(getResources().getString(R.string.invitation_email__welcome_header, name));
        messageTextView = ViewUtils.getView(view, R.id.ttv_email_invite__message);

        registerButton = ViewUtils.getView(view, R.id.zb__email_invite__register);
        registerButton.setIsFilled(true);
        registerButton.setAccentColor(buttonColor);
        registerButton.setEnabled(false);

        passwordEditText = ViewUtils.getView(view, R.id.tet__email_invite__password);
        passwordEditText.setHint(getResources().getString(R.string.invitation_email__password_placeholder,
                                                  getResources().getInteger(R.integer.password_validator__min_password_length)));

        emailTextView = ViewUtils.getView(view, R.id.tet__email_invite__email);
        emailTextView.setText(email);

        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int[] buttonLocation = new int[2];
                signUpAlternativeButton.getLocationOnScreen(buttonLocation);
                int[] headerLocation = new int[2];
                headerTextView.getLocationOnScreen(headerLocation);
                if (buttonLocation[1] + signUpAlternativeButton.getHeight() > headerLocation[1]) {
                    headerTextView.setVisibility(View.INVISIBLE);
                    messageTextView.setVisibility(View.INVISIBLE);
                } else {
                    headerTextView.setVisibility(View.VISIBLE);
                    messageTextView.setVisibility(View.VISIBLE);
                }
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        passwordEditText.requestFocus();
        KeyboardUtils.showKeyboard(getActivity());

    }

    @Override
    public void onResume() {
        super.onResume();
        signUpAlternativeButton.setOnClickListener(this);
        registerButton.setOnClickListener(this);
        passwordEditText.addTextChangedListener(this);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE && isPasswordValid();
            }
        });
    }

    @Override
    public void onPause() {
        passwordEditText.removeTextChangedListener(this);
        signUpAlternativeButton.setOnClickListener(null);
        registerButton.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.zb__email_invite__signup_alternative:
                onBackClicked();
                break;
            case R.id.zb__email_invite__register:
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
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new CancelledPersonalInvite(CancelledPersonalInvite.EventContext.INVITE_EMAIL));
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

        getStoreFactory().appEntryStore().acceptEmailInvitation(passwordEditText.getText().toString(),
                                                                   getControllerFactory().getAccentColorController().getAccentColor());
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ConfirmedPersonalInviteEvent(
            RegistrationEventContext.PERSONAL_INVITE_EMAIL));
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        registerButton.setEnabled(isPasswordValid());
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    private boolean isPasswordValid() {
        return PasswordValidator.instance(getActivity()).validate(passwordEditText.getText().toString());
    }

    public interface Container {
        void onOpenUrlInApp(String url, boolean withCloseButton);
        void enableProgress(boolean enabled);
    }
}
