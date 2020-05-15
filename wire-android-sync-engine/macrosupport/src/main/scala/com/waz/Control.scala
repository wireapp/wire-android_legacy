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

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

trait CacheLike[K, V <: AnyRef] {
  def getOrNull(k: K): V
  def put(k: K, v: V): Unit
}

object Control {
  def getOrUpdate[K, V <: AnyRef](cache: CacheLike[K, V])(k: K, body: V): V = macro ControlMacros.getOrUpdate[K, V]
  def getOrElse[K, V <: AnyRef](cache: CacheLike[K, V])(k: K, body: V): V = macro ControlMacros.getOrElse[K, V]
}

private object ControlMacros {
  def getOrUpdate[K, V <: AnyRef](c: Context)(cache: c.Expr[CacheLike[K, V]])(k: c.Expr[K], body: c.Expr[V]) = {
    import c.universe._
    q"""val x1 = $cache.getOrNull($k)
        if (x1 ne null) x1 else {
          val x2 = $body
          $cache.put($k, x2)
          x2
        }
    """
  }

  def getOrElse[K, V <: AnyRef](c: Context)(cache: c.Expr[CacheLike[K, V]])(k: c.Expr[K], body: c.Expr[V]) = {
    import c.universe._
    q"""val x = $cache.getOrNull($k)
        if (x ne null) x else $body
    """
  }
}
