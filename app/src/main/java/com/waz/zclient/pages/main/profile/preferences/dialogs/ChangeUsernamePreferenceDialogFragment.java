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
import android.text.TextUtils;
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
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.controllers.tracking.events.settings.SetUsernameEvent;
import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import java.util.Locale;

public class ChangeUsernamePreferenceDialogFragment extends BaseDialogFragment<ChangeUsernamePreferenceDialogFragment.Container> {
    public static final String TAG = ChangeUsernamePreferenceDialogFragment.class.getSimpleName();
    private static final String ARG_USERNAME = "ARG_USERNAME";
    private static final String ARG_CANCEL_ENABLED = "ARG_CANCEL_ENABLED";

    private TextInputLayout usernameInputLayout;
    private AppCompatEditText usernameEditText;
    private LoadingIndicatorView usernameVerifyingIndicator;
    private View okButton;
    private View backButton;
    private String inputUsername = "";
    private String suggestedUsername;
    private boolean editingEnabled = true;
    private boolean cancelEnabled = true;

    private TextWatcher usernameTextWatcher = new TextWatcher() {
        private String lastText = "";

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            String currentText = charSequence.toString();
            if (getStoreFactory() != null &&
                getStoreFactory().getZMessagingApiStore().getApi().getUsernames().isUsernameValid(charSequence.toString()).reason() != UsernameValidationError.INVALID_CHARACTERS) {
                lastText = currentText;
            }
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            String normalText = editable.toString();
            String lowercaseString = normalText.toLowerCase(Locale.getDefault());
            if (!lowercaseString.equals(normalText)) {
                usernameEditText.setText(lowercaseString);
            } else {
                IStoreFactory storeFactory = getStoreFactory();
                if (storeFactory == null || storeFactory.isTornDown()) {
                    return;
                }
                UsernameValidation validation = storeFactory.getZMessagingApiStore().getApi().getUsernames().isUsernameValid(normalText);
                if (!validation.isValid()) {
                    usernameInputLayout.setError(getErrorMessage(validation.reason()));
                    if (validation.reason() == UsernameValidationError.INVALID_CHARACTERS) {
                        usernameEditText.setText(lastText);
                        editBoxShakeAnimation();
                    } else if (validation.reason() == UsernameValidationError.TOO_LONG) {
                        usernameEditText.setText(lastText);
                    }
                } else {
                    usernameInputLayout.setError("");
                    if (!storeFactory.getZMessagingApiStore().getApi().getSelf().getUsername().equals(normalText)) {
                        storeFactory.getZMessagingApiStore().getApi().getUsernames().isUsernameAvailable(normalText, usernameAvailableCallback);
                    }
                }
            }
            usernameEditText.setSelection(usernameEditText.getText().length());
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
            usernameInputLayout.setError(getString(R.string.pref__account_action__dialog__change_username__error_unknown));
            enableEditing();
        }
    };

    private UsernamesRequestCallback usernameAvailableCallback = new UsernamesRequestCallback() {
        @Override
        public void onUsernameRequestResult(final UsernameValidation[] validation) {
            if (!validation[0].username().equals(usernameEditText.getText().toString())) {
                return;
            }
            if (validation[0].isValid()) {
                usernameInputLayout.setError("");
                okButton.setEnabled(editingEnabled);
            } else {
                usernameInputLayout.setError(getErrorMessage(validation[0].reason()));
                okButton.setEnabled(false);
            }
        }

        @Override
        public void onRequestFailed(Integer errorCode) {
            usernameInputLayout.setError(getString(R.string.pref__account_action__dialog__change_username__error_unknown));
            enableEditing();
            editBoxShakeAnimation();
        }
    };

    private View.OnClickListener okButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            inputUsername = usernameEditText.getText().toString();
            if (TextUtils.isEmpty(inputUsername)) {
                return;
            }
            IStoreFactory storeFactory = getStoreFactory();
            if (storeFactory != null &&
                !storeFactory.isTornDown() &&
                storeFactory.getZMessagingApiStore().getApi().getSelf().getUsername().equals(inputUsername)) {
                dismiss();
                return;
            }
            UsernameValidation validation = getStoreFactory().getZMessagingApiStore().getApi().getUsernames().isUsernameValid(inputUsername);
            if (!validation.isValid()) {
                editBoxShakeAnimation();
                return;
            }
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SetUsernameEvent(inputUsername.length()));
            disableEditing();
            getStoreFactory().getZMessagingApiStore().getApi().getSelf().setUsername(inputUsername, setUsernameCallback);
        }
    };

    private void disableEditing() {
        editingEnabled = false;
        usernameEditText.setEnabled(false);
        usernameVerifyingIndicator.show();
        okButton.setEnabled(false);
        backButton.setEnabled(false);
    }

    private void enableEditing() {
        editingEnabled = true;
        usernameVerifyingIndicator.hide();
        okButton.setEnabled(true);
        backButton.setEnabled(true);
        usernameEditText.setEnabled(true);
    }

    public ChangeUsernamePreferenceDialogFragment() {
    }

    public static ChangeUsernamePreferenceDialogFragment newInstance(String currentUsername, boolean cancellable) {
        ChangeUsernamePreferenceDialogFragment fragment = new ChangeUsernamePreferenceDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, currentUsername);
        args.putBoolean(ARG_CANCEL_ENABLED, cancellable);
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
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        suggestedUsername = getArguments().getString(ARG_USERNAME, "");
        cancelEnabled = getArguments().getBoolean(ARG_CANCEL_ENABLED);

        View view = inflater.inflate(R.layout.fragment_change_username_preference_dialog, container, false);
        usernameInputLayout = ViewUtils.getView(view, R.id.til__change_username);
        usernameEditText = ViewUtils.getView(view, R.id.acet__change_username);
        usernameVerifyingIndicator = ViewUtils.getView(view, R.id.liv__username_verifying_indicator);
        okButton = ViewUtils.getView(view, R.id.tv__ok_button);
        backButton  = ViewUtils.getView(view, R.id.tv__back_button);

        usernameEditText.setText(suggestedUsername);
        usernameEditText.setSelection(suggestedUsername.length());

        usernameVerifyingIndicator.setType(LoadingIndicatorView.SPINNER);
        usernameVerifyingIndicator.hide();

        setCancelable(false);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        usernameEditText.addTextChangedListener(usernameTextWatcher);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!cancelEnabled && getStoreFactory() != null) {
                    CredentialsUpdateListener emptyCredentialsListener = new CredentialsUpdateListener() {
                        @Override
                        public void onUpdated() {
                        }

                        @Override
                        public void onUpdateFailed(int code, String message, String label) {
                        }
                    };
                    getStoreFactory().getZMessagingApiStore().getApi().getSelf().setUsername(suggestedUsername, emptyCredentialsListener);
                }
                dismiss();
            }
        });
        okButton.setOnClickListener(okButtonClickListener);
        usernameEditText.requestFocus();
    }

    @Override
    public void onStop() {
        super.onStop();
        usernameEditText.removeTextChangedListener(usernameTextWatcher);
        backButton.setOnClickListener(null);
        okButton.setOnClickListener(null);
    }

    private String getErrorMessage(UsernameValidationError errorCode) {
        switch (errorCode) {
            case TOO_LONG:
                return " ";
            case TOO_SHORT:
                return " ";
            case INVALID_CHARACTERS:
                return " ";
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
