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
package com.waz.zclient

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.waz.service.ZMessaging
import com.wire.signals.EventContext
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.threading.Threading._

class ForceUpdateActivity extends BaseActivity {

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_force_update)
    getSupportFragmentManager.beginTransaction()
                             .replace(R.id.fl_main_content, UpdateFragment.newInstance(),
                                 UpdateFragment.TAG)
                             .commit()
  }
}

object ForceUpdateActivity {
  def checkBlacklist(activity: Activity)(implicit ecxt: EventContext): Unit =
    ZMessaging.currentGlobal.blacklist.upToDate
        .ifFalse
        .filter(_ => BuildConfig.ENABLE_BLACKLIST)
        .onUi { _ =>
          activity.startActivity(
              new Intent(activity.getApplicationContext, classOf[ForceUpdateActivity]))
          activity.finish()
        }
}
