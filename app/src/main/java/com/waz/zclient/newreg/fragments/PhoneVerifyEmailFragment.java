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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.ViewUtils;

public class PhoneVerifyEmailFragment extends BaseFragment<PhoneVerifyEmailFragment.Container> implements View.OnClickListener {
    public static final String TAG = PhoneVerifyEmailFragment.class.getName();

    private View buttonBack;
    private TextView resendTextView;
    private TextView checkEmailTextView;
    private TextView didntGetEmailTextView;

    public static Fragment newInstance() {
        return new PhoneVerifyEmailFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pending_phone_email_confirmation, viewGroup, false);

        buttonBack = ViewUtils.getView(view, R.id.ll__activation_button__back);
        resendTextView = ViewUtils.getView(view, R.id.ttv__sign_up__resend);
        checkEmailTextView = ViewUtils.getView(view, R.id.ttv__sign_up__check_email);
        didntGetEmailTextView = ViewUtils.getView(view, R.id.ttv__sign_up__didnt_get);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        buttonBack.setOnClickListener(this);
        resendTextView.setOnClickListener(this);
        onAccentColorHasChanged(getContainer().getAccentColor());
        onEmailLoaded(getStoreFactory().appEntryStore().getEmail());
        TextViewUtils.boldText(checkEmailTextView);
    }

    @Override
    public void onPause() {
        buttonBack.setOnClickListener(null);
        resendTextView.setOnClickListener(null);
        KeyboardUtils.hideKeyboard(getActivity());
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        buttonBack = null;
        super.onDestroyView();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Actions
    //
    //////////////////////////////////////////////////////////////////////////////////////////


    public void onEmailLoaded(String email) {
        checkEmailTextView.setText(getResources().getString(R.string.profile__email__verify__instructions, email));
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    public void onAccentColorHasChanged(int color) {
        resendTextView.setTextColor(color);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ll__activation_button__back:
                goBack();
                break;
            case R.id.ttv__pending_email__resend:
                ViewUtils.fadeOutView(resendTextView);
                getStoreFactory().appEntryStore().resendEmail();
                break;
            case R.id.ttv__sign_up__resend:
                didntGetEmailTextView.animate().alpha(0).start();
                resendTextView.animate()
                              .alpha(0)
                              .withEndAction(new Runnable() {
                                  @Override
                                  public void run() {
                                      resendTextView.setEnabled(false);
                                  }
                              })
                              .start();
                getStoreFactory().appEntryStore().resendEmail();
                break;
        }
    }

    private void goBack() {
        getStoreFactory().appEntryStore().onBackPressed();
    }

    public interface Container {

        void onOpenUrl(String url);

        int getAccentColor();
    }
}
