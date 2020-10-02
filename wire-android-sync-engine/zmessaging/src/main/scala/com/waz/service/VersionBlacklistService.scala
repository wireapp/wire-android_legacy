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

import com.waz.content.GlobalPreferences
import com.waz.content.GlobalPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.VersionBlacklist
import com.waz.sync.client.VersionBlacklistClient
import com.waz.threading.Threading
import com.wire.signals.Signal

import scala.concurrent.Future
import scala.concurrent.duration._

class VersionBlacklistService(metadata: MetaDataService, prefs: GlobalPreferences, client: VersionBlacklistClient)
  extends DerivedLogTag {

  import Threading.Implicits.Background
  import metadata._

  val lastUpToDateSync   = prefs.preference[Long](LastUpToDateSyncTime)
  val lastCheckedVersion = prefs.preference[Int](LastCheckedVersion)
  val upToDatePref       = prefs.preference[Boolean](VersionUpToDate)

  val upToDate = Signal.zip(lastCheckedVersion.signal, upToDatePref.signal) map {
    case (lastVersion, isUpToDate) => lastVersion != metadata.appVersion || isUpToDate
  }

  // check if needs syncing, and try doing that immediately
  shouldSync foreach {
    case true => syncVersionBlackList()
    case false => // no need
  }

  def shouldSync = for {
    lastSyncTime <- lastUpToDateSync()
    lastVersion <- lastCheckedVersion()
  } yield {
    lastVersion != metadata.appVersion || (System.currentTimeMillis - lastSyncTime).millis > 1.day
  }

  def syncVersionBlackList() = client.loadVersionBlacklist().future flatMap { blacklist =>
    updateBlacklist({
      val list = blacklist.right.getOrElse(VersionBlacklist())
      debug(l"Retrieved version blacklist: $list")
      list
    })
  }

  def updateBlacklist(blacklist: VersionBlacklist): Future[Unit] = {
    verbose(l"app Version: $appVersion, blacklist: $blacklist")
    for {
      _ <- upToDatePref := (appVersion >= blacklist.oldestAccepted && !blacklist.blacklisted.contains(appVersion))
      _ <- lastUpToDateSync := System.currentTimeMillis
      _ <- lastCheckedVersion := appVersion
    } yield ()
  }
}
