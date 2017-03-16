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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.XpPreferenceFragment;
import android.text.TextUtils;
import android.view.View;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.ServiceContainer;
import com.waz.zclient.ZApplication;
import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.controllers.userpreferences.UserPreferencesController;
import com.waz.zclient.core.controllers.tracking.events.Event;
import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.tracking.GlobalTrackingController;
import net.xpece.android.support.preference.PreferenceDividerDecoration;

public abstract class BasePreferenceFragment<T> extends XpPreferenceFragment implements ServiceContainer,
                                                                                        SharedPreferences.OnSharedPreferenceChangeListener {

    protected PreferenceManager preferenceManager;
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
    @CallSuper
    public void onCreatePreferences2(Bundle savedInstanceState, String rootKey) {
        preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(UserPreferencesController.USER_PREFS_TAG);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(getPreferenceScreen().getTitle());
        preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setFocusable(false);
        getListView().setItemAnimator(null);
        getListView().addItemDecoration(new PreferenceDividerDecoration(getContext()).drawBetweenCategories(false));
        setDivider(null);
    }

    @Override
    public void onStop() {
        preferenceManager.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onStart();
    }

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
    public void onDestroy() {
        preferenceManager = null;
        super.onDestroyView();
    }

    @Override
    public final IStoreFactory getStoreFactory() {
        return getActivity() != null ? ZApplication.from(getActivity()).getStoreFactory() : null;
    }

    @Override
    public final IControllerFactory getControllerFactory() {
        return getActivity() != null ? ZApplication.from(getActivity()).getControllerFactory() : null;
    }

    @Override
    public final void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (TextUtils.isEmpty(key)) {
            return;
        }
        final Event event = handlePreferenceChanged(sharedPreferences, key);
        if (event != null) {
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(event);
        }
    }

    public Event handlePreferenceChanged(SharedPreferences sharedPreferences, String key) {
        return null;
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
