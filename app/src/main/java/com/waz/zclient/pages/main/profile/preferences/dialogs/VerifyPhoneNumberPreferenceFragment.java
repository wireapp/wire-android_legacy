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
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.api.KindOfAccess;
import com.waz.api.KindOfVerification;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.pages.main.profile.views.OnTextChangedListener;
import com.waz.zclient.ui.utils.TypefaceUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class VerifyPhoneNumberPreferenceFragment extends BaseDialogFragment<VerifyPhoneNumberPreferenceFragment.Container> {
    public static final String TAG = VerifyPhoneNumberPreferenceFragment.class.getSimpleName();
    private static final int CODE_LENGTH = 6;
    private static final String ARG_PHONE = "ARG_PHONE";

    private final char[] verificationCode = new char[CODE_LENGTH];
    private List<EditText> textBoxes;

    public static VerifyPhoneNumberPreferenceFragment newInstance(String phoneNumber) {
        final VerifyPhoneNumberPreferenceFragment fragment = new VerifyPhoneNumberPreferenceFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_PHONE, phoneNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            for (int i = 0; i < verificationCode.length; i++) {
                verificationCode[i] = ' ';
            }
        }

        setStyle(STYLE_NO_FRAME, R.style.Theme_Dark_Preferences);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                                                 WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        super.onViewCreated(view, savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final String number = PhoneNumberUtils.formatNumber(getArguments().getString(ARG_PHONE, ""));
        final View view = inflater.inflate(R.layout.fragment_preference_phone_number_verification, container, false);

        final TextInputLayout verificationCodeInputLayout = ViewUtils.getView(view, R.id.til__verification_code);
        verificationCodeInputLayout.setErrorEnabled(true);
        final View backButton = ViewUtils.getView(view, R.id.tv__back_button);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        final View okButton = ViewUtils.getView(view, R.id.tv__ok_button);
        okButton.setEnabled(false);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String code = getVerificationCode();
                getStoreFactory().profileStore()
                                 .submitCode(number, code, new ZMessagingApi.PhoneNumberVerificationListener() {
                                         @Override
                                         public void onVerified(KindOfVerification kindOfVerification) {
                                             dismiss();
                                         }

                                         @Override
                                         public void onVerificationFailed(KindOfVerification kindOfVerification,
                                                                          int errorCode,
                                                                          String message,
                                                                          String label) {
                                             if (AppEntryError.PHONE_INVALID_REGISTRATION_CODE.correspondsTo(errorCode, label)) {
                                                verificationCodeInputLayout.setError(getString(AppEntryError.PHONE_INVALID_REGISTRATION_CODE.headerResource));
                                             } else {
                                                verificationCodeInputLayout.setError(getString(AppEntryError.PHONE_REGISTER_GENERIC_ERROR.headerResource));
                                             }
                                         }
                                     });
            }
        });
        final TextView changeNumberButton = ViewUtils.getView(view, R.id.tv__change_number_button);
        changeNumberButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getContainer() == null) {
                    return;
                }
                dismiss();
            }
        });
        final TextView resendButton = ViewUtils.getView(view, R.id.tv__resend_button);
        resendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resendButton.animate().alpha(0f).start();
                getStoreFactory().profileStore()
                                 .resendPhoneVerificationCode(number, new ZMessagingApi.PhoneConfirmationCodeRequestListener() {

                                     @Override
                                     public void onConfirmationCodeSent(KindOfAccess kindOfAccess) {
                                         resendButton.animate().alpha(1f).start();
                                     }

                                     @Override
                                     public void onPasswordExists(KindOfAccess kindOfAccess) {

                                     }

                                     @Override
                                     public void onConfirmationCodeSendingFailed(KindOfAccess kindOfAccess,
                                                                                 int code,
                                                                                 String message,
                                                                                 String label) {
                                         resendButton.animate().alpha(1f).start();
                                         AppEntryUtil.showErrorDialog(getActivity(), AppEntryError.PHONE_REGISTER_GENERIC_ERROR, null);
                                     }
                                 });
            }
        });
        final TextView verificationDescription = ViewUtils.getView(view, R.id.tv__verification_description);
        verificationDescription.setText(getString(R.string.pref__account_action__phone_verification__description, number));

        final EditText firstNumberEditText = ViewUtils.getView(view, R.id.et__verification_code__1);
        final EditText secondNumberEditText = ViewUtils.getView(view, R.id.et__verification_code__2);
        final EditText thirdNumberEditText = ViewUtils.getView(view, R.id.et__verification_code__3);
        final EditText fourthNumberEditText = ViewUtils.getView(view, R.id.et__verification_code__4);
        final EditText fifthNumberEditText = ViewUtils.getView(view, R.id.et__verification_code__5);
        final EditText sixthNumberEditText = ViewUtils.getView(view, R.id.et__verification_code__6);

        textBoxes = new LinkedList<>(Arrays.asList(firstNumberEditText,
                                                   secondNumberEditText,
                                                   thirdNumberEditText,
                                                   fourthNumberEditText,
                                                   fifthNumberEditText,
                                                   sixthNumberEditText));
        for (int i = 0; i < textBoxes.size(); i++) {
            final EditText textBox = textBoxes.get(i);
            final int finalI = i;
            textBox.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__bold)));
            textBox.addTextChangedListener(new OnTextChangedListener() {
                @Override
                public void afterTextChanged(Editable s) {
                    final String val = textBox.getText().toString().trim();
                    final char c;
                    if (TextUtils.isEmpty(val)) {
                        c = ' ';
                    } else {
                        c = val.charAt(0);
                    }
                    verificationCode[finalI] = c;
                    final boolean wasDelete = c == ' ';
                    if (!jumpToNextEmptyTextBox(wasDelete)) {
                        okButton.setEnabled(true);
                        if (finalI == textBoxes.size()) {
                            okButton.requestFocus();
                        }
                    } else {
                        okButton.setEnabled(false);
                    }
                }
            });
        }

        return view;
    }

    private String getVerificationCode() {
        return new String(verificationCode);
    }

    private boolean jumpToNextEmptyTextBox(boolean wasDelete) {
        for (int i = 0; i < verificationCode.length; i++) {
            if (verificationCode[i] == ' ') {
                if (wasDelete && i > 0) {
                    textBoxes.get(i - 1).requestFocus();
                } else {
                    textBoxes.get(i).requestFocus();
                }
                return true;
            }
        }
        return false;
    }

    public interface Container {
    }
}
