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
package com.waz.service.conversation

import scala.collection.mutable

/**
 * Helper buffer detecting windows (holes) in events sequence.
 * 
 * Keeps track of last event sequence numbers in a ring buffer implemented on top of BitSet.
 * Executes callback whenever some window is found.
 * 
 * @param pos - initial buffer position from which we start looking for windows, numbers less then this will be ignored
  */
class SequenceWindowBuffer(var pos: Long, callback: (Long, Long) => Unit, size: Int = 1024) {
  val bits = new mutable.BitSet(size)

  var offset = 0
  var max = pos
  bits += offset

  def add(seq: Long): Unit = {
    if (seq > pos) {
      while (seq - pos >= size) { // trying to add number out of buffer, we need to advance the buffer until it fits
        advanceWindow(seq)
        skipFilled()
      }
      put(seq)
    }
  }

  def advanceTo(seq: Long): Unit = {
    while (pos < seq) {
      advanceWindow(seq)
      skipFilled()
    }
    if (seq > pos) put(seq)
  }

  def advanceWindow(max: Long): Unit = {
    assert(!bits((offset + 1) % size), s"next bit should be empty at pos: $pos")
    bits -= offset
    val start = pos
    do {
      pos += 1
      offset = (offset + 1) % size
    } while(pos < max && !bits(offset) && pos <= this.max)

    if (pos > this.max) { // shortcut - at this point buffer is already empty
      assert(bits.isEmpty, s"buffer should be empty, but is: $bits")
      offset = 0
      pos = max
    }

    if (pos > start + 1) callback(start, pos)
  }

  private def put(seq: Long): Unit = {
    if (seq > max) max = seq
    bits += ((seq - pos).toInt + offset) % size
    skipFilled()
  }

  private def skipFilled(): Unit =
    if (bits(offset)) {
      while (bits((offset + 1) % size)) {
        bits -= offset
        offset += 1
        pos += 1
        if (offset == size) offset = 0
      }
    }
}
