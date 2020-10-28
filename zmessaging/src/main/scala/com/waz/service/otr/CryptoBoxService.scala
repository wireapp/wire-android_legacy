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
package com.waz.service.otr

import java.io.File

import android.content.Context
import com.waz.log.LogSE._
import com.waz.api.Verification
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.OtrLastPrekey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.model.otr.{Client, ClientId, SignalingKey}
import com.waz.service.MetaDataService
import com.wire.signals.{DispatchQueue, SerialDispatchQueue}
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.cryptobox.{CryptoBox, PreKey}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.util.Try

class CryptoBoxService(context: Context, userId: UserId, metadata: MetaDataService, userPrefs: UserPreferences) extends DerivedLogTag {
  import CryptoBoxService._
  private implicit val dispatcher = DispatchQueue(DispatchQueue.SERIAL, Threading.IO, None)

  private[service] lazy val cryptoBoxDir = returning(new File(new File(context.getFilesDir, metadata.cryptoBoxDirName), userId.str))(_.mkdirs())

  private[waz] val lastPreKeyId = userPrefs.preference(OtrLastPrekey)

  private lazy val clientLabel = if (metadata.localBluetoothName.isEmpty) metadata.deviceModel else metadata.localBluetoothName

  private var _cryptoBox = Option.empty[CryptoBox]

  lazy val sessions = new CryptoSessionService(this)

  def cryptoBox = Future {
    _cryptoBox.orElse {
      returning(load) { _cryptoBox = _ }
    }
  }

  private def load = Try {
    verbose(l"cryptobox directory created")
    cryptoBoxDir.mkdirs()
    CryptoBox.open(cryptoBoxDir.getAbsolutePath)
  } .toOption

  def apply[A](f: CryptoBox => Future[A]): Future[Option[A]] = cryptoBox flatMap {
    case None => Future successful None
    case Some(cb) => f(cb) map (Some(_))
  }

  def deleteCryptoBox() = Future {
    _cryptoBox.foreach(_.close())
    _cryptoBox = None
    IoUtils.deleteRecursively(cryptoBoxDir)
    verbose(l"cryptobox directory deleted")
  }

  def close() = Future {
    _cryptoBox.foreach(_.close())
    _cryptoBox = None
  }

  def createClient(id: ClientId = ClientId()) = apply { cb =>
    val (lastKey, keys) = (cb.newLastPreKey(), cb.newPreKeys(0, PreKeysCount))
    (lastPreKeyId := keys.last.id) map { _ =>
      (Client(id, clientLabel, metadata.deviceModel, Some(Instant.now), signalingKey = Some(SignalingKey()), verified = Verification.VERIFIED, devType = metadata.deviceClass), lastKey, keys.toSeq)
    }
  }

  def generatePreKeysIfNeeded(remainingKeys: Seq[Int]): Future[Seq[PreKey]] = {

    val remaining = remainingKeys.filter(_ <= CryptoBox.MAX_PREKEY_ID)

    val maxId = if (remaining.isEmpty) None else Some(remaining.max)

    // old version was not updating lastPreKeyId properly, we need to detect that and reset it to lastId on backend
    def shouldResetLastIdPref(lastId: Int) = maxId.exists(max => max > lastId && max < LocalPreKeysLimit / 2)

    if (remaining.size > LowPreKeysThreshold) Future.successful(Nil)
    else lastPreKeyId() flatMap { lastId =>
      val startId =
        if (lastId > LocalPreKeysLimit) 0
        else if (shouldResetLastIdPref(lastId)) maxId.fold(0)(_ + 1)
        else lastId + 1

      val count = PreKeysCount - remaining.size

      apply { cb =>
        val keys = cb.newPreKeys(startId, count).toSeq
        (lastPreKeyId := keys.last.id) map (_ => keys)
      } map { _ getOrElse Nil }
    }
  }
}

object CryptoBoxService {
  val PreKeysCount = 100
  val LowPreKeysThreshold = 50
  val LocalPreKeysLimit = 16 * 1024
}
