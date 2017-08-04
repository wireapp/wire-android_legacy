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
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.appentry.AppEntryState;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ViewUtils;

public class FirstLaunchAfterLoginFragment extends
                                           BaseFragment<FirstLaunchAfterLoginFragment.Container> implements
                                                                                                 View.OnClickListener {

    public static final String TAG = FirstLaunchAfterLoginFragment.class.getName();

    private ZetaButton registerButton;

    public static Fragment newInstance() {
        return new FirstLaunchAfterLoginFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login_first_launch, viewGroup, false);

        registerButton = ViewUtils.getView(view, R.id.zb__first_launch__confirm);
        registerButton.setIsFilled(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            registerButton.setAccentColor(getResources().getColor(R.color.text__primary_dark));
        } else {
            registerButton.setAccentColor(getResources().getColor(R.color.text__primary_dark, getContext().getTheme()));
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerButton.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        registerButton.setOnClickListener(null);
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.zb__first_launch__confirm:
                onConfirmClicked();
                break;
        }
    }

    private void onConfirmClicked() {
        if (getActivity() == null ||
            getStoreFactory() == null ||
            getStoreFactory().isTornDown()) {
            return;
        }
        getStoreFactory().appEntryStore().setState(AppEntryState.LOGGED_IN);
    }

    public interface Container {
    }
}
