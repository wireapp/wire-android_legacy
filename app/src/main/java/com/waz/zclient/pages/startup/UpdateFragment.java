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
package com.waz.zclient.pages.startup;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.ui.views.ZetaButton;

public class UpdateFragment extends BaseFragment<UpdateFragment.Container> {

    public static final String TAG = UpdateFragment.class.getName();

    public static UpdateFragment newInstance() {
        return new UpdateFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_update, container, false);

        ZetaButton zetaButton = ViewUtils.getView(view, R.id.zb__update__download);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            zetaButton.setAccentColor(getResources().getColor(R.color.forced_update__button__background_color));
        } else {
            zetaButton.setAccentColor(getResources().getColor(R.color.forced_update__button__background_color, getContext().getTheme()));
        }

        zetaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchDownloadLink();
            }
        });
        return view;
    }

    public interface Container {
    }

    private void launchDownloadLink() {
        final String appPackageName = getActivity().getPackageName();
        try {
            Intent launchIntent = getActivity().getPackageManager().getLaunchIntentForPackage("com.android.vending");
            ComponentName comp = new ComponentName("com.android.vending", "com.google.android.finsky.activities.LaunchUrlHandlerActivity");
            launchIntent.setComponent(comp);
            launchIntent.setData(Uri.parse("market://details?id=" + appPackageName));
            startActivity(launchIntent);
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }
}
