/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.pages;

import android.app.Activity;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import com.waz.zclient.ServiceContainer;
import com.waz.zclient.ZApplication;
import com.waz.zclient.controllers.IControllerFactory;

public class BaseDialogFragment<T> extends DialogFragment implements ServiceContainer {

    private T container;

    @Override
    public final void onAttach(Activity activity) {
        super.onAttach(activity);
        Fragment fragment = getParentFragment();
        if (fragment != null) {
            container = (T) fragment;
        } else {
            container = (T) activity;
        }
        onPostAttach(activity);
    }

    protected void onPostAttach(Activity activity) { }

    @Override
    public final void onDetach() {
        onPreDetach();
        container = null;
        super.onDetach();
    }

    protected void onPreDetach() {}

    public final T getContainer() {
        return container;
    }

    @Override
    public final IControllerFactory getControllerFactory() {
        return getActivity() != null ? ZApplication.from(getActivity()).getControllerFactory() : null;
    }
}
