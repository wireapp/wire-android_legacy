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
package com.waz.zclient.pages.main.profile.preferences.dialogs;

import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;

import com.waz.api.CredentialsUpdateListener;
import com.waz.api.UsernameValidation;
import com.waz.api.UsernameValidationError;
import com.waz.api.UsernamesRequestCallback;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;

import java.util.Locale;

public class ChangeUsernamePreferenceDialogFragment extends BaseDialogFragment<ChangeUsernamePreferenceDialogFragment.Container> {
    public static final String TAG = ChangeUsernamePreferenceDialogFragment.class.getSimpleName();
    private static final String ARG_USERNAME = "ARG_USERNAME";

    private TextInputLayout usernameInputLayout;
    private AppCompatEditText usernameEditText;
    private LoadingIndicatorView usernameVerifyingIndicator;
    private View okButton;
    private View backButton;
    private String inputUsername = "";

    private TextWatcher usernameTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            String lowercaseString = charSequence.toString().toLowerCase(Locale.getDefault());
            if (!lowercaseString.equals(charSequence.toString())) {
                usernameEditText.setText(lowercaseString);
                usernameEditText.setSelection(lowercaseString.length());
            } else {
                UsernameValidation validation = getStoreFactory().getZMessagingApiStore().getApi().getUsernames().isUsernameValid(charSequence.toString());
                if (!validation.isValid()) {
                    usernameInputLayout.setError(getErrorMessage(validation.reason()));
                } else {
                    usernameInputLayout.setError("");
                }
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    };

    private CredentialsUpdateListener setUsernameCallback = new CredentialsUpdateListener() {
        @Override
        public void onUpdated() {
            getContainer().onUsernameChanged(inputUsername);
            dismiss();
        }

        @Override
        public void onUpdateFailed(int code, String message, String label) {
            usernameInputLayout.setError(getErrorMessage(UsernameValidationError.ALREADY_TAKEN));
            enableEditing();
        }
    };

    private UsernamesRequestCallback usernameAvailableCallback = new UsernamesRequestCallback() {
        @Override
        public void onUsernameRequestResult(String username, final UsernameValidation validation) {
            if (validation.isValid()) {
                getStoreFactory().getZMessagingApiStore().getApi().getSelf().setUsername(username, setUsernameCallback);
            } else {
                usernameInputLayout.setError(getErrorMessage(validation.reason()));
                enableEditing();
                editBoxShakeAnimation();
            }
        }
    };

    private View.OnClickListener okButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            inputUsername = usernameEditText.getText().toString();
            UsernameValidation validation = getStoreFactory().getZMessagingApiStore().getApi().getUsernames().isUsernameValid(inputUsername);
            if (!validation.isValid()) {
                editBoxShakeAnimation();
                return;
            }
            disableEditing();
            getStoreFactory().getZMessagingApiStore().getApi().getUsernames().isUsernameAvailable(inputUsername, usernameAvailableCallback);
        }
    };

    private void disableEditing() {
        usernameEditText.setEnabled(false);
        usernameVerifyingIndicator.show();
        okButton.setEnabled(false);
        backButton.setEnabled(false);
    }

    private void enableEditing() {
        usernameVerifyingIndicator.hide();
        okButton.setEnabled(true);
        backButton.setEnabled(true);
        usernameEditText.setEnabled(true);
    }

    public ChangeUsernamePreferenceDialogFragment() {
    }

    public static ChangeUsernamePreferenceDialogFragment newInstance(String currentUsername) {
        ChangeUsernamePreferenceDialogFragment fragment = new ChangeUsernamePreferenceDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, currentUsername);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        String username = getArguments().getString(ARG_USERNAME, "");

        View view = inflater.inflate(R.layout.fragment_change_username_preference_dialog, container, false);
        usernameInputLayout = ViewUtils.getView(view, R.id.til__change_username);
        usernameEditText = ViewUtils.getView(view, R.id.acet__change_username);
        usernameVerifyingIndicator = ViewUtils.getView(view, R.id.liv__username_verifying_indicator);
        okButton = ViewUtils.getView(view, R.id.tv__ok_button);
        backButton  = ViewUtils.getView(view, R.id.tv__back_button);

        usernameEditText.setText(username);
        usernameEditText.setSelection(username.length());
        usernameEditText.addTextChangedListener(usernameTextWatcher);

        usernameVerifyingIndicator.setType(LoadingIndicatorView.SPINNER);
        usernameVerifyingIndicator.hide();

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        okButton.setOnClickListener(okButtonClickListener);

        getDialog().setCancelable(false);
        return view;
    }

    private String getErrorMessage(UsernameValidationError errorCode) {
        switch (errorCode) {
            case TOO_LONG:
                return getString(R.string.pref__account_action__dialog__change_username__error_too_long);
            case TOO_SHORT:
                return getString(R.string.pref__account_action__dialog__change_username__error_too_short);
            case INVALID_CHARACTERS:
                return getString(R.string.pref__account_action__dialog__change_username__error_invalid_characters);
            case ALREADY_TAKEN:
                return getString(R.string.pref__account_action__dialog__change_username__error_already_taken);
            default:
                return getString(R.string.pref__account_action__dialog__change_username__error_unknown);
        }
    }

    private void editBoxShakeAnimation() {
        usernameEditText.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.shake_animation));
    }

    public interface Container {
        void onUsernameChanged(String username);
    }
}
