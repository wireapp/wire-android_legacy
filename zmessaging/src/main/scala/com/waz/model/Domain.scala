package com.waz.model

final case class Domain(str: String) {
  def isDefined: Boolean = str.nonEmpty
  def isEmpty: Boolean = str.isEmpty
  def contains(str: String): Boolean = this.str == str
  def map[T](f: String => T): T = f(str)
  def mapOpt[T](f: String => T): Option[T] = if (isEmpty) None else Option(f(str))
  def toStringOpt: Option[String] = if (isEmpty) None else Some(str)
}

object Domain {
  val Empty: Domain = Domain("")

  def apply(optStr: Option[String]): Domain = optStr.fold(Domain.Empty)(Domain(_))
}
