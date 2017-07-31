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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.api.CredentialsUpdateListener;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.pages.main.profile.validator.EmailValidator;
import com.waz.zclient.pages.main.profile.validator.PasswordValidator;
import com.waz.zclient.utils.ViewUtils;

public class AddEmailAndPasswordPreferenceDialogFragment extends BaseDialogFragment<AddEmailAndPasswordPreferenceDialogFragment.Container> {
    public static final String TAG = AddEmailAndPasswordPreferenceDialogFragment.class.getSimpleName();
    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private EmailValidator emailValidator;
    private PasswordValidator passwordValidator;

    public static Fragment newInstance() {
        return new AddEmailAndPasswordPreferenceDialogFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        emailValidator = EmailValidator.newInstance();
        passwordValidator = PasswordValidator.instance(getActivity());
    }

    @SuppressLint("InflateParams")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.preference_dialog_add_email_password, null);

        emailInputLayout = ViewUtils.getView(view, R.id.til__preferences__email);
        final EditText emailEditText = ViewUtils.getView(view, R.id.acet__preferences__email);
        emailEditText.requestFocus();
        emailEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    emailInputLayout.getEditText().requestFocus();
                    return true;
                } else {
                    return false;
                }
            }
        });

        passwordInputLayout = ViewUtils.getView(view, R.id.til__preferences__password);
        final EditText passwordEditText = ViewUtils.getView(view, R.id.acet__preferences__password);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    handleInput();
                    return true;
                } else {
                    return false;
                }
            }
        });

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
            .setTitle(R.string.pref__account_action__dialog__add_email_password__title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        final Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (emailInputLayout == null) {
                    dismiss();
                    return;
                }
                handleInput();
            }
        });
    }

    @Override
    public void onDestroyView() {
        passwordInputLayout = null;
        emailInputLayout = null;
        super.onDestroyView();
    }

    private void handleInput() {
        final String email = emailInputLayout.getEditText().getText().toString().trim();
        if (!emailValidator.validate(email)) {
            emailInputLayout.setError(getString(R.string.pref__account_action__dialog__add_email_password__error__invalid_email));
            return;
        }

        final String password = passwordInputLayout.getEditText().getText().toString().trim();
        if (!passwordValidator.validate(password)) {
            passwordInputLayout.setError(getString(R.string.pref__account_action__dialog__add_email_password__error__invalid_password));
            return;
        }

        getStoreFactory().profileStore()
                         .addEmailAndPassword(email,
                                              password,
                                              new CredentialsUpdateListener() {
                                                  @Override
                                                  public void onUpdated() {
                                                      if (getContainer() == null) {
                                                          return;
                                                      }
                                                      getContainer().onVerifyEmail(email);
                                                  }

                                                  @Override
                                                  public void onUpdateFailed(int errorCode,
                                                                             String message,
                                                                             String label) {
                                                      if (passwordInputLayout == null ||
                                                          emailInputLayout == null) {
                                                          return;
                                                      }
                                                      if (AppEntryError.PHONE_ADD_PASSWORD.correspondsTo(errorCode, label)) {
                                                          passwordInputLayout.setError(getString(AppEntryError.PHONE_ADD_PASSWORD.headerResource));
                                                      } else if (AppEntryError.EMAIL_EXISTS.correspondsTo(errorCode, label)) {
                                                          passwordInputLayout.setError(getString(AppEntryError.EMAIL_EXISTS.headerResource));
                                                      } else if (AppEntryError.EMAIL_INVALID.correspondsTo(errorCode, label)) {
                                                          passwordInputLayout.setError(getString(AppEntryError.EMAIL_INVALID.headerResource));
                                                      } else {
                                                          passwordInputLayout.setError(getString(AppEntryError.EMAIL_GENERIC_ERROR.headerResource));
                                                      }
                                                  }
                                              });
    }

    public interface Container {
        void onVerifyEmail(String email);
    }
}
