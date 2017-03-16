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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.waz.zclient.pages.main.profile.preferences.PreferenceScreenStrategy;
import com.waz.zclient.utils.ViewUtils;

public abstract class BasePreferenceActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
                                                                             PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback,
                                                                             PreferenceScreenStrategy.ReplaceFragment.Callbacks {

    private PreferenceScreenStrategy.ReplaceFragment replaceFragmentStrategy;
    private Toolbar toolbar;
    private TextSwitcher titleSwitcher;
    private CharSequence title;

    @SuppressLint("PrivateResource")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        replaceFragmentStrategy = new PreferenceScreenStrategy.ReplaceFragment(this,
                                                                               R.anim.abc_fade_in,
                                                                               R.anim.abc_fade_out,
                                                                               R.anim.abc_fade_in,
                                                                               R.anim.abc_fade_out);

        toolbar = ViewUtils.getView(this, R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        assert ab != null;
        ab.setDisplayHomeAsUpEnabled(true);

        title = getTitle();
        titleSwitcher = new TextSwitcher(toolbar.getContext());
        titleSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                TextView tv = new AppCompatTextView(toolbar.getContext());
                TextViewCompat.setTextAppearance(tv, R.style.TextAppearance_AppCompat_Widget_ActionBar_Title);
                return tv;
            }
        });
        titleSwitcher.setCurrentText(title);

        ab.setCustomView(titleSwitcher);
        ab.setDisplayShowCustomEnabled(true);
        ab.setDisplayShowTitleEnabled(false);

        titleSwitcher.setInAnimation(this, R.anim.abc_fade_in);
        titleSwitcher.setOutAnimation(this, R.anim.abc_fade_out);
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        if (title == null) {
            return;
        }
        if (title.equals(this.title)) {
            return;
        }
        this.title = title;
        if (this.titleSwitcher != null) {
            this.titleSwitcher.setText(this.title);
        }
    }

    @Override
    public int getBaseTheme() {
        return R.style.Theme_Dark_Preferences;
    }

    @Override
    public boolean onPreferenceStartScreen(final PreferenceFragmentCompat preferenceFragmentCompat,
                                           final PreferenceScreen preferenceScreen) {
        replaceFragmentStrategy.onPreferenceStartScreen(getSupportFragmentManager(),
                                                        preferenceFragmentCompat,
                                                        preferenceScreen);
        return true;
    }

    @Override
    public boolean onPreferenceDisplayDialog(PreferenceFragmentCompat preferenceFragmentCompat, Preference preference) {
        return false;
    }
}
