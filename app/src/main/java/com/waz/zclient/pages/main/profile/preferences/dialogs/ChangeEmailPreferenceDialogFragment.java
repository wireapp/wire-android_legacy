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
import android.content.DialogInterface;
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
import com.waz.zclient.utils.ViewUtils;

public class ChangeEmailPreferenceDialogFragment extends BaseDialogFragment<ChangeEmailPreferenceDialogFragment.Container> {
    public static final String TAG = ChangeEmailPreferenceDialogFragment.class.getSimpleName();
    private static final String ARG_EMAIL = "ARG_EMAIL";

    private EmailValidator emailValidator;
    private TextInputLayout inputLayout;

    public static Fragment newInstance(String existingEmail) {
        final ChangeEmailPreferenceDialogFragment fragment = new ChangeEmailPreferenceDialogFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_EMAIL, existingEmail);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        emailValidator = EmailValidator.newInstance();
    }

    @SuppressLint("InflateParams")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.preference_dialog_change_email, null);

        inputLayout = ViewUtils.getView(view, R.id.til__preferences__email);
        final EditText editText = ViewUtils.getView(view, R.id.acet__preferences__email);
        editText.requestFocus();
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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
        final String existingEmail = getArguments().getString(ARG_EMAIL, "");
        editText.setText(existingEmail);
        editText.setSelection(editText.length());

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity())
            .setTitle(R.string.pref__account_action__dialog__change_email__title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null);
        /*
        XXX Temporarily commented out possibility to remove email
        if (getStoreFactory() != null && !StringUtils.isBlank(getStoreFactory().getProfileStore().getMyPhoneNumber())) {
            alertDialogBuilder.setNeutralButton(R.string.pref_account_delete, null);
        }
        */

        final AlertDialog alertDialog = alertDialogBuilder.create();
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
        dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inputLayout == null) {
                    dismiss();
                    return;
                }
                handleInput();
            }
        });
        Button deleteButton = dialog.getButton(Dialog.BUTTON_NEUTRAL);
        if (deleteButton != null) {
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearEmail();
                }
            });
        }

    }

    @Override
    public void onDestroyView() {
        inputLayout = null;
        super.onDestroyView();
    }

    private void clearEmail() {
        if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
            return;
        }
        if (!getStoreFactory().networkStore().hasInternetConnection()) {
            inputLayout.setError(getString(R.string.pref__account_action__dialog__delete_phone__no_internet_error));
            return;
        }
        String email = getStoreFactory().profileStore().getMyEmail();
        ViewUtils.showAlertDialog(getActivity(),
                                  getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__title),
                                  getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__message, email),
                                  getString(android.R.string.ok),
                                  getString(android.R.string.cancel),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
                                              return;
                                          }
                                          getStoreFactory().profileStore().deleteMyEmail(new CredentialsUpdateListener() {
                                              @Override
                                              public void onUpdated() {
                                                  if (getContainer() == null) {
                                                      return;
                                                  }
                                                  getContainer().onEmailCleared();
                                              }
                                              @Override
                                              public void onUpdateFailed(int code, String message, String label) {
                                                  if (getContainer() == null) {
                                                      return;
                                                  }
                                                  inputLayout.setError(getString(R.string.pref__account_action__dialog__delete_email__error));
                                              }
                                          });
                                      }
                                  },
                                  null);
    }

    private void handleInput() {
        final String email = inputLayout.getEditText().getText().toString().trim();
        if (!emailValidator.validate(email)) {
            inputLayout.setError(getString(R.string.pref__account_action__dialog__change_email__error__invalid_email));
            return;
        }
        if (email.equalsIgnoreCase(getStoreFactory().profileStore().getMyEmail())) {
            dismiss();
            return;
        }
        inputLayout.setError(null);
        getStoreFactory().profileStore()
                         .setMyEmail(email,
                                     new CredentialsUpdateListener() {
                                         @Override
                                         public void onUpdated() {
                                             if (getContainer() == null) {
                                                 return;
                                             }
                                             getContainer().onVerifyEmail(email);
                                         }

                                         @Override
                                         public void onUpdateFailed(int errorCode, String message, String label) {
                                             if (inputLayout == null) {
                                                 dismiss();
                                                 return;
                                             }
                                             if (AppEntryError.EMAIL_EXISTS.correspondsTo(errorCode, label)) {
                                                 inputLayout.setError(getString(AppEntryError.EMAIL_EXISTS.headerResource));
                                             } else if (AppEntryError.EMAIL_INVALID.correspondsTo(errorCode, label)) {
                                                 inputLayout.setError(getString(AppEntryError.EMAIL_INVALID.headerResource));
                                             } else {
                                                 inputLayout.setError(getString(AppEntryError.EMAIL_GENERIC_ERROR.headerResource));
                                             }
                                         }
                                     });
    }

    public interface Container {
        void onVerifyEmail(String email);
        void onEmailCleared();
    }
}
