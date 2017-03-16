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
package com.waz.zclient.pages;

import android.app.Activity;
import android.support.v4.app.Fragment;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.ServiceContainer;
import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.core.stores.IStoreFactory;

public class BaseFragment<T> extends Fragment implements ServiceContainer {

    private T container;
    private IControllerFactory controllerFactory;
    private IStoreFactory storeFactory;

    @Override
    public final void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof ServiceContainer) {
            controllerFactory = ((ServiceContainer) activity).getControllerFactory();
            storeFactory = ((ServiceContainer) activity).getStoreFactory();
        }
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

    protected void onPreDetach() { }

    public final T getContainer() {
        return container;
    }

    @Override
    public final IStoreFactory getStoreFactory() {
        return storeFactory;
    }

    @Override
    public final IControllerFactory getControllerFactory() {
        return controllerFactory;
    }

    public <A> A inject(Class<A> dependencyClass) {
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity != null) {
            return activity.injectJava(dependencyClass);
        } else {
            return null;
        }
    }
}
