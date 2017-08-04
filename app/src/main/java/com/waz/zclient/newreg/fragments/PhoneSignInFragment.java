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

import android.Manifest;
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
import com.waz.zclient.core.controllers.tracking.events.session.EnteredLoginPhoneEvent;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.newreg.fragments.country.Country;
import com.waz.zclient.newreg.fragments.country.CountryController;
import com.waz.zclient.newreg.utils.AppEntryUtil;
import com.waz.zclient.newreg.views.PhoneConfirmationButton;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.text.TypefaceEditText;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.tab.TabIndicatorLayout;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.ViewUtils;

public class PhoneSignInFragment extends BaseFragment<PhoneSignInFragment.Container> implements TextWatcher,
                                                                                                CountryController.Observer,
                                                                                                View.OnClickListener,
                                                                                                TabIndicatorLayout.Callback {
    public static final String TAG = PhoneSignInFragment.class.getName();

    private static final String[] GET_PHONE_NUMBER_PERMISSIONS = new String[] {Manifest.permission.READ_PHONE_STATE};

    private TextView textViewCountryName;
    private TextView textViewCountryCode;
    private TypefaceEditText editTextPhone;
    private PhoneConfirmationButton phoneConfirmationButton;
    private View buttonCountryChooser;
    private CountryController countryController;
    private TextView zetaButtonGotToEmailSignIn;
    private TabIndicatorLayout tabIndicatorLayout;
    private View closeAddAccountButton;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(PhoneSignInFragment.this.getActivity(),
                                         appEntryError,
                                         new AppEntryUtil.ErrorDialogCallback() {
                                             @Override
                                             public void onOk() {
                                                 if (editTextPhone == null) {
                                                     return;
                                                 }
                                                 KeyboardUtils.showKeyboard(getActivity());
                                                 editTextPhone.requestFocus();
                                                 phoneConfirmationButton.setState(PhoneConfirmationButton.State.INVALID);
                                             }
                                         });
        }
    };

    public static Fragment newInstance() {
        return new PhoneSignInFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_signin__with_phone, viewGroup, false);
        closeAddAccountButton = ViewUtils.getView(view, R.id.close_add_account);
        zetaButtonGotToEmailSignIn = ViewUtils.getView(view, R.id.ttv__new_reg__sign_in__go_to__email);
        phoneConfirmationButton = ViewUtils.getView(view, R.id.pcb__signin__phone);
        textViewCountryName = ViewUtils.getView(view, R.id.ttv_new_reg__signup__phone__country_name);
        textViewCountryCode = ViewUtils.getView(view, R.id.tv__country_code);
        editTextPhone = ViewUtils.getView(view, R.id.et__reg__phone);
        buttonCountryChooser = ViewUtils.getView(view, R.id.ll__signup__country_code__button);
        tabIndicatorLayout = ViewUtils.getView(view, R.id.til__app_entry);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        countryController = getContainer().getCountryController();

        editTextPhone.setText(getStoreFactory().appEntryStore().getPhone());
        Country country = countryController.getCountryFromCode(getStoreFactory().appEntryStore().getCountryCode());
        if (country != null) {
            onCountryHasChanged(country);
        }
        tabIndicatorLayout.setLabels(new int[] {R.string.new_reg__phone_signup__create_account, R.string.i_have_an_account});
        tabIndicatorLayout.setSelected(TabPages.SIGN_IN);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            tabIndicatorLayout.setTextColor(getResources().getColorStateList(R.color.wire__text_color_dark_selector));
        } else {
            tabIndicatorLayout.setTextColor(getResources().getColorStateList(R.color.wire__text_color_dark_selector, getContext().getTheme()));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (PermissionUtils.hasSelfPermissions(getActivity(), GET_PHONE_NUMBER_PERMISSIONS)) {
            setSimPhoneNumber();
        }
        onAccentColorHasChanged(getContainer().getAccentColor());

        editTextPhone.requestFocus();

        countryController.addObserver(this);

        Country country = countryController.getCountryFromCode(getStoreFactory().appEntryStore().getCountryCode());
        String phone = getStoreFactory().appEntryStore().getPhone();

        if (phone != null) {
            editTextPhone.setText(phone);
            editTextPhone.setSelection(phone.length());
            updatePhoneInputControls(phone);
        }
        if (country != null) {
            countryController.setCountry(country);
        }
    }

    private void setSimPhoneNumber() {
        final String abbreviation = getControllerFactory().getDeviceUserController().getPhoneCountryISO();
        final String countryCode = countryController.getCodeForAbbreviation(abbreviation);
        editTextPhone.setText(getControllerFactory().getDeviceUserController().getPhoneNumber(countryCode));
    }

    @Override
    public void onResume() {
        super.onResume();
        tabIndicatorLayout.setCallback(this);

        zetaButtonGotToEmailSignIn.setOnClickListener(this);
        buttonCountryChooser.setOnClickListener(this);
        editTextPhone.addTextChangedListener(this);
        phoneConfirmationButton.setOnClickListener(this);
        textViewCountryCode.setOnClickListener(this);
        closeAddAccountButton.setOnClickListener(this);
        closeAddAccountButton.setVisibility(ZMessaging.currentAccounts().hasLoggedInAccount() ? View.VISIBLE : View.GONE);

        editTextPhone.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    confirmPhoneNumber();
                    return true;
                } else {
                    return false;
                }
            }
        });
        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());
    }

    @Override
    public void onPause() {
        zetaButtonGotToEmailSignIn.setOnClickListener(null);
        buttonCountryChooser.setOnClickListener(null);
        editTextPhone.removeTextChangedListener(this);
        phoneConfirmationButton.setOnClickListener(null);
        textViewCountryCode.setOnClickListener(null);
        tabIndicatorLayout.setCallback(null);
        closeAddAccountButton.setOnClickListener(null);

        super.onPause();
    }

    @Override
    public void onStop() {
        countryController.removeObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        textViewCountryName = null;
        textViewCountryCode = null;
        closeAddAccountButton = null;
        editTextPhone = null;
        phoneConfirmationButton = null;
        zetaButtonGotToEmailSignIn = null;
        super.onDestroyView();
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    public void onAccentColorHasChanged(int color) {
        editTextPhone.setAccentColor(color);
        phoneConfirmationButton.setAccentColor(color);
    }

    private void goBack() {
        getStoreFactory().appEntryStore().onBackPressed();
    }

    private void confirmPhoneNumber() {
        if (getContainer() == null || editTextPhone == null) {
            return;
        }
        // before loging in show loader and dismiss keyboard
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new EnteredLoginPhoneEvent());
        getStoreFactory().appEntryStore().setSignInPhone(textViewCountryCode.getText().toString(),
                                                            editTextPhone.getText().toString(),
                                                            errorCallback);
    }

    private void openEmailSignIn() {
        getStoreFactory().appEntryStore().setState(AppEntryState.EMAIL_SIGN_IN);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.close_add_account:
                if (getContainer() != null) {
                    getContainer().abortAddAccount();
                }
                break;
            case R.id.tv__country_code:
            case R.id.ll__signup__country_code__button:
                getContainer().openCountryBox();
                break;
            case R.id.pcb__signin__phone:
                confirmPhoneNumber();
                break;
            case R.id.ttv__new_reg__sign_in__go_to__email:
                openEmailSignIn();
                break;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        updatePhoneInputControls(charSequence);
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    private void updatePhoneInputControls(CharSequence charSequence) {
        phoneConfirmationButton.setState(validatePhoneNumber(charSequence.toString()));
    }

    private PhoneConfirmationButton.State validatePhoneNumber(String number) {
        if (number.length() > 0) {
            return PhoneConfirmationButton.State.CONFIRM;
        } else {
            return PhoneConfirmationButton.State.NONE;
        }
    }

    @Override
    public void onCountryHasChanged(Country country) {
        textViewCountryCode.setText(String.format("+%s", country.getCountryCode()));
        textViewCountryName.setText(country.getName());
        updatePhoneInputControls(editTextPhone.getText());
    }

    @Override
    public void onItemSelected(int pos) {
        if (pos == TabPages.CREATE_ACCOUNT) {
            goBack();
        }
    }

    public interface Container {

        void abortAddAccount();

        int getAccentColor();

        void openCountryBox();

        CountryController getCountryController();

        void enableProgress(boolean enabled);
    }
}
