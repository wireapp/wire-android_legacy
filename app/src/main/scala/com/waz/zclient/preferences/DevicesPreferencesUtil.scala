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
import android.text.format.DateFormat
import android.util.TypedValue
import com.waz.model.otr.Client
import com.waz.zclient.R
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.{RichClient, StringUtils, ZTimeFormatter}
import org.threeten.bp.{LocalDateTime, ZoneId}

object DevicesPreferencesUtil {
  private val BOLD_PREFIX = "[["
  private val BOLD_SUFFIX = "]]"
  private val SEPARATOR = ' '
  private val NEW_LINE = '\n'
  private val UNKNOWN_LOCATION = "?"

  def getTitle(context: Context, client: Client): CharSequence =
    TextViewUtils.getBoldText(context, context.getString(R.string.pref_devices_device_title, StringUtils.capitalise(client.model)))

  def getSummary(context: Context, client: Client, includeActivationSummary: Boolean): CharSequence = {
    val typedValue  = new TypedValue
    val a  = context.obtainStyledAttributes(typedValue.data, Array[Int](android.R.attr.textColorPrimary))
    val highlightColor = a.getColor(0, 0)
    a.recycle()
    val sb = new StringBuilder
    sb.append(context.getString(R.string.pref_devices_device_id, client.displayId))
    val highlightEnd = sb.length
    if (includeActivationSummary) {
      sb.append(NEW_LINE).append(NEW_LINE).append(getActivationSummary(context, client))
    }
    TextViewUtils.getBoldHighlightText(context, sb.toString, highlightColor, 0, highlightEnd)
  }

  private def getActivationSummary(context: Context, client: Client): String = {
    val now = LocalDateTime.now(ZoneId.systemDefault)
    val time = client.regTime match {
      case Some(regTime) => ZTimeFormatter.getSeparatorTime(context, now, LocalDateTime.ofInstant(regTime, ZoneId.systemDefault), DateFormat.is24HourFormat(context), ZoneId.systemDefault, false)
      case _ => ""
    }
    val regLocation = client.regLocation.fold("")(_.getName)
    context.getString(R.string.pref_devices_device_activation_summary, time, if (StringUtils.isBlank(regLocation)) UNKNOWN_LOCATION else regLocation)
  }

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
