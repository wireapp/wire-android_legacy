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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import com.waz.service.ZMessaging;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.events.profile.ResetPassword;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.newreg.views.PhoneConfirmationButton;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.profile.validator.EmailValidator;
import com.waz.zclient.pages.main.profile.validator.PasswordValidator;
import com.waz.zclient.pages.main.profile.views.GuidedEditText;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.tab.TabIndicatorLayout;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

public class EmailSignInFragment extends BaseFragment<EmailSignInFragment.Container> implements TextWatcher,
                                                                                                View.OnClickListener,
                                                                                                TabIndicatorLayout.Callback {
    public static final String TAG = EmailSignInFragment.class.getName();

    private static final String TET_EMAIL_VALUE_KEY = "email_value";
    private static final String TET_PASSWORD_VALUE_KEY = "password_value";

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private GuidedEditText guidedEditTextEmail;
    private GuidedEditText guidedEditTextPassword;
    private TextView textViewGoToPhoneSignIn;
    private TextView resetPasswordTextView;
    private PhoneConfirmationButton signInButton;
    private TabIndicatorLayout tabIndicatorLayout;
    private View closeAddAccountButton;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(EmailSignInFragment.this.getActivity(),
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
        return new EmailSignInFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_signin__with_email, viewGroup, false);
        guidedEditTextEmail = ViewUtils.getView(view, R.id.get__sign_in__email);
        guidedEditTextPassword = ViewUtils.getView(view, R.id.get__sign_in__password);
        resetPasswordTextView = ViewUtils.getView(view, R.id.ttv_signin_forgot_password);
        tabIndicatorLayout = ViewUtils.getView(view, R.id.til__app_entry);
        closeAddAccountButton = ViewUtils.getView(view, R.id.close_add_account);

        textViewGoToPhoneSignIn = ViewUtils.getView(view, R.id.ttv__new_reg__sign_in__go_to__phone);
        signInButton = ViewUtils.getView(view, R.id.pcb__signin__email);

        guidedEditTextEmail.setResource(R.layout.guided_edit_text_sign_in__email);
        guidedEditTextEmail.setValidator(EmailValidator.newInstanceAcceptingEverything());
        guidedEditTextEmail.getEditText().addTextChangedListener(this);

        guidedEditTextPassword.setResource(R.layout.guided_edit_text_sign_in__password);
        guidedEditTextPassword.setValidator(PasswordValidator.instanceLegacy(getActivity()));
        guidedEditTextPassword.getEditText().addTextChangedListener(this);
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

        // reset password
        resetPasswordTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().onOpenUrl(getResources().getString(R.string.url_password_reset));
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ResetPassword(ResetPassword.Location.FROM_SIGN_IN));
            }
        });

        return view;
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tabIndicatorLayout.setLabels(new int[]{R.string.new_reg__phone_signup__create_account, R.string.i_have_an_account});
        tabIndicatorLayout.setSelected(TabPages.SIGN_IN);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            tabIndicatorLayout.setTextColor(getResources().getColorStateList(R.color.wire__text_color_dark_selector));
        } else {
            tabIndicatorLayout.setTextColor(getResources().getColorStateList(R.color.wire__text_color_dark_selector,
                                                                             getContext().getTheme()));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (guidedEditTextEmail == null) {
            return;
        }
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
        onAccentColorHasChanged(getContainer().getAccentColor());
    }

    @Override
    public void onResume() {
        super.onResume();
        textViewGoToPhoneSignIn.setOnClickListener(this);
        resetPasswordTextView.setOnClickListener(this);
        signInButton.setOnClickListener(this);
        closeAddAccountButton.setOnClickListener(this);
        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());
        tabIndicatorLayout.setCallback(this);
        closeAddAccountButton.setVisibility(ZMessaging.currentAccounts().hasLoggedInAccount() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPause() {
        tabIndicatorLayout.setCallback(null);
        textViewGoToPhoneSignIn.setOnClickListener(null);
        resetPasswordTextView.setOnClickListener(null);
        closeAddAccountButton.setOnClickListener(null);
        signInButton.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        textViewGoToPhoneSignIn = null;
        guidedEditTextPassword.getEditText().removeTextChangedListener(this);
        guidedEditTextEmail.getEditText().removeTextChangedListener(this);
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
            case R.id.close_add_account:
                if (getContainer() != null) {
                    getContainer().abortAddAccount();
                }
                break;
            case R.id.ttv_signin_forgot_password:
                onPasswordResetButtonClicked();
                break;
            case R.id.ttv__new_reg__sign_in__go_to__phone:
                goToSignInPhone();
                break;
            case R.id.pcb__signin__email:
                signIn();
                break;
        }
    }

    private void signIn() {
        getControllerFactory().getLoadTimeLoggerController().loginPressed();
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());
        getStoreFactory().appEntryStore()
                         .signInWithEmail(guidedEditTextEmail.getText(),
                                          guidedEditTextPassword.getText(),
                                          errorCallback);
    }

    private void goBack() {
        getStoreFactory().appEntryStore().onBackPressed();
    }

    private void goToSignInPhone() {
        getStoreFactory().appEntryStore().setState(AppEntryState.PHONE_SIGN_IN);
    }

    private void onPasswordResetButtonClicked() {
        getContainer().onOpenUrl(getResources().getString(R.string.url_password_reset));
    }

    @Override
    public void onItemSelected(int pos) {
        if (pos == TabPages.CREATE_ACCOUNT) {
            if (LayoutSpec.isPhone(getActivity())) {
                goBack();
            } else {
                getStoreFactory().appEntryStore().setState(AppEntryState.EMAIL_REGISTER);
            }
        }
    }

    public interface Container {

        void onOpenUrl(String url);

        int getAccentColor();

        void enableProgress(boolean enabled);

        void abortAddAccount();
    }
}
