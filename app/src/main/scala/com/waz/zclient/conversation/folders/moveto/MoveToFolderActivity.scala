/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.conversation.folders.moveto

import android.content.{Context, Intent}
import android.os.Bundle
import com.waz.zclient.{BaseActivity, R}

class MoveToFolderActivity extends BaseActivity {

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_blank)
    getSupportFragmentManager.beginTransaction()
      .replace(R.id.activity_blank_framelayout_container, MoveToFolderFragment.newInstance)
      .addToBackStack(MoveToFolderFragment.TAG)
      .commit()
  }

}

object MoveToFolderActivity {
  def newIntent(context: Context) : Intent = {
    new Intent(context, classOf[MoveToFolderActivity])
  }
}
