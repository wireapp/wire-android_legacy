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
package com.waz.service.assets

import android.media.AudioFormat._
import libcore.io.SizeOf
import org.threeten.bp.Duration

object PCM {
  val sampleRate = 44100 // samples per second
  val inputChannelConfig = CHANNEL_IN_MONO
  val outputChannelConfig = CHANNEL_OUT_MONO
  val sampleFormat = ENCODING_PCM_16BIT

  def durationFromByteCount(byteCount: Long): Duration = durationFromSampleCount(byteCount / SizeOf.SHORT)
  def durationFromSampleCount(sampleCount: Long): Duration = Duration.ofMillis(sampleCount * 1000L / sampleRate)
}
