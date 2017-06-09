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
package com.waz.zclient.pages.main.profile.preferences;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.preferences.PreferencesActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.profile.ResetPassword;
import com.waz.zclient.controllers.tracking.events.profile.SignOut;
import com.waz.zclient.core.controllers.tracking.events.session.LoggedOutEvent;
import com.waz.zclient.core.controllers.tracking.events.settings.EditedUsernameEvent;
import com.waz.zclient.core.stores.profile.ProfileStoreObserver;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.preferences.dialogs.AccentColorPreferenceDialogFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.AddEmailAndPasswordPreferenceDialogFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.AddPhoneNumberPreferenceDialogFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.ChangeEmailPreferenceDialogFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.ChangeUsernamePreferenceDialogFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.VerifyEmailPreferenceFragment;
import com.waz.zclient.pages.main.profile.preferences.dialogs.VerifyPhoneNumberPreferenceFragment;
import com.waz.zclient.preferences.BasePreferenceFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;

import net.xpece.android.support.preference.EditTextPreference;

public class AccountPreferences extends BasePreferenceFragment implements ProfileStoreObserver,
                                                                          SharedPreferences.OnSharedPreferenceChangeListener,
                                                                          VerifyPhoneNumberPreferenceFragment.Container,
                                                                          VerifyEmailPreferenceFragment.Container,
                                                                          AddEmailAndPasswordPreferenceDialogFragment.Container,
                                                                          ChangeEmailPreferenceDialogFragment.Container,
                                                                          AddPhoneNumberPreferenceDialogFragment.Container,
                                                                          ChangeUsernamePreferenceDialogFragment.Container,
                                                                          AccentColorObserver {

    public static final String TAG = AccountPreferences.class.getName();
    private EditTextPreference namePreference;
    private Preference usernamePreference;
    private Preference phonePreference;
    private Preference emailPreference;
    private Preference signOutPreference;
    private Preference deletePreference;
    private Preference resetPasswordPreference;
    private PicturePreference picturePreference;
    private ColorPreference colorPreference;

    public static AccountPreferences newInstance(String rootKey, Bundle extras) {
        AccountPreferences f = new AccountPreferences();
        Bundle args = new Bundle(extras);
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences2(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_account);
        boolean shouldShowUsernameEdit = getArguments().getBoolean(PreferencesActivity.ShowUsernameEdit());
        namePreference = (EditTextPreference) findPreference(getString(R.string.pref_account_name_key));
        namePreference.setOnEditTextCreatedListener(new EditTextPreference.OnEditTextCreatedListener() {
            @Override
            public void onEditTextCreated(final EditText edit) {
                //Having it directly on the xml doesn't seem to work for EditTextPreference
                edit.setSingleLine(true);

                edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        edit.setSelection(edit.getText().length());
                    }
                });
            }
        });

        namePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String newName = ((String) newValue).trim();
                if (TextUtils.getTrimmedLength(newName) < getResources().getInteger(R.integer.account_preferences__min_name_length)) {
                    ViewUtils.showAlertDialog(getActivity(),
                        null,
                        getString(R.string.pref_account_edit_name_empty_warning),
                        getString(R.string.pref_account_edit_name_empty_verify),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        },
                        false);

                    return false;
                }
                getStoreFactory().getProfileStore().setMyName(newName);
                return false;
            }
        });
        usernamePreference = findPreference(getString(R.string.pref_account_username_key));
        usernamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (getStoreFactory() == null || getStoreFactory().isTornDown() || getStoreFactory().getProfileStore().getSelfUser() == null) {
                    return false;
                }
                String username = getStoreFactory().getProfileStore().getSelfUser().getUsername();
                changeUsername(username, true);
                return true;
            }
        });
        phonePreference = findPreference(getString(R.string.pref_account_phone_key));
        phonePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final CharSequence phoneNumber = preference.getTitle();
                if (TextUtils.isEmpty(phoneNumber) || phoneNumber.equals(getString(R.string.pref_account_add_phone_title))) {
                    addPhoneNumber();
                } else {
                    changePhoneNumber(String.valueOf(phoneNumber));
                }
                return true;
            }
        });
        emailPreference = findPreference(getString(R.string.pref_account_email_key));
        emailPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final CharSequence email = preference.getTitle();
                if (TextUtils.isEmpty(email) || email.equals(getString(R.string.pref_account_add_email_title))) {
                    addEmailAndPassword();
                } else {
                    changeEmail(String.valueOf(email));
                }
                return true;
            }
        });
        resetPasswordPreference = findPreference(getString(R.string.pref_account_password_key));
        signOutPreference = findPreference(getString(R.string.pref_account_sign_out_key));
        signOutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                signOut();
                return true;
            }
        });
        deletePreference = findPreference(getString(R.string.pref_account_delete_key));
        deletePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                deleteAccount();
                return true;
            }
        });

        resetPasswordPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ResetPassword(ResetPassword.Location.FROM_PROFILE));
                return false;
            }
        });

        picturePreference = (PicturePreference) findPreference(getString(R.string.pref_account_picture_key));
        picturePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                getControllerFactory().getCameraController().openCamera(CameraContext.SETTINGS);
                return true;
            }
        });
        colorPreference = (ColorPreference) findPreference(getString(R.string.pref_account_color_key));
        colorPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                getChildFragmentManager().beginTransaction()
                                         .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                         .add(AccentColorPreferenceDialogFragment.newInstance(getStoreFactory().getProfileStore().getAccentColor()),
                                              AccentColorPreferenceDialogFragment.TAG)
                                         .addToBackStack(AccentColorPreferenceDialogFragment.TAG)
                                         .commit();
                return true;
            }
        });
        if (shouldShowUsernameEdit) {
            changeUsername(getControllerFactory().getUsernameController().getGeneratedUsername(), false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().getProfileStore().addProfileStoreAndUpdateObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().getProfileStore().removeProfileStoreObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (namePreference != null) {
            namePreference.setOnPreferenceChangeListener(null);
            namePreference = null;
        }
        phonePreference = null;
        emailPreference = null;
        if (signOutPreference != null) {
            signOutPreference.setOnPreferenceClickListener(null);
            signOutPreference = null;
        }
        if (resetPasswordPreference != null) {
            resetPasswordPreference.setOnPreferenceClickListener(null);
            resetPasswordPreference = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onAccentColorChangedRemotely(Object sender, int color) {
    }

    @Override
    public void onMyNameHasChanged(Object sender, String myName) {
        if (namePreference == null) {
            return;
        }
        namePreference.setTitle(myName);
        namePreference.setText(myName);
        namePreference.setSummary(getString(R.string.pref_account_name_title));
    }

    @Override
    public void onMyEmailHasChanged(String myEmail, boolean isVerified) {
        if (emailPreference == null) {
            return;
        }
        if (TextUtils.isEmpty(myEmail)) {
            emailPreference.setTitle(R.string.pref_account_add_email_title);
            emailPreference.setSummary("");
        } else {
            if (isVerified) {
                getChildFragmentManager().popBackStack(VerifyEmailPreferenceFragment.TAG,
                                                       FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
            emailPreference.setTitle(myEmail);
            emailPreference.setSummary(R.string.pref_account_email_title);
        }
    }

    @Override
    public void onMyPhoneHasChanged(String myPhone, boolean isVerified) {
        if (phonePreference == null) {
            return;
        }
        if (TextUtils.isEmpty(myPhone)) {
            phonePreference.setTitle(R.string.pref_account_add_phone_title);
            phonePreference.setSummary("");
        } else {
            phonePreference.setTitle(myPhone);
            phonePreference.setSummary(R.string.pref_account_phone_title);
        }
    }

    @Override
    public void onPhoneUpdateFailed(String myPhone, int errorCode, String message, String label) {}

    @Override
    public void onMyEmailAndPasswordHasChanged(String myEmail) {
        onMyEmailHasChanged(myEmail, getStoreFactory().getProfileStore().isEmailVerified());
    }

    @Override
    public void onVerifyEmail(String email) {
        getChildFragmentManager().popBackStack(AddEmailAndPasswordPreferenceDialogFragment.TAG,
                                               FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getChildFragmentManager().popBackStack(ChangeEmailPreferenceDialogFragment.TAG,
                                               FragmentManager.POP_BACK_STACK_INCLUSIVE);
        verifyEmail(email);
    }

    @Override
    public void onEmailCleared() {
        getChildFragmentManager().popBackStack(ChangeEmailPreferenceDialogFragment.TAG,
                                               FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onVerifyPhone(String phoneNumber) {
        getChildFragmentManager().popBackStack(AddPhoneNumberPreferenceDialogFragment.TAG,
                                               FragmentManager.POP_BACK_STACK_INCLUSIVE);
        verifyPhoneNumber(phoneNumber);
    }

    @Override
    public void onPhoneNumberCleared() {
        getChildFragmentManager().popBackStack(AddPhoneNumberPreferenceDialogFragment.TAG,
                                               FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void signOut() {
        ViewUtils.showAlertDialog(getActivity(),
                                  null,
                                  getString(R.string.pref_account_sign_out_warning_message),
                                  getString(R.string.pref_account_sign_out_warning_verify),
                                  getString(R.string.pref_account_sign_out_warning_cancel),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          // TODO: Remove old SignOut event https://wearezeta.atlassian.net/browse/AN-4232
                                          ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SignOut());
                                          ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new LoggedOutEvent());
                                          getControllerFactory().getUsernameController().logout();
                                          getStoreFactory().getZMessagingApiStore().logout();
                                      }
                                  },
                                  null);
    }

    private void deleteAccount() {
        final String email = (String) emailPreference.getTitle();
        final String phone = (String) phonePreference.getTitle();
        final String message;
        if (getString(R.string.pref_account_add_email_title).equals(email)) {
            message = getString(R.string.pref_account_delete_warning_message_sms, phone);
        } else {
            message = getString(R.string.pref_account_delete_warning_message_email, email);
        }
        ViewUtils.showAlertDialog(getActivity(),
                                  getString(R.string.pref_account_delete_warning_title),
                                  TextViewUtils.getBoldText(getActivity(), message),
                                  getString(R.string.pref_account_delete_warning_verify),
                                  getString(R.string.pref_account_delete_warning_cancel),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          getStoreFactory().getZMessagingApiStore().delete();
                                      }
                                  },
                                  null);
    }

    private void verifyPhoneNumber(String phoneNumber) {
        getChildFragmentManager().beginTransaction()
                                 .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                 .add(VerifyPhoneNumberPreferenceFragment.newInstance(phoneNumber),
                                      VerifyPhoneNumberPreferenceFragment.TAG)
                                 .addToBackStack(VerifyPhoneNumberPreferenceFragment.TAG)
                                 .commit();
    }

    private void verifyEmail(String email) {
        getChildFragmentManager().beginTransaction()
                                 .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                 .add(VerifyEmailPreferenceFragment.newInstance(email),
                                      VerifyEmailPreferenceFragment.TAG)
                                 .addToBackStack(VerifyEmailPreferenceFragment.TAG)
                                 .commit();
    }

    private void addPhoneNumber() {
        getChildFragmentManager().beginTransaction()
                                 .add(AddPhoneNumberPreferenceDialogFragment.newInstance(),
                                      AddPhoneNumberPreferenceDialogFragment.TAG)
                                 .addToBackStack(AddPhoneNumberPreferenceDialogFragment.TAG)
                                 .commit();
    }

    @Override
    public void changePhoneNumber(String phoneNumber) {
        getChildFragmentManager().beginTransaction()
                                 .add(AddPhoneNumberPreferenceDialogFragment.newInstance(phoneNumber),
                                      AddPhoneNumberPreferenceDialogFragment.TAG)
                                 .addToBackStack(AddPhoneNumberPreferenceDialogFragment.TAG)
                                 .commit();
    }

    @Override
    public void changeEmail(String email) {
        getChildFragmentManager().beginTransaction()
                                 .add(ChangeEmailPreferenceDialogFragment.newInstance(email),
                                      ChangeEmailPreferenceDialogFragment.TAG)
                                 .addToBackStack(ChangeEmailPreferenceDialogFragment.TAG)
                                 .commit();
    }

    private void addEmailAndPassword() {
        getChildFragmentManager().beginTransaction()
                                 .add(AddEmailAndPasswordPreferenceDialogFragment.newInstance(),
                                      AddEmailAndPasswordPreferenceDialogFragment.TAG)
                                 .addToBackStack(AddEmailAndPasswordPreferenceDialogFragment.TAG)
                                 .commit();
    }

    private void changeUsername(String currentUsername, boolean cancellable) {
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new EditedUsernameEvent());
        getChildFragmentManager().beginTransaction()
                                 .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                                 .add(ChangeUsernamePreferenceDialogFragment.newInstance(currentUsername, cancellable),
                                      ChangeUsernamePreferenceDialogFragment.TAG)
                                 .addToBackStack(ChangeUsernamePreferenceDialogFragment.TAG)
                                 .commit();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        colorPreference.setAccentColor(color);
    }

    @Override
    public void onMyUsernameHasChanged(String myUsername) {
        if (usernamePreference == null) {
            return;
        }
        if (TextUtils.isEmpty(myUsername)) {
            usernamePreference.setTitle(getString(R.string.pref_account_username_empty_title));
            usernamePreference.setSummary("");
        } else {
            usernamePreference.setTitle(StringUtils.formatHandle(myUsername));
            usernamePreference.setSummary(getString(R.string.pref_account_username_title));
        }
    }

    @Override
    public void onUsernameChanged(String username) {
        onMyUsernameHasChanged(username);
    }
}
