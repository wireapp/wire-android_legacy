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
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
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
import com.waz.zclient.core.controllers.tracking.events.registration.PrefilledPhoneNumberEvent;
import com.waz.zclient.core.controllers.tracking.events.registration.ViewTOS;
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
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.tab.TabIndicatorLayout;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.ViewUtils;
import timber.log.Timber;

public class PhoneRegisterFragment extends BaseFragment<PhoneRegisterFragment.Container> implements TextWatcher,
                                                                                                    CountryController.Observer,
                                                                                                    View.OnClickListener,
                                                                                                    TabIndicatorLayout.Callback {
    public static final String TAG = PhoneRegisterFragment.class.getName();

    private static final String[] GET_PHONE_NUMBER_PERMISSIONS = new String[] {Manifest.permission.READ_PHONE_STATE};

    private TextView textViewCountryName;
    private TextView textViewCountryCode;
    private TextView textViewTermsOfService;
    private TypefaceEditText editTextPhone;
    private View closeAddAccountButton;
    private PhoneConfirmationButton phoneConfirmationButton;
    private TextView changeCountryView;
    private CountryController countryController;
    private TabIndicatorLayout tabIndicatorLayout;
    private TextView titleView;
    private View logoView;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(PhoneRegisterFragment.this.getActivity(),
                                         appEntryError,
                                         new AppEntryUtil.ErrorDialogCallback() {
                                             @Override
                                             public void onOk() {
                                                 if (phoneConfirmationButton == null) {
                                                     return;
                                                 }
                                                 KeyboardUtils.showKeyboard(getActivity());
                                                 phoneConfirmationButton.setState(PhoneConfirmationButton.State.INVALID);
                                             }
                                         });
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_phone__signup, container, false);

        textViewCountryName = ViewUtils.getView(view, R.id.ttv_new_reg__signup__phone__country_name);
        textViewCountryCode = ViewUtils.getView(view, R.id.tv__country_code);
        editTextPhone = ViewUtils.getView(view, R.id.et__reg__phone);
        textViewTermsOfService = ViewUtils.getView(view, R.id.tv__welcome__terms_of_service);
        closeAddAccountButton = ViewUtils.getView(view, R.id.close_add_account);
        phoneConfirmationButton = ViewUtils.getView(view, R.id.pcb__signup);
        changeCountryView = ViewUtils.getView(view, R.id.ttv_new_reg__signup__phone__change_country);
        tabIndicatorLayout = ViewUtils.getView(view, R.id.til__app_entry);
        titleView = ViewUtils.getView(view, R.id.tv__reg__title);
        logoView = ViewUtils.getView(view, R.id.iv__reg__logo);

        textViewTermsOfService.setVisibility(View.GONE);


        TextViewUtils.linkifyText(changeCountryView,
                                  ContextCompat.getColor(getActivity(), R.color.first_time__welcome__tos_color),
                                  false,
                                  new Runnable() {
                                      @Override
                                      public void run() {

                                      }
                                  });
        TextViewUtils.linkifyText(textViewTermsOfService,
                                  ContextCompat.getColor(getActivity(), R.color.text__primary_dark),
                                  true,
                                  new Runnable() {
                                      @Override
                                      public void run() {
                                      }
                                  });

        initializeTitleABTest();

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        countryController = getContainer().getCountryController();
        tabIndicatorLayout.setLabels(new int[] {R.string.new_reg__phone_signup__create_account, R.string.i_have_an_account});
        tabIndicatorLayout.setSelected(TabPages.CREATE_ACCOUNT);
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
        editTextPhone.requestFocus();
        onAccentColorHasChanged(getContainer().getAccentColor());

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
        final String number = getControllerFactory().getDeviceUserController().getPhoneNumber(countryCode);
        if (!TextUtils.isEmpty(number)) {
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new PrefilledPhoneNumberEvent());
        }
        editTextPhone.setText(number);
    }

    @Override
    public void onResume() {
        super.onResume();
        tabIndicatorLayout.setCallback(this);

        changeCountryView.setOnClickListener(this);
        editTextPhone.addTextChangedListener(this);
        closeAddAccountButton.setOnClickListener(this);
        closeAddAccountButton.setVisibility(ZMessaging.currentAccounts().hasLoggedInAccount() ? View.VISIBLE : View.GONE);
        phoneConfirmationButton.setOnClickListener(this);
        textViewCountryCode.setOnClickListener(this);
        textViewTermsOfService.setOnClickListener(this);
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
        textViewTermsOfService.setOnClickListener(null);
        changeCountryView.setOnClickListener(null);
        editTextPhone.removeTextChangedListener(this);
        closeAddAccountButton.setOnClickListener(null);
        phoneConfirmationButton.setOnClickListener(null);
        textViewCountryCode.setOnClickListener(null);
        editTextPhone.setOnEditorActionListener(null);
        tabIndicatorLayout.setCallback(null);

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
        editTextPhone = null;
        textViewTermsOfService = null;
        closeAddAccountButton = null;
        phoneConfirmationButton = null;
        super.onDestroyView();
    }

    private void confirmPhoneNumber() {
        // before loging in show loader and dismiss keyboard
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());

        getStoreFactory().appEntryStore()
                         .setRegistrationPhone(textViewCountryCode.getText().toString(),
                                               editTextPhone.getText().toString(),
                                               errorCallback);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv__country_code:
            case R.id.ttv_new_reg__signup__phone__change_country:
                getContainer().openCountryBox();
                break;
            case R.id.pcb__signup:
                confirmPhoneNumber();
                break;
            case R.id.close_add_account:
                if (getContainer() != null) {
                    getContainer().abortAddAccount();
                }
                break;
            case R.id.tv__welcome__terms_of_service:
                getContainer().onOpenUrlInApp(getString(R.string.url_terms_of_service), true);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ViewTOS(ViewTOS.Source.FROM_JOIN_PAGE));
                break;
        }
    }

    //  EditText callback /////////////////////////////////////////////////////////////

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        updatePhoneInputControls(charSequence);
        if (TextUtils.isEmpty(charSequence)) {
            textViewCountryName.setVisibility(View.VISIBLE);
            changeCountryView.setVisibility(View.VISIBLE);
            textViewTermsOfService.setVisibility(View.GONE);
        } else {
            textViewCountryName.setVisibility(View.GONE);
            changeCountryView.setVisibility(View.GONE);
            textViewTermsOfService.setVisibility(View.VISIBLE);
        }
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

    public void onAccentColorHasChanged(int color) {
        editTextPhone.setAccentColor(color);
        phoneConfirmationButton.setAccentColor(color);
    }

    @Override
    public void onItemSelected(int pos) {
        if (pos == TabPages.SIGN_IN) {
            getStoreFactory().appEntryStore().setState(AppEntryState.EMAIL_SIGN_IN);
            ViewUtils.fadeOutView(titleView);
        } else {
            ViewUtils.fadeInView(titleView);
        }
    }

    private void initializeTitleABTest() {
        final int largeScreenHeight = getResources().getDimensionPixelSize(R.dimen.new_reg__title_ab_test__hide_logo__screen_height);
        Timber.i("Title AB-test group %s", getControllerFactory().getUserPreferencesController().getABTestingGroup());
        // AB-test for title
        if (getControllerFactory().getUserPreferencesController().getABTestingGroup() == 1 ||
            getControllerFactory().getUserPreferencesController().getABTestingGroup() == 2) {
            // Control group
            titleView.setVisibility(View.GONE);

        } else if (getControllerFactory().getUserPreferencesController().getABTestingGroup() == 3 ||
                   getControllerFactory().getUserPreferencesController().getABTestingGroup() == 4) {
            // Test variant 1
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(getText(R.string.new_reg_phone__title__variant_1));
            if (ViewUtils.getOrientationIndependentDisplayHeight(getActivity()) < largeScreenHeight) {
                logoView.setVisibility(View.GONE);
            }

        } else if (getControllerFactory().getUserPreferencesController().getABTestingGroup() == 5 ||
                   getControllerFactory().getUserPreferencesController().getABTestingGroup() == 6) {
            // Test variant 2
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(getText(R.string.new_reg_phone__title__variant_2));
            if (ViewUtils.getOrientationIndependentDisplayHeight(getActivity()) < largeScreenHeight) {
                logoView.setVisibility(View.GONE);
            }
        }
    }

    public static Fragment newInstance() {
        return new PhoneRegisterFragment();
    }

    public interface Container {

        void abortAddAccount();

        void onOpenUrlInApp(String url, boolean withCloseButton);

        void enableProgress(boolean enabled);

        int getAccentColor();

        void openCountryBox();

        CountryController getCountryController();
    }
}
