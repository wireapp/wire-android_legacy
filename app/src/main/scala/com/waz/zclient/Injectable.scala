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
package com.waz.zclient


trait Injectable {
  def inject[T: reflect.Manifest](implicit inj: Injector): T =
    inj.apply[T]()
}

case class Provider[T](fn: () => T) extends (() => T) {
  def apply() = fn()
}
case class Singleton[T](fn: () => T) extends (() => T) {
  lazy val value = fn()
  def apply() = value
}

trait Injector { self =>
  def binding[T: reflect.Manifest]: Option[() => T]

  def apply[T: reflect.Manifest](): T =
    binding[T].getOrElse(throw new Exception(s"No binding for: ${implicitly[reflect.Manifest[T]].runtimeClass.getName} in $this")).apply()

  private[zclient] val head = self
  private[zclient] val tail = self

  private[zclient] var parent = Option.empty[Injector]

  def ::(inj: Injector) = new Injector {
    inj.tail.parent = Some(self.head)

    override private[zclient] val head: Injector = inj.head
    override private[zclient] val tail: Injector = self.tail

    override def binding[T: reflect.Manifest]: Option[() => T] = head.binding

    override def toString: String = s"Injector($inj :: $self, parent: $parent)"
  }
}

class Module extends Injector with Injectable {
  protected implicit val inj: Injector = this

  private val bindings = new scala.collection.mutable.HashMap[reflect.Manifest[_], () => _]

  protected class Binding[T](cls: reflect.Manifest[T]) {
    def to(fn: => T) = bindings += cls -> Singleton(() => fn)
    def toProvider(fn: => T) = bindings += cls -> Provider(() => fn)
  }

  protected def bind[T: reflect.Manifest] = new Binding[T](implicitly[reflect.Manifest[T]])

  override def binding[T: Predef.Manifest]: Option[() => T] =
    internal(implicitly[reflect.Manifest[T]]).orElse(parent.flatMap(_.binding[T]))

  private[zclient] def internal[T](m: reflect.Manifest[T]) = bindings.get(m).asInstanceOf[Option[() => T]]

  override def toString: String = s"Module(bindings: $bindings, parent: $parent)"
}
