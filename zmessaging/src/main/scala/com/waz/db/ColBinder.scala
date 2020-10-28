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
package com.waz.db

import com.waz.utils.wrappers.{DBContentValues, DBCursor, DBProgram}

case class ColBinder[A, B](col: Col[A], extractor: B => A, var index: Int = 0) {
  def apply(value: A): String = col.sqlLiteral(value)
  def load(cursor: DBCursor, index: Int): A = col.load(cursor, index)
  def save(value: B, values: DBContentValues): Unit = col.save(extractor(value), values)
  def bind(value: B, stmt: DBProgram): Unit = col.bind(extractor(value), index + 1, stmt)
  def bindCol(value: A, stmt: DBProgram): Unit = col.bind(value, index + 1, stmt)
  def name: String = col.name
}
