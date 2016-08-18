/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.messages

import android.support.v7.widget.RecyclerView
import com.waz.ZLog._


abstract class RecyclerDataSet[K, V: Ordering](adapter: RecyclerView.Adapter[_]) extends IndexedSeq[V] {
  import RecyclerDataSet._

  private implicit val Tag: LogTag = s"RecyclerDataSet[${this.getClass.getName}]"

  private val ord = implicitly[Ordering[V]]

  var data = IndexedSeq.empty[V]

  def getId(v: V): K

  def apply(i: Int) = data(i)

  def length = data.length

  def set(items: IndexedSeq[V]) =
    diff(data, items).result foreach {
      case (Added(is), index) =>
        val (pref, suf) = data.splitAt(index)
        data = pref ++ is ++ suf
        verbose(s"notify range added")
        adapter.notifyItemRangeInserted(index, is.length)
      case (Removed(is), index) =>
        data = data.slice(0, index) ++ data.slice(index + is.length, data.length)
        adapter.notifyItemRangeRemoved(index, is.length)
        verbose(s"notify range removed")
      case (Updated(is), index) =>
        data = data.slice(0, index) ++ is ++ data.slice(index + is.length, data.length)
        adapter.notifyItemRangeChanged(index, is.length)
        verbose(s"notify range changed")
      case change =>
        warn(s"unexpected change event: $change")
    }

  def diff(from: Seq[V], to: Seq[V], acc: DiffList[V] = new DiffList(Nil)): DiffList[V] = (from.headOption, to.headOption) match {
    case (None, None)                                   => acc
    case (Some(fh), None)                               => acc.removed(from)
    case (None, Some(th))                               => acc.added(to)
    case (Some(fh), Some(th)) if fh == th               => diff(from.tail, to.tail, acc.unchanged(th))
    case (Some(fh), Some(th)) if getId(fh) == getId(th) => diff(from.tail, to.tail, acc.updated(th))
    case (Some(fh), Some(th)) if ord.gt(fh, th)         => diff(from, to.tail, acc.added(th))
    case (Some(fh), Some(th))                           => diff(from.tail, to, acc.removed(fh))
  }
}

object RecyclerDataSet {
  private implicit val Tag: LogTag = s"RecyclerDataSet"

  sealed trait Diff[+V] {
    val items: Seq[V]
  }
  case class Added[V](items: Seq[V]) extends Diff[V]
  case class Removed[V](items: Seq[V]) extends Diff[V]
  case class Unchanged[V](items: Seq[V]) extends Diff[V]
  case class Updated[V](items: Seq[V]) extends Diff[V]

  case class DiffList[V](diffs: List[Diff[V]]) {

    def unchanged(item: V) = diffs match {
      case Unchanged(is) :: ls => DiffList(Unchanged(is :+ item) :: ls)
      case ls                  => DiffList(Unchanged(Seq(item)) :: ls)
    }

    def updated(item: V) = diffs match {
      case Updated(is) :: ls => DiffList(Updated(is :+ item) :: ls)
      case ls                => DiffList(Updated(Seq(item)) :: ls)
    }

    def added(item: V) = diffs match {
      case Added(is) :: ls => DiffList(Added(is :+ item) :: ls)
      case ls              => DiffList(Added(Seq(item)) :: ls)
    }

    def added(items: Seq[V]) = diffs match {
      case Added(is) :: ls => DiffList(Added(is ++ items) :: ls)
      case ls              => DiffList(Added(items) :: ls)
    }

    def removed(item: V) = diffs match {
      case Removed(is) :: ls => DiffList(Removed(is :+ item) :: ls)
      case ls                => DiffList(Removed(Seq(item)) :: ls)
    }

    def removed(items: Seq[V]) = diffs match {
      case Removed(is) :: ls => DiffList(Removed(is ++ items) :: ls)
      case ls                => DiffList(Removed(items) :: ls)
    }

    def items =
      diffs.foldRight(IndexedSeq.newBuilder[V])((diff, b) => b ++= diff.items) .result()

    def result = {
      // TODO: detect item move - return one change instead of separate add and remove
      verbose(s"result, diffs: $diffs")

      diffs.foldRight((Seq.newBuilder[(Diff[V], Int)], 0)) { case (change, (builder, index)) =>
        change match {
          case Unchanged(items) => (builder, index + items.length)
          case d @ Updated(items) =>
            builder += (d -> index)
            (builder, index + items.length)
          case d @ Added(items) =>
            builder += (d -> index)
            (builder, index + items.length)
          case d @ Removed(items) =>
            builder += (d -> index)
            (builder, index)
        }
      } ._1.result()
    }
  }
}
