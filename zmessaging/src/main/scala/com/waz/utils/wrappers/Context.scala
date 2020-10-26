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
package com.waz.utils.wrappers

import android.content.{Context => AContext}
import scala.language.implicitConversions

//TODO break up the context into smaller wrappers and also wrap other objects used here.
trait Context {
  def startService(intent: Intent): Boolean
  def getSystemService[A](name: String): A
  def sendBroadcast(intent: Intent): Unit
}

class AndroidContext(val context: AContext) extends Context {
  override def startService(intent: Intent) = Option(context.startService(intent)).isDefined
  override def getSystemService[A](name: String) = context.getSystemService(name).asInstanceOf[A]
  override def sendBroadcast(intent: Intent) = context.sendBroadcast(intent)
}

object Context {
  implicit def wrap(aContext: AContext): Context = new AndroidContext(aContext)
  implicit def unwrap(context: Context): AContext = context match {
    case a:AndroidContext => a.context
    case _ => null
  }
}
