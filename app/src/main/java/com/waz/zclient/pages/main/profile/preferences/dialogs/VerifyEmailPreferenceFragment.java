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
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.waz.zclient.R;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.utils.ViewUtils;

public class VerifyEmailPreferenceFragment extends BaseDialogFragment<VerifyEmailPreferenceFragment.Container> {
    public static final String TAG = VerifyEmailPreferenceFragment.class.getSimpleName();
    private static final String ARG_EMAIL = "ARG_EMAIL";

    public static VerifyEmailPreferenceFragment newInstance(String email) {
        final VerifyEmailPreferenceFragment fragment = new VerifyEmailPreferenceFragment();
        final Bundle arg = new Bundle();
        arg.putString(ARG_EMAIL, email);
        fragment.setArguments(arg);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        final String email = TextUtils.htmlEncode(getArguments().getString(ARG_EMAIL, ""));
        final View view = inflater.inflate(R.layout.fragment_preference_email_verification, container, false);

        final View backButton = ViewUtils.getView(view, R.id.tv__back_button);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        final TextView changeNumberButton = ViewUtils.getView(view, R.id.tv__change_email_button);
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
                if (getStoreFactory() == null ||
                    getStoreFactory().isTornDown()) {
                    return;
                }
                getStoreFactory().profileStore()
                                 .resendVerificationEmail(email);
            }
        });
        final String resendHtml = getString(R.string.pref__account_action__email_verification__resend, email);
        resendButton.setText(Html.fromHtml(resendHtml));

        return view;
    }

    public interface Container {
    }
}
