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
package com.waz.zclient.controllers.notifications;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.notifications.controllers.ImageNotificationsController;
import com.waz.zclient.utils.IntentUtils;

public class ShareSavedImageActivity extends BaseActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null ||
            !IntentUtils.isLaunchFromSaveImageNotificationIntent(intent)) {
            finish();
            return;
        }

        URI sharedImageUri;
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            finish();
            return;
        } else {
            sharedImageUri = new AndroidURI(uri);
        }

        injectJava(ImageNotificationsController.class).dismissImageSavedNotification();

        startActivity(IntentUtils.getSavedImageShareIntent(this, sharedImageUri));
        finish();
    }
}
