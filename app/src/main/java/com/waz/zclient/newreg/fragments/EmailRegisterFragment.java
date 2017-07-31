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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.newreg.views.PhoneConfirmationButton;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.profile.views.OnTextChangedListener;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.text.TypefaceEditText;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.ViewUtils;

public class EmailRegisterFragment extends BaseFragment<EmailRegisterFragment.Container> implements TextWatcher,
                                                                                                    View.OnClickListener {
    public static final String TAG = EmailRegisterFragment.class.getName();

    private TypefaceEditText textViewName;
    private TypefaceEditText textViewEmail;
    private TypefaceEditText textViewPassword;
    private TypefaceTextView passwordLengthMessage;
    private View backButton;
    private PhoneConfirmationButton signInButton;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(EmailRegisterFragment.this.getActivity(),
                                         appEntryError,
                                         new AppEntryUtil.ErrorDialogCallback() {
                                             @Override
                                             public void onOk() {
                                                 KeyboardUtils.showKeyboard(getActivity());
                                             }
                                         });
        }
    };

    public static Fragment newInstance() {
        return new EmailRegisterFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_signup_email, viewGroup, false);
        textViewName = ViewUtils.getView(view, R.id.ttv__signup__name);
        textViewEmail = ViewUtils.getView(view, R.id.ttv__signup__email);
        textViewPassword = ViewUtils.getView(view, R.id.ttv__signup__password);
        backButton = ViewUtils.getView(view, R.id.ll__registrations_button__back);
        signInButton = ViewUtils.getView(view, R.id.pcb__signin__email);
        passwordLengthMessage = ViewUtils.getView(view, R.id.ttv__email_reg__pw_length);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        passwordLengthMessage.setAlpha(0f);
        passwordLengthMessage.setText(getResources().getString(R.string.new_reg__password_length, getResources().getInteger(R.integer.password_validator__min_password_length)));
        textViewName.setText(getStoreFactory().appEntryStore().getName());
        textViewEmail.setText(getStoreFactory().appEntryStore().getEmail());
        textViewPassword.setText(getStoreFactory().appEntryStore().getPassword());

        textViewPassword.addTextChangedListener(new OnTextChangedListener() {
            boolean wasValid = true;
            @Override
            public void afterTextChanged(Editable s) {
                if (isPasswordValid() == wasValid) {
                    return;
                }
                if (!isPasswordValid()) {
                    passwordLengthMessage.animate()
                                         .alpha(1f)
                                         .setInterpolator(new Expo.EaseIn())
                                         .start();
                } else {
                    passwordLengthMessage.animate()
                                         .alpha(0f)
                                         .setInterpolator(new Expo.EaseIn())
                                         .start();
                }
                wasValid = isPasswordValid();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        textViewName.requestFocus();
        onAccentColorHasChanged(getContainer().getAccentColor());
        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());

    }

    @Override
    public void onResume() {
        super.onResume();
        backButton.setOnClickListener(this);
        signInButton.setOnClickListener(this);
        textViewName.addTextChangedListener(this);
        textViewEmail.addTextChangedListener(this);
        textViewPassword.addTextChangedListener(this);
        textViewPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE && areFieldsValid();
            }
        });
    }

    @Override
    public void onPause() {
        textViewName.removeTextChangedListener(this);
        textViewEmail.removeTextChangedListener(this);
        textViewPassword.removeTextChangedListener(this);
        backButton.setOnClickListener(null);
        signInButton.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        textViewName = null;
        textViewEmail = null;
        textViewPassword = null;
        backButton = null;
        super.onDestroyView();
    }

    private void onAccentColorHasChanged(int color) {
        textViewName.setAccentColor(color);
        textViewEmail.setAccentColor(color);
        textViewPassword.setAccentColor(color);
        signInButton.setAccentColor(color);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ll__registrations_button__back:
                onBackClicked();
                break;
            case R.id.pcb__signin__email:
                onRegisterClicked();
                break;

        }
    }

    private void onBackClicked() {
        KeyboardUtils.hideKeyboard(getActivity());
        getStoreFactory().appEntryStore().onBackPressed();
    }

    private void onRegisterClicked() {
        // before loging in show loader and dismiss keyboard
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());

        getStoreFactory().appEntryStore()
                         .registerWithEmail(textViewEmail.getText().toString(),
                                            textViewPassword.getText().toString(),
                                            textViewName.getText().toString(),
                                            getControllerFactory().getAccentColorController().getAccentColor(),
                                            errorCallback);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (areFieldsValid()) {
            signInButton.setState(PhoneConfirmationButton.State.CONFIRM);
        } else {
            signInButton.setState(PhoneConfirmationButton.State.NONE);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    private boolean areFieldsValid() {
        String name = textViewName.getText().toString().trim();
        String email = textViewEmail.getText().toString().trim();

        boolean nameValid = !TextUtils.isEmpty(name);
        boolean emailValid = !TextUtils.isEmpty(email);

        return nameValid && emailValid && isPasswordValid();
    }

    private boolean isPasswordValid() {
        String password = textViewPassword.getText().toString();
        int minPasswordLength = getResources().getInteger(R.integer.password_validator__min_password_length);
        return !TextUtils.isEmpty(password) && password.length() >= minPasswordLength;
    }

    public interface Container {

        void enableProgress(boolean enabled);

        int getAccentColor();
    }
}
