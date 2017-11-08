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
package com.waz.zclient;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.ClientRegistrationState;
import com.waz.api.Self;
import com.waz.api.UpdateListener;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ViewUtils;

public class OtrDeviceLimitFragment extends BaseDialogFragment<OtrDeviceLimitFragment.Container> implements
                                                                                                 OnBackPressedListener,
                                                                                                 View.OnClickListener {

    public static final String TAG = OtrDeviceLimitFragment.class.getName();

    private ZetaButton logoutButton;
    private ZetaButton manageDevicesButton;
    private Self self;

    private final UpdateListener selfUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            if (self == null) {
                return;
            }
            if (self.getClientRegistrationState() != ClientRegistrationState.LIMIT_REACHED &&
                getContainer() != null) {
                getContainer().dismissOtrDeviceLimitFragment();
            }
        }
    };

    public static OtrDeviceLimitFragment newInstance() {
        return new OtrDeviceLimitFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_otr_device_limit, container, false);
        view.setOnClickListener(this);

        logoutButton = ViewUtils.getView(view, R.id.zb__otr_device_limit__logout);
        logoutButton.setIsFilled(false);
        logoutButton.setOnClickListener(this);

        manageDevicesButton = ViewUtils.getView(view, R.id.zb__otr_device_limit__manage_devices);
        manageDevicesButton.setIsFilled(true);
        manageDevicesButton.setOnClickListener(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            logoutButton.setAccentColor(getResources().getColor(R.color.text__primary_dark));
            //noinspection deprecation
            manageDevicesButton.setAccentColor(getResources().getColor(R.color.text__primary_dark));
        } else {
            logoutButton.setAccentColor(getResources().getColor(R.color.text__primary_dark, getContext().getTheme()));
            manageDevicesButton.setAccentColor(getResources().getColor(R.color.text__primary_dark, getContext().getTheme()));
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (self == null) {
            self = getStoreFactory().zMessagingApiStore().getApi().getSelf();
            self.addUpdateListener(selfUpdateListener);
            selfUpdateListener.updated();
        }
    }

    @Override
    public void onStop() {
        if (self != null) {
            self.removeUpdateListener(selfUpdateListener);
            self = null;
        }
        super.onStop();
    }

    @Override
    public void onClick(View v) {
        if (getContainer() == null || v == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.zb__otr_device_limit__logout:
                getContainer().logout();
                break;
            case R.id.zb__otr_device_limit__manage_devices:
                getContainer().manageDevices();
                break;
        }
    }

    @Override
    public boolean onBackPressed() {
        return true;
    }

    public interface Container {
        void logout();
        void manageDevices();
        void dismissOtrDeviceLimitFragment();
    }
}
