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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.service.push.PushNotificationEventsStorage.PlainWriter
import com.waz.threading.Threading
import com.waz.utils.crypto.AESUtils
import com.waz.utils.events.{AggregatingSignal, EventStream}
import com.waz.utils.{LoggedTry, Serialized, returning}
import com.wire.cryptobox.{CryptoBox, CryptoSession, PreKey}

import scala.concurrent.Future

class CryptoSessionService(cryptoBox: CryptoBoxService) {

  implicit val dis = Threading.Background

  val onCreate = EventStream[String]()
  val onCreateFromMessage = EventStream[String]()

  private def dispatch[A](id: String)(f: Option[CryptoBox] => A) =
    Serialized.future(id){cryptoBox.cryptoBox.map(f)}

  private def dispatchFut[A](id: String)(f: Option[CryptoBox] => Future[A]) =
    Serialized.future(id){cryptoBox.cryptoBox.flatMap(f)}

  def getOrCreateSession(id: String, key: PreKey) = dispatch(id) {
    case None => None
    case Some(cb) =>
      verbose(s"getOrCreateSession($id)")
      def createSession() = returning(cb.initSessionFromPreKey(id, key))(_ => onCreate ! id)

      loadSession(cb, id).getOrElse(createSession())
  }

  private def loadSession(cb: CryptoBox, id: String): Option[CryptoSession] =
    LoggedTry(Option(cb.tryGetSession(id))).getOrElse {
      error("session loading failed unexpectedly, will delete session file")
      cb.deleteSession(id)
      None
    }

  def deleteSession(id: String) = dispatch(id) { cb =>
    verbose(s"deleteSession($id)")
    cb foreach (_.deleteSession(id))
  }

  def getSession(id: String) = dispatch(id) { cb =>
    verbose(s"getSession($id)")
    cb.flatMap(loadSession(_, id))
  }

  def withSession[A](id: String)(f: CryptoSession => A): Future[Option[A]] = dispatch(id) { cb =>
    cb.flatMap(loadSession(_, id)) map { session =>
      returning(f(session)) { _ => session.save() }
    }
  }

  def decryptMessage(sessionId: String, msg: Array[Byte], eventsWriter: PlainWriter): Future[Unit] = {
    def decrypt(arg: Option[CryptoBox]): (CryptoSession, Array[Byte]) = arg match {
      case None => throw new Exception("CryptoBox missing")
      case Some(cb) =>
        verbose(s"decryptMessage($sessionId for message: ${msg.length} = ${AESUtils.base64(msg)})")
        loadSession(cb, sessionId).fold {
          val sm = cb.initSessionFromMessage(sessionId, msg)
          onCreate ! sessionId
          onCreateFromMessage ! sessionId
          (sm.getSession, sm.getMessage)
        } { s =>
          (s, s.decrypt(msg))
        }
    }

    dispatchFut(sessionId) { opt =>
      val (session, plain) = decrypt(opt)
      eventsWriter(plain).map { _ =>
        session.save()
        verbose(s"decrypted data len: ${plain.length}")
      }
    }
  }

  def remoteFingerprint(sid: String) = {
    def fingerprint = withSession(sid)(_.getRemoteFingerprint)
    val stream = onCreate.filter(_ == sid).mapAsync(_ => fingerprint)

    new AggregatingSignal[Option[Array[Byte]], Option[Array[Byte]]](stream, fingerprint, (prev, next) => next)
  }
}
