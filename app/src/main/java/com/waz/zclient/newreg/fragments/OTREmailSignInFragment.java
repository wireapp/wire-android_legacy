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
import android.widget.TextView;
import com.waz.api.Credentials;
import com.waz.api.CredentialsFactory;
import com.waz.api.LoginListener;
import com.waz.api.Self;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.newreg.views.PhoneConfirmationButton;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.profile.validator.EmailValidator;
import com.waz.zclient.pages.main.profile.validator.PasswordValidator;
import com.waz.zclient.pages.main.profile.views.GuidedEditText;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.ViewUtils;

public class OTREmailSignInFragment extends BaseFragment<OTREmailSignInFragment.Container> implements TextWatcher,
                                                                                                      View.OnClickListener {
    public static final String TAG = OTREmailSignInFragment.class.getName();

    private static final String TET_EMAIL_VALUE_KEY = "email_value";
    private static final String TET_PASSWORD_VALUE_KEY = "password_value";

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private GuidedEditText guidedEditTextEmail;
    private GuidedEditText guidedEditTextPassword;
    private TextView resetPasswordTextView;
    private PhoneConfirmationButton signInButton;

    public static Fragment newInstance() {
        return new OTREmailSignInFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_signin__with_email_otr, viewGroup, false);
        guidedEditTextEmail = ViewUtils.getView(view, R.id.get__sign_in__email);
        guidedEditTextPassword = ViewUtils.getView(view, R.id.get__sign_in__password);
        resetPasswordTextView = ViewUtils.getView(view, R.id.ttv_signin_forgot_password);
        signInButton = ViewUtils.getView(view, R.id.pcb__signin__email);

        guidedEditTextEmail.setResource(R.layout.guided_edit_text_sign_in__email);
        guidedEditTextEmail.setValidator(EmailValidator.newInstanceAcceptingEverything());

        guidedEditTextPassword.setResource(R.layout.guided_edit_text_sign_in__password);
        guidedEditTextPassword.setValidator(PasswordValidator.instanceLegacy(getActivity()));

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(TET_EMAIL_VALUE_KEY, guidedEditTextEmail.getText());
        outState.putString(TET_PASSWORD_VALUE_KEY, guidedEditTextPassword.getText());
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            guidedEditTextEmail.setText(savedInstanceState.getString(TET_EMAIL_VALUE_KEY));
            guidedEditTextPassword.setText(savedInstanceState.getString(TET_PASSWORD_VALUE_KEY));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        guidedEditTextEmail.getEditText().requestFocus();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            onAccentColorHasChanged(getResources().getColor(R.color.text__primary_dark));
        } else {
            onAccentColorHasChanged(getResources().getColor(R.color.text__primary_dark, getContext().getTheme()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        resetPasswordTextView.setOnClickListener(this);
        signInButton.setOnClickListener(this);
        guidedEditTextPassword.getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    signIn();
                    return true;
                } else {
                    return false;
                }
            }
        });
        guidedEditTextEmail.getEditText().addTextChangedListener(this);
        guidedEditTextPassword.getEditText().addTextChangedListener(this);

        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());
    }

    @Override
    public void onPause() {
        guidedEditTextPassword.getEditText().setOnEditorActionListener(null);
        guidedEditTextEmail.getEditText().removeTextChangedListener(this);
        guidedEditTextPassword.getEditText().removeTextChangedListener(this);
        resetPasswordTextView.setOnClickListener(null);
        signInButton.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        guidedEditTextEmail = null;
        guidedEditTextPassword = null;
        resetPasswordTextView = null;
        super.onDestroyView();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Actions
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private boolean areEmailAndPasswordValid() {
        return guidedEditTextEmail.onlyValidate() && guidedEditTextPassword.onlyValidate();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (areEmailAndPasswordValid()) {
            signInButton.setState(PhoneConfirmationButton.State.CONFIRM);
        } else {
            signInButton.setState(PhoneConfirmationButton.State.NONE);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    public void onAccentColorHasChanged(int color) {
        guidedEditTextEmail.setAccentColor(color);
        guidedEditTextPassword.setAccentColor(color);
        signInButton.setAccentColor(color);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ttv_signin_forgot_password:
                onPasswordResetButtonClicked();
                break;
            case R.id.pcb__signin__email:
                signIn();
                break;
        }
    }

    private void signIn() {
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());

        Credentials credentials = CredentialsFactory.emailCredentials(guidedEditTextEmail.getText(),
                                                                      guidedEditTextPassword.getText());
        getStoreFactory().zMessagingApiStore().getApi().login(credentials, new LoginListener() {
            @Override
            public void onSuccess(Self user) {
                if (getContainer() == null) {
                    return;
                }

                getContainer().startMain();
            }

            @Override
            public void onFailed(int code, String message, String label) {
                if (getContainer() == null) {
                    return;
                }

                getContainer().enableProgress(false);
                AppEntryUtil.showErrorDialog(OTREmailSignInFragment.this.getActivity(),
                                             AppEntryError.EMAIL_REGISTER_GENERIC_ERROR,
                                             new AppEntryUtil.ErrorDialogCallback() {
                                                 @Override
                                                 public void onOk() {
                                                     KeyboardUtils.showKeyboard(getActivity());
                                                 }
                                             });
            }
        });
    }

    private void onPasswordResetButtonClicked() {
        getContainer().onOpenUrl(getResources().getString(R.string.url_password_reset));
    }

    public interface Container {

        void onOpenUrl(String url);

        void enableProgress(boolean enabled);

        void startMain();
    }
}
