/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.utils

import com.waz.zclient.R
import android.content.Context
import com.waz.zclient.utils.ContextUtils.{getColor, getQuantityString, getString}

case class ResId(id: Int) extends AnyVal

trait ResValue[A] {
  def resolve(implicit ctx: Context): A
}

final case class ResString(resId: Int, quantity: Int, args: ResString.Args) extends ResValue[String] {
  import ResString._

  // in a rare case arguments to a ResString might be ResStrings themselves
  // it shouldn't go deeper than one level, so we shouldn't have to worry about stack overflow
  def resolve(implicit ctx: Context): String = (args, quantity) match {
    case (StringArgs(a), 0) if resId == 0 && a.nonEmpty => a.head
    case (StringArgs(a), 0)                             => getString(resId, a: _*)
    case (StringArgs(a), q) if q > 0                    => getQuantityString(resId, q, a: _*)
    case (ResStringArgs(a), 0)                          => getString(resId, a.map(_.resolve): _*)
    case (ResStringArgs(a), q) if q > 0                 => getQuantityString(resId, q, a.map(_.resolve): _*)
    case (AnyRefArgs(a), q)    if q > 0                 => getQuantityString(resId, q, a: _*)
    case _ => ""
  }

  // this way you can copy the resId from the logs as a hex and find its handle in R.java
  override def toString: String = s"ResString(${resId.toHexString.toLowerCase},$quantity,$args)"
}

object ResString {
  sealed trait Args

  final case class StringArgs   (args: List[String] = Nil)    extends Args
  final case class ResStringArgs(args: List[ResString] = Nil) extends Args
  final case class AnyRefArgs   (args: List[AnyRef] = Nil)    extends Args

  val Empty: ResString = ResString(R.string.empty_string, 0, StringArgs())

  def apply(resId: Int)                             : ResString = ResString(resId, 0, StringArgs())
  def apply(str: String)                            : ResString = ResString(0,     0, StringArgs(List(str)))
  def apply(resId: Int, args: String*)              : ResString = ResString(resId, 0, StringArgs(args.toList))
  def apply(resId: Int, quantity: Int, number: Int) : ResString = ResString(resId, quantity, AnyRefArgs(List(number.asInstanceOf[AnyRef], quantity.toString)))
  def apply(resId: Int, quantity: Int)              : ResString = ResString(resId, quantity, AnyRefArgs(List(quantity.toString)))

  // during the compilation both String* and ResString* undergo type erasure to Seq, so both apply(...) would have the same signature if ResString* was used
  def apply(resId: Int, args: List[ResString]): ResString = ResString(resId, 0, ResStringArgs(args))
}

final case class ResColor(input: Either[ResId, ResColor.Color]) extends ResValue[Int] {
  import ResColor._

  override def resolve(implicit ctx: Context): Int = input match {
    case Left(ResId(id)) => getColor(id)
    case Right(Color(v)) => v
  }
}

object ResColor {
  case class Color(v: Int) extends AnyVal

  def fromId(resId: Int): ResColor = apply(Left(ResId(resId)))
  def fromColor(color: Int): ResColor = apply(Right(Color(color)))

}
