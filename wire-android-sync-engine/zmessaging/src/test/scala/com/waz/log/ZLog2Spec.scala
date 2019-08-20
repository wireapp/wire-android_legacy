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
package com.waz.log

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.specs.ZSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ZLog2Spec extends ZSpec with DerivedLogTag {

  case class Person(name: String, age: Int)

  private val testPerson = Person("Mike", 25)

  feature("Safe logging") {

    scenario("create Log that use LogShow implementation for building log message") {
      implicit val PersonLogShow: LogShow[Person] = new LogShow[Person] {
        override def showSafe(value: Person): String   = value.toString + " Safe"
        override def showUnsafe(value: Person): String = value.toString + " Unsafe"
      }

      val personLog = l"$testPerson"
      debug(l"Check the current debug mode: Person = $testPerson")
      personLog.buildMessageSafe shouldBe PersonLogShow.showSafe(testPerson)
      personLog.buildMessageUnsafe shouldBe PersonLogShow.showUnsafe(testPerson)
    }

    scenario("compile and create Log for all basic types") {
      val numbersLog = l"byte: ${0.toByte} short: ${1.toShort} int: ${2} long: ${3.toLong}"
      numbersLog.buildMessageSafe shouldBe numbersLog.buildMessageUnsafe
      numbersLog.buildMessageSafe shouldBe "byte: 0 short: 1 int: 2 long: 3"
    }

    scenario("compile and create Log for type if LogShow instance is in scope") {
      implicit val PersonLogShow: LogShow[Person] = LogShow.create(_.toString)

      val personLog = l"person: $testPerson"
      personLog.buildMessageSafe shouldBe personLog.buildMessageUnsafe
      personLog.buildMessageSafe shouldBe s"person: ${testPerson.toString}"
    }

    scenario("compile and create default Log for type if LogShow instance is not in scope") {
      val nonImplicitPersonLogShow: LogShow[Person] = LogShow.logShowWithHash

      val personLog = l"person: $testPerson"
      personLog.buildMessageSafe shouldBe s"person: ${nonImplicitPersonLogShow.showSafe(testPerson)}"
      personLog.buildMessageUnsafe shouldBe s"person: ${nonImplicitPersonLogShow.showUnsafe(testPerson)}"
    }

    scenario("compile and create Log for types in a collection") {
      implicit val PersonLogShow: LogShow[Person] = new LogShow[Person] {
        override def showSafe(value: Person): String   = "Safe"
        override def showUnsafe(value: Person): String = "Unsafe"
      }

      val collectionLog = l"${List(testPerson, testPerson, testPerson, testPerson)}"
      collectionLog.buildMessageSafe shouldBe "Safe, Safe, Safe and 1 other elements..."
      collectionLog.buildMessageUnsafe shouldBe "Unsafe, Unsafe, Unsafe and 1 other elements..."
    }
  }
}
