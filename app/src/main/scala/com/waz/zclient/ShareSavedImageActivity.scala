/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient

import android.content.Intent
import android.os.Bundle
import com.waz.utils.wrappers.AndroidURI
import com.waz.zclient.notifications.controllers.ImageNotificationsController

class ShareSavedImageActivity extends BaseActivity {

  override def onCreate(savedInstanceState: Bundle) = {
      super.onCreate(savedInstanceState)

    val intent = getIntent
    if(intent == null || !Intents.isLaunchFromSaveImageNotificationIntent(Some(intent))) {
      finish()
    } else {
      val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
      if(uri == null) {
        finish()
      } else {
        val sharedImageUri = new AndroidURI(uri)
        inject[ImageNotificationsController].dismissImageSavedNotification()

        startActivity(Intents.SavedImageShareIntent(this, sharedImageUri))
        finish()
      }
    }
  }
}
