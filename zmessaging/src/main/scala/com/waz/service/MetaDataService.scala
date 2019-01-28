/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.waz.api.OtrClientType
import com.waz.utils.returning

import scala.util.Try

class MetaDataService(context: Context) {

  lazy val metaData = Try {

    val ai = context.getPackageManager.getApplicationInfo(context.getPackageName, PackageManager.GET_META_DATA)
    returning(Option(ai.metaData).getOrElse(new Bundle)) { meta =>
    }
  } .getOrElse(new Bundle)

  lazy val appVersion = Try(context.getPackageManager.getPackageInfo(context.getPackageName, 0).versionCode).getOrElse(0)

  lazy val versionName = Try(context.getPackageManager.getPackageInfo(context.getPackageName, 0).versionName).getOrElse("X.XX")

  lazy val (majorVersion, minorVersion) = Try {
    val vs = versionName.split('.').take(2)
    (vs(0), vs(1))
  }.getOrElse(("X", "XX"))


  lazy val internalBuild = metaData.getBoolean("INTERNAL", false)

  // rough check for device type, used in otr client info
  lazy val deviceClass = {
    val dm = context.getResources.getDisplayMetrics
    val minSize = 600 * dm.density
    if (dm.heightPixels >= minSize && dm.widthPixels >= minSize) OtrClientType.TABLET else OtrClientType.PHONE
  }

  lazy val deviceModel = {
    import android.os.Build._
    s"$MANUFACTURER $MODEL"
  }

  lazy val androidVersion = android.os.Build.VERSION.RELEASE

  lazy val localBluetoothName =
    Try(Option(BluetoothAdapter.getDefaultAdapter.getName).getOrElse("")).getOrElse("")

  val cryptoBoxDirName = "otr"
}
