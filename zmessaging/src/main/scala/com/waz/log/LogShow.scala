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

import java.text.DecimalFormat

import com.waz.log.BasicLogging.Log
import com.waz.utils.sha2

import scala.collection.immutable.ListSet
import scala.reflect.ClassTag
import scala.util.Try

trait LogShow[-T] {
  def showSafe(value: T): String
  def showUnsafe(value: T): String = showSafe(value)

  def contramap[B](f: B => T): LogShow[B] = LogShow.create[B](f andThen showSafe, f andThen showUnsafe)
}

object LogShow {

  //Used to mark traits that are safe to print their natural toString implementation
  trait SafeToLog
  object SafeToLog {
    implicit val SafeToLogLogShow: LogShow[SafeToLog] = LogShow.logShowWithToString
  }

  // Use to show string.
  class ShowString(val value: String) extends AnyVal
  // Use to hide string content only in public logs.
  class RedactedString(val value: String) extends AnyVal
  // Use for size logging.
  class Size(val value: Long) extends AnyVal

  def apply[T: LogShow]: LogShow[T] = implicitly[LogShow[T]]

  def create[T](safe: T => String, unsafe: T => String): LogShow[T] = new LogShow[T] {
    override def showSafe(value: T): String = safe(value)
    override def showUnsafe(value: T): String = unsafe(value)
  }

  def create[T](safe: T => String): LogShow[T] = new LogShow[T] {
    override def showSafe(value: T): String = safe(value)
  }

  def createFrom[T](log: T => Log): LogShow[T] = new LogShow[T] {
    override def showSafe(value: T): String = log(value).buildMessageSafe
    override def showUnsafe(value: T): String = log(value).buildMessageUnsafe
  }

  //maybe we need to tune it
  //TODO provide ability to take set of "unsafe" fields, for which we search and apply the LogShow for their type
  import shapeless._
  import shapeless.ops.record.ToMap
  def create[T, H <: HList](hideFields: Set[String] = Set.empty, inlineFields: Set[String] = Set.empty, padding: Int = 2)
                           (implicit ct: ClassTag[T], lg: LabelledGeneric.Aux[T, H], tm: ToMap[H]): LogShow[T] =
    new LogShow[T] {
      override def showSafe(value: T): String = {
        val record = tm.apply(lg.to(value)).collect {
          case (k: Symbol, v) if !hideFields.contains(k.name) => k.name -> v
        }

        val (inlined, normal) = record.partition(t => inlineFields.contains(t._1))
        val builder = new StringBuilder(s"\n${ct.runtimeClass.getSimpleName}:\n")
        val paddingStr = String.valueOf(Array.fill(padding)(' '))

        val padTo = if (normal.isEmpty) 0 else normal.keySet.maxBy(_.length).length

        normal.foreach { case (fieldName, fieldValue) =>
          builder.append(paddingStr).append(String.format("%1$-" + (padTo + 1) + "s", fieldName + ":")).append(s" $fieldValue").append("\n")
        }

        if (inlined.nonEmpty) {
          builder.append(paddingStr).append("OTHER FIELDS: ")
          inlined.foreach { case (fieldName, fieldValue) =>
            builder.append(fieldName).append(" = ").append(fieldValue.toString).append(" | ")
          }
        }

        builder.toString()
      }
    }

  val logShowWithToString: LogShow[Any] = create(_.toString)

  val logShowWithHash: LogShow[Any] = new LogShow[Any] {
    private def name(v: Any): String = {
      try {
        v.getClass.getSimpleName
      } catch {
        case _: InternalError =>
          v.getClass.getName
      }
    }
    override def showSafe(v: Any): String = s"${name(v)}(${v.toString})"
    override def showUnsafe(v: Any): String = s"${name(v)}(${v.toString})"
  }

  implicit def traversableShow[T](implicit show: LogShow[T]): LogShow[Traversable[T]] = {
    def createString(xs: Traversable[T], obfuscate: T => String, elemsToPrint: Int = 3): String = {
      val end = if (xs.size > elemsToPrint) s" and ${xs.size - elemsToPrint} other elements..." else ""
      xs.take(elemsToPrint).map(e => obfuscate(e)).mkString("", ", ", end)
    }

    create(
      (xs: Traversable[T]) => createString(xs, show.showSafe),
      (xs: Traversable[T]) => createString(xs, show.showUnsafe)
    )
  }

  implicit def arrayShow[T: LogShow]: LogShow[Array[T]] =
    LogShow[Traversable[T]].contramap(_.toTraversable)

  implicit def listSetShow[T: LogShow]: LogShow[ListSet[T]] =
    LogShow[Traversable[T]].contramap(_.toTraversable)

  implicit def optionShow[T](implicit show: LogShow[T]): LogShow[Option[T]] =
    create(_.map(show.showSafe).toString, _.map(show.showUnsafe).toString)

  implicit def eitherShow[A,B](implicit showA: LogShow[A], showB: LogShow[B]): LogShow[Either[A,B]] =
    create(
      _.left.map(showA.showSafe).right.map(showB.showSafe).toString,
      _.left.map(showA.showUnsafe).right.map(showB.showUnsafe).toString
    )

  implicit def tryShow[T](implicit show: LogShow[T]): LogShow[Try[T]] =
    create(_.map(show.showSafe).toString, _.map(show.showUnsafe).toString)

  implicit def tuple2Show[A, B](implicit showA: LogShow[A], showB: LogShow[B]): LogShow[(A, B)] =
    create(
      t => (showA.showSafe(t._1), showB.showSafe(t._2)).toString(),
      t => (showA.showUnsafe(t._1), showB.showUnsafe(t._2)).toString())

  implicit def tuple3Show[A, B, C](implicit showA: LogShow[A], showB: LogShow[B], showC: LogShow[C]): LogShow[(A, B, C)] =
    create(
      t => (showA.showSafe(t._1), showB.showSafe(t._2), showC.showSafe(t._3)).toString(),
      t => (showA.showUnsafe(t._1), showB.showUnsafe(t._2), showC.showUnsafe(t._3)).toString()
    )

  //primitives
  implicit val BooleanLogShow: LogShow[Boolean] = logShowWithToString
  implicit val ByteLogShow: LogShow[Byte] = logShowWithToString
  implicit val ShortLogShow: LogShow[Short] = logShowWithToString
  implicit val IntLogShow: LogShow[Int] = logShowWithToString
  implicit val LongLogShow: LogShow[Long] = logShowWithToString
  implicit val FloatLogShow: LogShow[Float] = logShowWithToString
  implicit val DoubleLogShow: LogShow[Double] = logShowWithToString
  implicit val ThrowableShow: LogShow[Throwable] = logShowWithToString
  implicit val EnumShow: LogShow[Enum[_]] = LogShow.create((enumValue: Enum[_]) => enumValue.name())

  //utilities
  implicit val ShowStringLogShow: LogShow[ShowString] = create(_.value)
  implicit val RedactedStringShow: LogShow[RedactedString] = create(_ => "<redacted>", _.value)
  implicit val SizeLogShow: LogShow[Size] = create(wrapper => {
    if (wrapper.value <= 0) "0B"
    else {
      val units       = Array[String]("B", "kB", "MB", "GB", "TB")
      val digitGroups = (Math.log10(wrapper.value) / Math.log10(1024)).toInt
      new DecimalFormat("#,##0.#").format(wrapper.value / Math.pow(1024, digitGroups)) + " " + units(digitGroups)
    }
  })

  //default LogShow for all types
  implicit def defaultLogShowFor[T]: LogShow[T] = LogShow.logShowWithToString
}
