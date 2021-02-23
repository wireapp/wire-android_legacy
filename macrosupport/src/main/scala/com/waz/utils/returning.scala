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

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object returning {
  def apply[A](init: A)(effects: A => Unit): A = macro KestrelMacro.apply[A]
}

object returningF { // fallback for the very rare case where macro expansion results in a compiler crash
  def apply[A](a: A)(body: A => Unit): A = { body(a); a }
}

private object KestrelMacro {
  def apply[A](c: blackbox.Context)(init: c.Tree)(effects: c.Tree): c.universe.Tree = {
    import c.universe._
    c.untypecheck(effects) match {
      case          Function(List(ValDef(_, t: TermName, _, EmptyTree)), b)  => q"val $t = $init; $b; $t"
      case Block(p, Function(List(ValDef(_, t: TermName, _, EmptyTree)), b)) => q"val $t = $init; $p; $b; $t"
      case _        /*  no inlining possible or necessary */                 => q"val x = $init; $effects(x); x"
    }
  }
}
