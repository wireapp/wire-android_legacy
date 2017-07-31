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
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
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
import com.waz.zclient.utils.StringUtils;

public class PhoneSetNameFragment extends BaseFragment<PhoneSetNameFragment.Container> implements TextWatcher,
                                                                                                  View.OnClickListener {
    public static final String TAG = PhoneSetNameFragment.class.getName();

    private TypefaceEditText editTextName;
    private PhoneConfirmationButton nameConfirmationButton;

    private IAppEntryStore.ErrorCallback errorCallback = new IAppEntryStore.ErrorCallback() {
        @Override
        public void onError(AppEntryError appEntryError) {
            if (getContainer() == null) {
                return;
            }
            getContainer().enableProgress(false);

            AppEntryUtil.showErrorDialog(PhoneSetNameFragment.this.getActivity(),
                                         appEntryError,
                                         new AppEntryUtil.ErrorDialogCallback() {
                                             @Override
                                             public void onOk() {
                                                 if (getActivity() == null) {
                                                     return;
                                                 }
                                                 KeyboardUtils.showKeyboard(getActivity());
                                                 editTextName.requestFocus();
                                                 nameConfirmationButton.setState(PhoneConfirmationButton.State.INVALID);
                                             }
                                         });
        }
    };

    public static PhoneSetNameFragment newInstance() {
        PhoneSetNameFragment fragment = new PhoneSetNameFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_phone__name, container, false);

        // get references
        editTextName = ViewUtils.getView(view, R.id.et__reg__name);
        nameConfirmationButton = ViewUtils.getView(view, R.id.pcb__signup);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().appEntryStore().setState(AppEntryState.PHONE_SET_NAME);
        editTextName.requestFocus();
        onAccentColorHasChanged(getContainer().getAccentColor());
        getControllerFactory().getVerificationController().finishVerification();
    }

    @Override
    public void onResume() {
        super.onResume();

        editTextName.addTextChangedListener(this);
        setExistingName();

        nameConfirmationButton.setOnClickListener(this);

        editTextName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE && isNameValid(editTextName.getText().toString())) {
                    confirmName();
                    return true;
                } else {
                    return false;
                }
            }
        });
        getControllerFactory().getGlobalLayoutController().setSoftInputModeForPage(Page.PHONE_REGISTRATION);
        KeyboardUtils.showKeyboard(getActivity());
    }

    private void setExistingName() {
        String name = getStoreFactory().appEntryStore().getName();
        if (name != null) {
            editTextName.setText(name);
            editTextName.setSelection(name.length());
        }
    }

    @Override
    public void onPause() {
        editTextName.removeTextChangedListener(this);
        nameConfirmationButton.setOnClickListener(null);

        super.onPause();
    }

    @Override
    public void onStop() {
        KeyboardUtils.hideKeyboard(getActivity());
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        editTextName = null;
        nameConfirmationButton = null;
        super.onDestroyView();
    }

    private void confirmName() {
        // before loging in show loader and dismiss keyboard
        getContainer().enableProgress(true);
        KeyboardUtils.hideKeyboard(getActivity());

        String name = editTextName.getText().toString();
        getStoreFactory().appEntryStore().registerWithPhone(name,
                                                               getControllerFactory().getAccentColorController()
                                                                                     .getAccentColor(),
                                                               errorCallback);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.pcb__signup:
                confirmName();
                break;
        }
    }

    //  EditText callback /////////////////////////////////////////////////////////////

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        nameConfirmationButton.setState(validateName(charSequence.toString()));
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    private PhoneConfirmationButton.State validateName(String name) {
        if (isNameValid(name)) {
            return PhoneConfirmationButton.State.CONFIRM;
        } else {
            return PhoneConfirmationButton.State.NONE;
        }
    }

    private boolean isNameValid(String name) {
        return !StringUtils.isBlank(name);
    }

    public void onAccentColorHasChanged(int color) {
        nameConfirmationButton.setAccentColor(color);
        editTextName.setAccentColor(color);
    }

    public interface Container {
        void enableProgress(boolean enable);

        int getAccentColor();
    }
}
