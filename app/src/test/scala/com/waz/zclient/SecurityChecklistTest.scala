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
package com.waz.zclient
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.SecurityChecklistTest._
import com.waz.zclient.security.SecurityChecklist
import com.waz.zclient.security.SecurityChecklist.{Action, Check}
import org.junit.Assert._
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class SecurityChecklistTest extends JUnitSuite {

  private var numSatisfiedChecks = 0
  private var numUnsatisfiedChecks = 0

  private val onPass = { () => numSatisfiedChecks += 1  }
  private val onFail = { () => numUnsatisfiedChecks += 1 }

  private def passingCheck: Check = new MockCheck(true, onPass, onFail)
  private def failingCheck: Check = new MockCheck(false, onPass, onFail)

  @Before
  def setUp(): Unit = {
    numSatisfiedChecks = 0
    numUnsatisfiedChecks = 0
  }

  @Test
  def testEmptyChecklist(): Unit = {
    // Given
    val sut = SecurityChecklist()
    // When
    sut.run().foreach { allChecksPassed =>
      // Then
      assert(allChecksPassed)
    }
  }

  @Test
  def testItRunsAllSatisfiedChecks(): Unit = {
    // Given
    val sut = new SecurityChecklist(List.fill(5)(passingCheck -> List.empty))

    // When
    sut.run().foreach { allChecksPassed =>
      // Then
      assert(allChecksPassed)
      assertEquals(5, numSatisfiedChecks)
      assertEquals(0, numUnsatisfiedChecks)
    }
  }

  @Test
  def testItDoesNotRunChecksAfterFirstUnsatisfiedCheck(): Unit = {
    // Given
    val checksAndActions = List(
      passingCheck -> List.empty,
      failingCheck -> List.empty,
      passingCheck -> List.empty,
      passingCheck -> List.empty
    )

    val sut = new SecurityChecklist(checksAndActions)

    // When
    sut.run().foreach { allChecksPassed =>
      // Then
      assert(!allChecksPassed)
      assertEquals(1, numSatisfiedChecks)
      assertEquals(1, numUnsatisfiedChecks)
    }
  }

  @Test
  def testItRunsActionsOnlyForFirstUnsatisfiedCheck(): Unit = {
    // Given
    var firstExecuted = false
    var secondActionExecuted = false

    val checksAndActions = List(
      passingCheck -> List.empty,
      failingCheck -> List(new MockAction(() => firstExecuted = true)),
      failingCheck -> List(new MockAction(() => secondActionExecuted = true))
    )

    val sut = new SecurityChecklist(checksAndActions)

    // When
    sut.run().foreach { allChecksPassed =>
      // Then
      assert(!allChecksPassed)
      assertEquals(1, numSatisfiedChecks)
      assertEquals(1, numUnsatisfiedChecks)
      assert(firstExecuted)
      assert(!secondActionExecuted)
    }
  }

  @Test
  def testItRunsActionsActionsInOrder(): Unit = {
    // Given
    val actionOrder = new ListBuffer[Int]()

    val checksAndActions = List(
      passingCheck -> List.empty,
      failingCheck -> List(new MockAction(() => actionOrder.append(1))),
      failingCheck -> List(new MockAction(() => actionOrder.append(2)))
    )

    val sut = new SecurityChecklist(checksAndActions)

    // When
    sut.run().foreach { allChecksPassed =>
      // Then
      assert(!allChecksPassed)
      assertEquals(1, numSatisfiedChecks)
      assertEquals(1, numUnsatisfiedChecks)
      assertArrayEquals(List(1, 2).toArray, actionOrder.toArray)
    }
  }
}

object SecurityChecklistTest {
  type Block = () => Unit
}

// Mocks //////////////////////////////////////////////////////////////////////////

class MockCheck(shouldPass: Boolean, onPass: Block, onFail: Block) extends Check {
  override def isSatisfied: Future[Boolean] = {
    if (shouldPass) onPass()
    else onFail()
    Future.successful(shouldPass)
  }
}

class MockAction(block: () => Unit) extends Action {
  override def execute(): Future[Unit] = {
    block()
    Future.successful(())
  }
}
