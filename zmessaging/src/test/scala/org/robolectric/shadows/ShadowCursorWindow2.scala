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
package org.robolectric.shadows

import java.util.concurrent.ConcurrentSkipListMap

import android.database.CursorWindow
import org.robolectric.annotation.{Implementation, Implements}

import scala.collection.JavaConverters._

@Implements(classOf[CursorWindow]) class ShadowCursorWindow2 extends ShadowCursorWindow

@Implements(classOf[CursorWindow]) object ShadowCursorWindow2 {
  @volatile var trackWindows = false

  val stacks = new ConcurrentSkipListMap[Int, Array[StackTraceElement]]()

  @Implementation def nativeCreate(name: String, cursorWindowSize: Int): Int = {
    val ptr = ShadowCursorWindow.nativeCreate(name, cursorWindowSize)
    if (trackWindows) stacks.put(ptr, Thread.currentThread.getStackTrace)
    ptr
  }

  @Implementation def nativeDispose(windowPtr: Int): Unit = {
    ShadowCursorWindow.nativeDispose(windowPtr)
    if (trackWindows) stacks.remove(windowPtr)
  }

  def printUnclosedWindows: Unit = {
    println("unclosed windows:")
    stacks.asScala.foreach { case (window, trace) =>
      println(s"  window: $window")
      trace.foreach(t => println(s"    $t"))
    }
  }
}
