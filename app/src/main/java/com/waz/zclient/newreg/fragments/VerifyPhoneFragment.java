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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.newreg.views.PhoneConfirmationButton;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.text.TypefaceEditText;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.ViewUtils;

public class VerifyPhoneFragment extends BaseFragment<VerifyPhoneFragment.Container> implements View.OnClickListener,
                                                                                                TextWatcher {
    public static final String TAG = VerifyPhoneFragment.class.getName();
    private static final String ARG_SHOW_NOT_NOW = "ARG_SHOW_NOT_NOW";
    private static final int SHOW_RESEND_CODE_BUTTON_DELAY = 15000;
    private static final int RESEND_CODE_TIMER_INTERVAL = 1000;

    public static VerifyPhoneFragment newInstance(boolean showNotNowButton) {
        VerifyPhoneFragment fragment = new VerifyPhoneFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SHOW_NOT_NOW, showNotNowButton);
        fragment.setArguments(args);
        return fragment;
    }

    private TextView resendCodeButton;
    private TextView resendCodeTimer;
    private View resendCodeCallButton;
    private TypefaceEditText editTextCode;
    private PhoneConfirmationButton phoneConfirmationButton;
    private View buttonBack;
    private TextView textViewInfo;
    private TextView textViewNotNow;
    private int phoneVerificationCodeMinLength;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(VerifyPhoneFragment.this.getActivity(),
                                         appEntryError,
                                         new AppEntryUtil.ErrorDialogCallback() {
                                             @Override
                                             public void onOk() {
                                                 if (editTextCode == null) {
                                                     return;
                                                 }
                                                 KeyboardUtils.showKeyboard(getActivity());
                                                 editTextCode.requestFocus();
                                                 phoneConfirmationButton.setState(PhoneConfirmationButton.State.INVALID);
                                             }
                                         });
        }
    };
    private IAppEntryStore.SuccessCallback resendSuccessCallback;
    private IAppEntryStore.ErrorCallback resendFailedCallback;

    private IAppEntryStore.SuccessCallback resendCallSuccessCallback;
    private IAppEntryStore.ErrorCallback resendCallFailedCallback;

    private int milliSecondsToShowResendButton;
    private Handler resendCodeTimerHandler;
    private Runnable resendCodeTimerRunnable = new Runnable() {
        @Override
        public void run() {
            milliSecondsToShowResendButton = milliSecondsToShowResendButton - RESEND_CODE_TIMER_INTERVAL;
            if (milliSecondsToShowResendButton <= 0) {
                resendCodeTimer.setVisibility(View.GONE);
                resendCodeButton.setVisibility(View.VISIBLE);
                resendCodeCallButton.setVisibility(View.VISIBLE);
                return;
            }
            int sec = (milliSecondsToShowResendButton / 1000);
            resendCodeTimer.setText(getResources().getQuantityString(R.plurals.welcome__resend__timer_label, sec, sec));
            resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, RESEND_CODE_TIMER_INTERVAL);
        }
    };

    @Override
    @SuppressLint("NewApi")
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_phone__activation, container, false);

        // get references
        editTextCode = ViewUtils.getView(view, R.id.et__reg__code);
        resendCodeCallButton = ViewUtils.getView(view, R.id.ttv__call_me_button);
        resendCodeButton = ViewUtils.getView(view, R.id.ttv__resend_button);
        resendCodeTimer = ViewUtils.getView(view, R.id.ttv__resend_timer);
        phoneConfirmationButton = ViewUtils.getView(view, R.id.pcb__activate);
        buttonBack = ViewUtils.getView(view, R.id.ll__activation_button__back);
        textViewInfo = ViewUtils.getView(view, R.id.ttv__info_text);
        textViewNotNow = ViewUtils.getView(view, R.id.ttv__not_now);
        ViewUtils.getView(view, R.id.fl__confirmation_checkmark).setVisibility(View.GONE);
        ViewUtils.getView(view, R.id.gtv__not_now__close).setVisibility(View.GONE);
        phoneVerificationCodeMinLength = getResources().getInteger(R.integer.new_reg__phone_verification_code__min_length);

        resendCodeTimerHandler = new Handler();
        resendCodeButton.setVisibility(View.GONE);
        resendCodeCallButton.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            editTextCode.setLetterSpacing(1);
        }

        resendSuccessCallback = new IAppEntryStore.SuccessCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(getActivity(),
                               getResources().getString(R.string.new_reg__code_resent),
                               Toast.LENGTH_LONG).show();
            }
        };

        resendFailedCallback = new IAppEntryStore.ErrorCallback() {
            @Override
            public void onError(AppEntryError appEntryError) {
                Toast.makeText(getActivity(),
                               appEntryError.errorCode,
                               Toast.LENGTH_LONG)
                     .show();
            }
        };

        resendCallSuccessCallback = new IAppEntryStore.SuccessCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(getActivity(),
                               getResources().getString(R.string.new_reg__code_resent__call),
                               Toast.LENGTH_LONG).show();
            }
        };

        resendCallFailedCallback = new IAppEntryStore.ErrorCallback() {
            @Override
            public void onError(AppEntryError appEntryError) {
                Toast.makeText(getActivity(),
                               appEntryError.headerResource,
                               Toast.LENGTH_LONG)
                     .show();
            }
        };

        if (getArguments().getBoolean(ARG_SHOW_NOT_NOW)) {
            textViewNotNow.setVisibility(View.VISIBLE);
        } else {
            textViewNotNow.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        editTextCode.requestFocus();
        onAccentColorHasChanged(getContainer().getAccentColor());

        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());

        final String controllerCode = getControllerFactory().getVerificationController().getVerificationCode();
        getControllerFactory().getVerificationController().startVerification();
        if (!TextUtils.isEmpty(controllerCode) &&
            !controllerCode.equals(editTextCode.getText().toString())) {
            editTextCode.setText(controllerCode);
            confirmCode();
        }

        startResendCodeTimer();
    }

    @Override
    public void onResume() {
        super.onResume();

        phoneConfirmationButton.setOnClickListener(this);
        resendCodeButton.setOnClickListener(this);
        buttonBack.setOnClickListener(this);
        editTextCode.addTextChangedListener(this);
        textViewNotNow.setOnClickListener(this);
        resendCodeCallButton.setOnClickListener(this);
        onPhoneNumberLoaded(String.format("%s %s",
                                          getStoreFactory().appEntryStore().getCountryCode(),
                                          getStoreFactory().appEntryStore().getPhone()));
    }

    @Override
    public void onPause() {
        phoneConfirmationButton.setOnClickListener(null);
        resendCodeButton.setOnClickListener(null);
        buttonBack.setOnClickListener(null);
        editTextCode.removeTextChangedListener(this);
        textViewNotNow.setOnClickListener(null);
        resendCodeCallButton.setOnClickListener(null);

        super.onPause();
    }

    @Override
    public void onStop() {
        resendCodeTimerHandler.removeCallbacks(resendCodeTimerRunnable);
        getControllerFactory().getVerificationController().finishVerification();
        KeyboardUtils.hideKeyboard(getActivity());
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        editTextCode = null;
        resendCodeButton = null;
        phoneConfirmationButton = null;
        buttonBack = null;
        textViewInfo = null;
        super.onDestroyView();
    }

    private void onPhoneNumberLoaded(String phone) {
        String text = String.format(getResources().getString(R.string.activation_code_info_manual), phone);
        textViewInfo.setText(Html.fromHtml(text));
    }


    private void resendPhoneNumber() {
        editTextCode.setText("");
        getStoreFactory().appEntryStore()
                         .resendPhone(resendSuccessCallback,
                                      resendFailedCallback);
    }

    private void goBack() {
        getStoreFactory().appEntryStore().onBackPressed();
    }

    private void confirmCode() {
        getControllerFactory().getLoadTimeLoggerController().loginPressed();
        // before logging in show loader and dismiss keyboard
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());

        String code = editTextCode.getText().toString();
        getStoreFactory().appEntryStore()
                         .submitCode(code,
                                     errorCallback);
    }

    private void onNotNow() {
        getStoreFactory().appEntryStore().setState(AppEntryState.LOGGED_IN);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ll__activation_button__back:
                goBack();
                break;
            case R.id.ttv__resend_button:
                resendPhoneNumber();
                startResendCodeTimer();
                break;
            case R.id.pcb__activate:
                confirmCode();
                break;
            case R.id.ttv__not_now:
                onNotNow();
                break;
            case R.id.ttv__call_me_button:
                getStoreFactory().appEntryStore().triggerVerificationCodeCallToUser(resendCallSuccessCallback, resendCallFailedCallback);
                startResendCodeTimer();
                break;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        phoneConfirmationButton.setState(validatePhoneNumber(charSequence.toString()));
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    private PhoneConfirmationButton.State validatePhoneNumber(String number) {
        if (number.length() == phoneVerificationCodeMinLength) {
            return PhoneConfirmationButton.State.CONFIRM;
        } else {
            return PhoneConfirmationButton.State.NONE;
        }
    }


    public void onAccentColorHasChanged(int color) {
        editTextCode.setAccentColor(color);
        phoneConfirmationButton.setAccentColor(color);
        resendCodeButton.setTextColor(color);
    }

    private void startResendCodeTimer() {
        milliSecondsToShowResendButton = SHOW_RESEND_CODE_BUTTON_DELAY;
        resendCodeButton.setVisibility(View.GONE);
        resendCodeCallButton.setVisibility(View.GONE);
        resendCodeTimer.setVisibility(View.VISIBLE);
        int sec = (milliSecondsToShowResendButton / 1000);
        resendCodeTimer.setText(getResources().getQuantityString(R.plurals.welcome__resend__timer_label, sec, sec));
        resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, RESEND_CODE_TIMER_INTERVAL);
    }

    public interface Container {
        int getAccentColor();

        void enableProgress(boolean enabled);
    }
}
