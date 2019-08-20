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
package com.waz.zclient.preferences

import android.content.Context
import android.util.TypedValue
import com.waz.zclient.ui.utils.TextViewUtils

object DevicesPreferencesUtil {
  private val BOLD_PREFIX = "[["
  private val BOLD_SUFFIX = "]]"
  private val SEPARATOR = ' '
  private val NEW_LINE = '\n'

  def getFormattedFingerprint(context: Context, fingerprint: String): CharSequence = {
    val typedValue = new TypedValue
    val ta = context.obtainStyledAttributes(typedValue.data, Array[Int](android.R.attr.textColorPrimary))
    val highlightColor = ta.getColor(0, 0)
    ta.recycle()

    val formattedFingerprint = getFormattedFingerprint(fingerprint)
    TextViewUtils.getBoldHighlightText(context, formattedFingerprint, highlightColor, 0, formattedFingerprint.length)
  }

  private def getFormattedFingerprint(fingerprint: String): String = {
    getFormattedString(fingerprint, 2)
  }

  private def getFormattedString(string: String, chunkSize: Int): String = {
    var currentChunkSize = 0
    var bold = true
    val sb = new StringBuilder
    (0 until string.length).foreach { i =>
      if (currentChunkSize == 0 && bold) {
        sb.append(BOLD_PREFIX)
      }
      sb.append(string.charAt(i))
      currentChunkSize += 1
      if (currentChunkSize == chunkSize || i == string.length - 1) {
        if (bold) {
          sb.append(BOLD_SUFFIX)
        }
        bold = !bold
        if (i == string.length - 1) {
          sb.append(NEW_LINE)
        }
        else {
          sb.append(SEPARATOR)
        }
        currentChunkSize = 0
      }
    }
    sb.toString
  }
}
