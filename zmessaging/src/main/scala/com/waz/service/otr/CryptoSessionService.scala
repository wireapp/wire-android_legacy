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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.push.PushNotificationEventsStorage.PlainWriter
import com.waz.threading.Threading
import com.wire.signals.{AggregatingSignal, EventStream, Serialized}
import com.waz.utils.returning
import com.wire.cryptobox.{CryptoBox, CryptoSession, PreKey}

import scala.concurrent.Future
import scala.util.Try

class CryptoSessionService(cryptoBox: CryptoBoxService) extends DerivedLogTag {

  implicit val dis = Threading.Background

  val onCreate = EventStream[SessionId]()
  val onCreateFromMessage = EventStream[SessionId]()

  private def dispatch[A](id: SessionId)(f: Option[CryptoBox] => A) =
    Serialized.future(id.toString){cryptoBox.cryptoBox.map(f)}

  private def dispatchFut[A](id: SessionId)(f: Option[CryptoBox] => Future[A]) =
    Serialized.future(id.toString){cryptoBox.cryptoBox.flatMap(f)}

  def getOrCreateSession(id: SessionId, key: PreKey) = dispatch(id) {
    case None => None
    case Some(cb) =>
      verbose(l"getOrCreateSession($id)")
      def createSession() = returning(cb.initSessionFromPreKey(id.toString, key))(_ => onCreate ! id)

      loadSession(cb, id).getOrElse(createSession())
  }

  private def loadSession(cb: CryptoBox, id: SessionId): Option[CryptoSession] =
    Try(Option(cb.tryGetSession(id.toString))).getOrElse {
      error(l"session loading failed unexpectedly, will delete session file")
      cb.deleteSession(id.toString)
      None
    }

  def deleteSession(id: SessionId) = dispatch(id) { cb =>
    verbose(l"deleteSession($id)")
    cb foreach (_.deleteSession(id.toString))
  }

  def getSession(id: SessionId) = dispatch(id) { cb =>
    verbose(l"getSession($id)")
    cb.flatMap(loadSession(_, id))
  }

  def withSession[A](id: SessionId)(f: CryptoSession => A): Future[Option[A]] = dispatch(id) { cb =>
    cb.flatMap(loadSession(_, id)) map { session =>
      returning(f(session)) { _ => session.save() }
    }
  }

  def decryptMessage(sessionId: SessionId, msg: Array[Byte], eventsWriter: PlainWriter): Future[Unit] = {
    def decrypt(arg: Option[CryptoBox]): (CryptoSession, Array[Byte]) = arg match {
      case None => throw new Exception("CryptoBox missing")
      case Some(cb) =>
        verbose(l"decryptMessage($sessionId. Message length: ${msg.length})")
        loadSession(cb, sessionId).fold {
          val sm = cb.initSessionFromMessage(sessionId.toString, msg)
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
        verbose(l"decrypted data len: ${plain.length}")
      }
    }
  }

  def remoteFingerprint(sid: SessionId) = {
    def fingerprint = withSession(sid)(_.getRemoteFingerprint)
    val stream = onCreate.filter(_ == sid).mapAsync(_ => fingerprint)

    new AggregatingSignal[Option[Array[Byte]], Option[Array[Byte]]](stream, fingerprint, (prev, next) => next)
  }
}
