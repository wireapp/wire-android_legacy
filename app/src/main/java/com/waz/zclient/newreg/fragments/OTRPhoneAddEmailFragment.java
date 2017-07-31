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
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.newreg.views.PhoneConfirmationButton;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.profile.validator.EmailValidator;
import com.waz.zclient.pages.main.profile.validator.PasswordValidator;
import com.waz.zclient.pages.main.profile.views.GuidedEditText;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.ViewUtils;

public class OTRPhoneAddEmailFragment extends BaseFragment<OTRPhoneAddEmailFragment.Container> implements TextWatcher,
                                                                                                          View.OnClickListener {
    public static final String TAG = OTRPhoneAddEmailFragment.class.getName();

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private GuidedEditText guidedEditTextEmail;
    private GuidedEditText guidedEditTextPassword;
    private PhoneConfirmationButton phoneConfirmationButton;
    private TypefaceTextView passwordLengthMessage;
    private boolean passwordWasValid = true;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(OTRPhoneAddEmailFragment.this.getActivity(),
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
        return new OTRPhoneAddEmailFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_otr_add_email, viewGroup, false);

        guidedEditTextEmail = ViewUtils.getView(view, R.id.get__sign_in__email);
        guidedEditTextPassword = ViewUtils.getView(view, R.id.get__sign_in__password);
        phoneConfirmationButton = ViewUtils.getView(view, R.id.pcb__signin__email);
        passwordLengthMessage = ViewUtils.getView(view, R.id.ttv__signin_add_email__pw_length);
        ViewUtils.getView(view, R.id.gtv__not_now__close).setVisibility(View.GONE);

        guidedEditTextEmail.setResource(R.layout.guided_edit_text_sign_in__email);
        guidedEditTextEmail.setValidator(EmailValidator.newInstanceAcceptingEverything());
        guidedEditTextEmail.getEditText().addTextChangedListener(this);

        guidedEditTextPassword.setResource(R.layout.guided_edit_text_sign_in__password);
        guidedEditTextPassword.setValidator(PasswordValidator.instance(getActivity()));
        guidedEditTextPassword.getEditText().addTextChangedListener(this);

        guidedEditTextPassword.getEditText().setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                passwordLengthMessage.setAlpha(hasFocus ? 1f : 0f);
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordLengthMessage.setAlpha(0f);
        passwordLengthMessage.setText(getResources().getString(R.string.new_reg__password_length,
                                                               getResources().getInteger(R.integer.password_validator__min_password_length)));


        guidedEditTextEmail.setText(getStoreFactory().appEntryStore().getEmail());
        guidedEditTextPassword.setText(getStoreFactory().appEntryStore().getPassword());
    }

    @Override
    public void onStart() {
        super.onStart();
        guidedEditTextEmail.getEditText().requestFocus();
        onAccentColorHasChanged(getContainer().getAccentColor());

        getControllerFactory().getVerificationController().finishVerification();
        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());
        updateState();
    }


    @Override
    public void onResume() {
        super.onResume();
        phoneConfirmationButton.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        phoneConfirmationButton.setOnClickListener(null);
        KeyboardUtils.hideKeyboard(getActivity());
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        guidedEditTextPassword.getEditText().removeTextChangedListener(this);
        guidedEditTextEmail.getEditText().removeTextChangedListener(this);
        guidedEditTextEmail = null;
        guidedEditTextPassword = null;
        super.onDestroyView();
    }

    private boolean areEmailAndPasswordValid() {
        return guidedEditTextEmail.onlyValidate() && guidedEditTextPassword.onlyValidate();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        updateState();

        boolean passwordIsValid = guidedEditTextPassword.onlyValidate();

        if (passwordIsValid  == passwordWasValid) {
            return;
        }
        if (!passwordIsValid && guidedEditTextPassword.hasFocus()) {
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
        passwordWasValid = passwordIsValid;
    }

    @Override
    public void afterTextChanged(Editable editable) {

    }

    private void updateState() {
        if (areEmailAndPasswordValid()) {
            phoneConfirmationButton.setState(PhoneConfirmationButton.State.CONFIRM);
        } else {
            phoneConfirmationButton.setState(PhoneConfirmationButton.State.NONE);
        }
    }

    public void onAccentColorHasChanged(int color) {
        guidedEditTextEmail.setAccentColor(color);
        guidedEditTextPassword.setAccentColor(color);
        phoneConfirmationButton.setAccentColor(color);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.pcb__signin__email:
                addEmail();
                break;
        }
    }

    private void addEmail() {
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());

        getStoreFactory().appEntryStore()
                         .addEmailAndPasswordToPhone(guidedEditTextEmail.getText(),
                                                     guidedEditTextPassword.getText(),
                                                     errorCallback,
                                                     errorCallback);
    }

    public interface Container {

        void onOpenUrl(String url);

        int getAccentColor();

        void enableProgress(boolean enabled);
    }
}
