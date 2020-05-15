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
package com.waz.zclient.security.checks

import java.io.File

import android.content.Context
import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences.RootDetected
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading.Implicits.Background
import com.waz.zclient.Injectable
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityChecklist

import scala.concurrent.Future
import scala.util.Try

class RootDetectionCheck(preferences: GlobalPreferences)(implicit context: Context)
  extends SecurityChecklist.Check with Injectable with DerivedLogTag {

  override def isSatisfied: Future[Boolean] = wasPreviouslyRooted.map {
    case true => false
    case false =>
      lazy val releaseTagsExist = getSystemProperty("ro.build.tags").contains("release-keys")
      lazy val otacertsExist = new File("/etc/security/otacerts.zip").exists()
      lazy val canRunSu = runCommand("su")
      val isDeviceRooted = !releaseTagsExist || !otacertsExist || canRunSu

      if (isDeviceRooted) noteThatPhoneIsRooted()

      verbose(l"isDeviceRooted: $isDeviceRooted")
      !isDeviceRooted
  }

  private def getSystemProperty(key: String): Option[String] =
    Try(
      Class.forName("android.os.SystemProperties")
        .getMethod("get", classOf[String])
        .invoke(null, key)
        .asInstanceOf[String]
    ).toOption

  private def runCommand(command: String): Boolean =
    Try {
      verbose(l"runCommand($command)")
      Runtime.getRuntime.exec(command)
    }.isSuccess

  private def wasPreviouslyRooted: Future[Boolean] = preferences(RootDetected).apply()
  private def noteThatPhoneIsRooted(): Future[Unit] = preferences(RootDetected) := true
}

object RootDetectionCheck {

  def apply(preferences: GlobalPreferences)(implicit context: Context): RootDetectionCheck =
    new RootDetectionCheck(preferences)
}
