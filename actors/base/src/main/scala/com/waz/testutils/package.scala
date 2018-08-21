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
package com.waz

import android.os.Parcel
import com.waz.api.impl._
import com.waz.api.{CoreList, IConversation}
import com.waz.content.MsgCursor
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading

import scala.collection.breakOut
import scala.language.implicitConversions

package object testutils {
  private implicit lazy val ec = Threading.Background

  object Implicits {

    implicit class MessagesCursorSeq(list: MsgCursor) extends Seq[MessageAndLikes] {
      override def length: Int = list.size
      override def apply(idx: Int): MessageAndLikes = list(idx)
      override def iterator: Iterator[MessageAndLikes] = new Iterator[MessageAndLikes]() {
        var idx = 0
        override def hasNext: Boolean = idx < list.size
        override def next(): MessageAndLikes = {
          idx += 1
          list(idx - 1)
        }
      }
    }

    implicit class EnrichedInt(val a: Int) extends AnyVal {
      def times(f: => Unit): Unit = {
        require(a >= 1, "number of times should be at least 1")
        (1 to a) foreach (_ => f)
      }
    }

    implicit class CoreListAsScala[A](list: CoreList[A]) {
      def asScala: Vector[A] = (0 until list.size).map(list.get)(breakOut)
    }

    implicit class RichCoreListSeq[T](list: CoreList[T]) extends Seq[T] {
      override def length: Int = list.size
      override def apply(idx: Int): T = list.get(idx)
      override def iterator: Iterator[T] = new Iterator[T]() {
        var idx = 0
        override def hasNext: Boolean = idx < list.size
        override def next(): T = {
          idx += 1
          list.get(idx - 1)
        }
      }
    }
  }

  def withParcel[T](f: Parcel => T): T = {
    val p = Parcel.obtain()
    try f(p) finally p.recycle()
  }
}
