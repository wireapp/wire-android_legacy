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
import java.util.UUID
import UUID.randomUUID

import com.waz.utils.IoUtils
import com.wire.cryptobox._
import org.junit.runner.RunWith
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
@Ignore class OtrSpec extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with GeneratorDrivenPropertyChecks {

  val base = new File("target/cryptobox-jni/temp")
  var store: File = _
  var box: CryptoBox = _

  def createBox(file: File = new File(base, s"store-$randomUUID")) = {
    file.mkdirs()
    CryptoBox.open(file.toString)
  }

  before {
    store = new File(base, s"store-$randomUUID")
    store.mkdirs()
    box = CryptoBox.open(store.toString)
  }

  after {
    box.close()
  }

  scenario("Open a box and play with the toys in it") {
    box.isClosed shouldBe false

    val x = box.newPreKeys(1, 1)
    val y = box.initSessionFromPreKey(randomUUID.toString, x(0))

    info(y.id)
    info(new String(y.getRemoteFingerprint, "US-ASCII"))
  }

  scenario("Two clients initialize session between each other at the same time") {
    val box1 = createBox()

    val prekeys = box.newPreKeys(1, 1)
    val prekeys1 = box1.newPreKeys(1, 1)

    val session = box.initSessionFromPreKey("session", prekeys1.head)
    val session1 = box1.initSessionFromPreKey("session1", prekeys.head)


    val msg = session.encrypt("From 1".getBytes)
    val msg1 = session1.encrypt("From 2".getBytes)
    val msg2 = session1.encrypt("From 21".getBytes)

    new String(session.decrypt(msg1)) shouldEqual "From 2"
    new String(session1.decrypt(msg)) shouldEqual "From 1"
    new String(session.decrypt(msg2)) shouldEqual "From 21"

    try {
      session.decrypt(msg2)
      fail("should fail")
    } catch {
      case e: CryptoException => e.code shouldEqual CryptoException.Code.DUPLICATE_MESSAGE
    }

    forAll { msgs: List[(Int, String)] =>
      msgs foreach {
        case (client, m) if client % 2 == 0 =>
          new String(session1.decrypt(session.encrypt(m.getBytes))) shouldEqual m
        case (_, m) =>
          new String(session.decrypt(session1.encrypt(m.getBytes))) shouldEqual m
      }
    }

    val msg3 = session1.encrypt("From 21".getBytes)
    new String(session.decrypt(msg3)) shouldEqual "From 21"
    intercept[CryptoException] {
      new String(session.decrypt(msg3)) shouldEqual "From 21"
    }
  }

  scenario("Session is lost on sending side") {
    val box1 = createBox()

    val prekeys = box.newPreKeys(1, 1)
    val prekeys1 = box1.newPreKeys(1, 2)

    val session = box.initSessionFromPreKey("session", prekeys1.head)
    val msg = session.encrypt("From 1".getBytes)

    val s = box1.initSessionFromMessage("session1", msg)
    val (session1, m) = (s.getSession, new String(s.getMessage))
    m shouldEqual "From 1"

    new String(session.decrypt(session1.encrypt("From 2".getBytes))) shouldEqual "From 2"
    new String(session1.decrypt(session.encrypt("From 12".getBytes))) shouldEqual "From 12"

    val session2 = box.initSessionFromPreKey("session2", prekeys1(1))
    val msg3 = session2.encrypt("Test".getBytes)

    info(s"fingerprint: ${new String(session.getRemoteFingerprint, "US-ASCII")}")
    info(s"new session fingerprint: ${new String(session2.getRemoteFingerprint, "US-ASCII")}")

    new String(session1.decrypt(session2.encrypt("Fresh session".getBytes))) shouldEqual "Fresh session"
  }

  scenario("Sending side looses cryptobox instance") {
    val box1 = createBox()

    val prekeys = box.newPreKeys(1, 2)
    val prekeys1 = box1.newPreKeys(1, 2)

    val session = box.initSessionFromPreKey("session", prekeys1.head)
    val msg = session.encrypt("From 1".getBytes)

    val s = box1.initSessionFromMessage("session1", msg)
    val (session1, m) = (s.getSession, new String(s.getMessage))
    m shouldEqual "From 1"

    new String(session.decrypt(session1.encrypt("From 2".getBytes))) shouldEqual "From 2"
    new String(session1.decrypt(session.encrypt("From 12".getBytes))) shouldEqual "From 12"

    val box2 = createBox()
    val session2 = box2.initSessionFromPreKey("session2", prekeys(1))
    val msg3 = session2.encrypt("Test".getBytes)

    try {
      session.decrypt(msg3)
      fail("should throw REMOTE_IDENTITY_CHANGED")
    } catch {
      case e: CryptoException =>
        e.code shouldEqual CryptoException.Code.REMOTE_IDENTITY_CHANGED
    }
    info(s"fingerprint: ${new String(session.getRemoteFingerprint, "US-ASCII")}")

    val s3 = box.initSessionFromMessage("session1", msg3)
    val (session3, m3) = (s3.getSession, new String(s3.getMessage))

    info(s"new session fingerprint: ${new String(session3.getRemoteFingerprint, "US-ASCII")}")
    m3 shouldEqual "Test"
  }

  scenario("One end skips messages") {
    val box1 = createBox()

    val prekeys = box.newPreKeys(1, 1)
    val prekeys1 = box1.newPreKeys(1, 1)

    val session = box.initSessionFromPreKey("session", prekeys1.head)
    val session1 = box1.initSessionFromPreKey("session1", prekeys.head)

    val msg = session.encrypt("From 1".getBytes)
    val msg1 = session1.encrypt("From 2".getBytes)

    new String(session.decrypt(msg1)) shouldEqual "From 2"
    new String(session1.decrypt(msg)) shouldEqual "From 1"

    // send many messages, receive only couple of them
    for (i <- 0 to 10000) {
      val msg = session.encrypt(s"message $i".getBytes)
      if (i % 100 == 0) new String(session1.decrypt(msg)) shouldEqual s"message $i"
    }

    forAll(maxSize(1000)) { msgs: List[(Int, String)] =>
      msgs foreach {
        case (client, m) if client % 2 == 0 =>
          new String(session1.decrypt(session.encrypt(m.getBytes))) shouldEqual m
        case (_, m) =>
          new String(session.decrypt(session1.encrypt(m.getBytes))) shouldEqual m
      }
    }
  }

  scenario("One end uses prekeys generated from different cryptobox instance") {
    val dir1 = new File(base, s"store-box1")
    val dir2 = new File(base, s"store-box2")
    val box1 = createBox(dir1)
    val box2 = createBox(dir2)

    val prekeys1 = box1.newPreKeys(1, 1)

    new File(dir1, "prekeys").listFiles() foreach { f =>
      IoUtils.copy(f, new File(new File(dir2, "prekeys"), f.getName))
    }

    val session = box.initSessionFromPreKey("session", prekeys1.head)
    val msg = session.encrypt("From 0".getBytes)

    try {
      box2.initSessionFromMessage("session1", msg)
      fail(s"should throw INVALID_SIGNATURE")
    } catch {
      case e: CryptoException => e.code shouldEqual CryptoException.Code.INVALID_SIGNATURE
    }
  }

  scenario("One end uses prekeys from different cryptobox, session is initialized from prekey on both ends") {
    val dir1 = new File(base, s"store-box1")
    val dir2 = new File(base, s"store-box2")
    val box1 = createBox(dir1)
    val box2 = createBox(dir2)

    val prekeys = box.newPreKeys(1, 1)
    val prekeys1 = box1.newPreKeys(1, 1)

    new File(dir1, "prekeys").listFiles() foreach { f =>
      IoUtils.copy(f, new File(new File(dir2, "prekeys"), f.getName))
    }

    val session = box.initSessionFromPreKey("session", prekeys1.head)
    val session1 = box2.initSessionFromPreKey("session1", prekeys.head)

    val msg = session.encrypt("From 0".getBytes)

    try {
      session1.decrypt(msg)
      fail(s"should throw INVALID_SIGNATURE")
    } catch {
      case e: CryptoException => e.code shouldEqual CryptoException.Code.INVALID_SIGNATURE
    }
  }

}
