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

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import com.waz.zclient.newreg.fragments.OTREmailSignInFragment;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import timber.log.Timber;

public class OTRSignInActivity  extends BaseActivity implements OTREmailSignInFragment.Container {
    public static final String TAG = OTRSignInActivity.class.getName();
    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";

    private LoadingIndicatorView progressView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        super.onCreate(savedInstanceState);

        if (LayoutSpec.isPhone(this)) {
            ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this);
        }

        setContentView(R.layout.activity_otr_sign_in);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.fl_main_content,
                                                               OTREmailSignInFragment.newInstance(),
                                                               OTREmailSignInFragment.TAG).commit();
        }

        progressView = ViewUtils.getView(this, R.id.liv__progress);

        // always disable progress bar at the beginning
        enableProgress(false);
    }

    @Override
    public int getBaseTheme() {
        return R.style.Theme_Dark;
    }

    @Override
    public void enableProgress(boolean enabled) {
        if (enabled) {
            progressView.show(LoadingIndicatorView.SPINNER_WITH_DIMMED_BACKGROUND, true);
        } else {
            progressView.hide();
        }
    }

    @Override
    public void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onOpenUrl(String url) {
        try {
            if (!url.startsWith(HTTP_PREFIX) && !url.startsWith(HTTPS_PREFIX)) {
                url = HTTP_PREFIX + url;
            }
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        } catch (Exception e) {
            Timber.e("Failed to open URL: %s", url);
        }
    }
}
