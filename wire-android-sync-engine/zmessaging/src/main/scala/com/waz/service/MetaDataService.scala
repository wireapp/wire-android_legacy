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
import com.waz.api.OtrClientType
import scala.collection.JavaConverters._

import scala.util.Try

class MetaDataService(context: Context) {
  def metaData: Map[String, String] =
    Try(context.getPackageManager.getApplicationInfo(context.getPackageName, PackageManager.GET_META_DATA).metaData)
      .toOption
      .map { bundle => bundle.keySet.asScala.map(key => key -> bundle.get(key).toString).toMap }
      .getOrElse(Map.empty)

  lazy val appVersion: Int =
    Try(context.getPackageManager.getPackageInfo(context.getPackageName, 0).versionCode).getOrElse(0)

  lazy val versionName: String =
    Try(context.getPackageManager.getPackageInfo(context.getPackageName, 0).versionName).getOrElse("X.XX")

  lazy val (majorVersion, minorVersion): (String, String) = Try {
    val vs = versionName.split('.').take(2)
    (vs(0), vs(1))
  }.getOrElse(("X", "XX"))

  lazy val internalBuild: Boolean = metaData.get("INTERNAL").exists(_.toBoolean)

  // rough check for device type, used in otr client info
  lazy val deviceClass: OtrClientType = {
    val dm = context.getResources.getDisplayMetrics
    val minSize = 600 * dm.density
    if (dm.heightPixels >= minSize && dm.widthPixels >= minSize) OtrClientType.TABLET else OtrClientType.PHONE
  }

  lazy val deviceModel: String = {
    import android.os.Build._
    s"$MANUFACTURER $MODEL"
  }

  lazy val androidVersion: String = android.os.Build.VERSION.RELEASE

  lazy val localBluetoothName: String =
    Try(Option(BluetoothAdapter.getDefaultAdapter.getName).getOrElse("")).getOrElse("")

  val cryptoBoxDirName: String = "otr"
}

object MetaDataService {
  val HTTP_PROXY_URL_KEY = "http_proxy_url"
  val HTTP_PROXY_PORT_KEY = "http_proxy_port"
}