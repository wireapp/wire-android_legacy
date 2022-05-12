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
package com.waz.utils

import com.waz.testutils.Matchers._
import java.io.Closeable

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FeatureSpec, Ignore, Inspectors, Matchers}

@RunWith(classOf[JUnitRunner])
class ManagedSpec extends FeatureSpec with Matchers with Inspectors {

  feature("Lazy evaluation") {
    scenario("Creation")(new Fixture {
      val closet = Managed(Closet(onCreate = () => firstHasBeenCreated = true))
      firstHasBeenCreated shouldBe false
      closet.acquire(_.browse(23)) shouldEqual 42
      firstHasBeenCreated shouldBe true
    })

    scenario("Execution")(new Fixture {
      val closet = Managed(Closet(browse = _ => throw Up(), onClose = () => firstIsClosed = true))
      expectThat [Up] isThrownBy(closet.acquire(_.browse(23)))
      firstIsClosed shouldBe true
    })
  }

  feature("Monadic resource management") {
    scenario("Happy path")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => {
          secondIsClosed shouldBe true
          firstIsClosed = true
        }))
        num = first.browse(111)
        second <- Managed(Closet(browse = a => num.toString + a.toString, onClose = () => {
          firstIsClosed shouldBe false
          secondIsClosed = true
        }))
      } yield second.browse(" - 23")

      result = mangagedCloset.acquire { v =>
        secondIsClosed shouldBe false
        firstIsClosed shouldBe false
        v + " - 5"
      }

      firstIsClosed shouldBe true
      secondIsClosed shouldBe true
      result shouldEqual "111 - 42 - 23 - 5"
    })

    scenario("Exception in first create")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(onCreate = () => throw Up(), browse = a => a.toString + " - 42", onClose = () => {
          secondIsClosed shouldBe true
          firstIsClosed = true
        }))
        num = first.browse(111)
        second <- Managed(Closet(onCreate = () => secondHasBeenCreated = true, browse = a => num.toString + a.toString, onClose = () => {
          firstIsClosed shouldBe false
          secondIsClosed = true
        }))
      } yield second.browse(" - 23")

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      firstIsClosed shouldBe false
      secondHasBeenCreated shouldBe false
      secondIsClosed shouldBe false
    })

    scenario("Exception in first browse")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(onCreate = () => firstHasBeenCreated = true, browse = _ => throw Up(), onClose = () => {
          secondHasBeenCreated shouldBe false
          secondIsClosed shouldBe false
          firstIsClosed = true
        }))
        num = first.browse(111)
        second <- Managed(Closet(onCreate = () => secondHasBeenCreated = true, browse = a => num.toString + a.toString, onClose = () => {
          firstHasBeenCreated shouldBe true
          firstIsClosed shouldBe false
          secondIsClosed = true
        }))
      } yield second.browse(" - 23")

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      firstHasBeenCreated shouldBe true
      firstIsClosed shouldBe true
      secondHasBeenCreated shouldBe false
      secondIsClosed shouldBe false
    })

    scenario("Exception in first close")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => throw Up()))
        num = first.browse(111)
        second <- Managed(Closet(browse = a => num.toString + a.toString, onClose = () => secondIsClosed = true))
      } yield second.browse(" - 23")

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      secondIsClosed shouldBe true
    })

    scenario("Exception in second create")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => firstIsClosed = true))
        num = first.browse(111)
        second <- Managed(Closet(onCreate = () => throw Up(), browse = a => num.toString + a.toString, onClose = () => secondIsClosed = true))
      } yield second.browse(" - 23")

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      firstIsClosed shouldBe true
      secondIsClosed shouldBe false
    })

    scenario("Exception in second browse")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => firstIsClosed = true))
        num = first.browse(111)
        second <- Managed(Closet(browse = _ => throw Up(), onClose = () => secondIsClosed = true))
      } yield second.browse(" - 23")

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      firstIsClosed shouldBe true
      secondIsClosed shouldBe true
    })

    scenario("Exception in second close")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => firstIsClosed = true))
        num = first.browse(111)
        second <- Managed(Closet(browse = a => num.toString + a.toString, onClose = () => throw Up()))
      } yield second.browse(" - 23")

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      firstIsClosed shouldBe true
    })

    scenario("Exception in first and second close")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => throw Back()))
        num = first.browse(111)
        second <- Managed(Closet(browse = a => num.toString + a.toString, onClose = () => throw Up()))
      } yield second.browse(" - 23")

      expectThat [Back] isThrownBy mangagedCloset.acquire(_ => throw Pillows())
    })

    scenario("Exception in final mapping")(new Fixture {
      val mangagedCloset = for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => firstIsClosed = true))
        num = first.browse(111)
        second <- Managed(Closet(browse = a => num.toString + a.toString, onClose = () => secondIsClosed = true))
      } yield throw Up()

      expectThat [Up] isThrownBy mangagedCloset.acquire(_ => throw Pillows())

      firstIsClosed shouldBe true
      secondIsClosed shouldBe true
    })

    scenario("Exception in flat map after map")(new Fixture {
      val first = Managed(Closet(browse = a => a.toString + " - 23", onClose = () => {
        secondIsClosed shouldBe true
        firstIsClosed = true
      }))
      val intermediate = first.map(_.browse("42"))
      val second = intermediate.flatMap(num => Managed(Closet(browse = a => throw Up(), onClose = () => {
        firstIsClosed shouldBe false
        secondIsClosed = true
      })))

      expectThat [Up] isThrownBy second.acquire(l => l.browse(111))

      firstIsClosed shouldBe true
      secondIsClosed shouldBe true
    })
  }

  feature("For-loop support") {
    scenario("Happy path")(new Fixture {
      for {
        first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => {
          secondIsClosed shouldBe true
          firstIsClosed = true
        }))
        num = first.browse(111)
        second <- Managed(Closet(browse = a => num.toString + a.toString, onClose = () => {
          firstIsClosed shouldBe false
          secondIsClosed = true
        }))
      } {
        secondIsClosed shouldBe false
        firstIsClosed shouldBe false
        result = second.browse(" - 23")
      }

      firstIsClosed shouldBe true
      secondIsClosed shouldBe true
      result shouldEqual "111 - 42 - 23"
    })

    scenario("Exception in second browse")(new Fixture {
      expectThat [Up] isThrownBy {
        for {
          first <- Managed(Closet(browse = a => a.toString + " - 42", onClose = () => {
            secondIsClosed shouldBe true
            firstIsClosed = true
          }))
          num = first.browse(111)
          second <- Managed(Closet(browse = _ => throw Up(), onClose = () => {
            firstIsClosed shouldBe false
            secondIsClosed = true
          }))
        } result = second.browse(" - 23")
      }

      firstIsClosed shouldBe true
      secondIsClosed shouldBe true
      result shouldBe None
    })
  }

  trait Fixture {
    var firstHasBeenCreated = false
    var firstIsClosed = false
    var secondHasBeenCreated = false
    var secondIsClosed = false
    var result: Any = None
  }
}

case class Closet(onCreate: () => Unit = () => (), browse: Any => Any = _ => 42, onClose: () => Unit = () => ()) extends Closeable {
  onCreate()

  override def close(): Unit = {
    onClose()
  }
}

case class Up() extends RuntimeException
case class Back() extends RuntimeException

case class Pillows() extends RuntimeException("unexpected: should never have been reached")
