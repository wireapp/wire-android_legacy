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
package com.waz.utils.wrappers

class JVMIntent(context: Option[Context] = None, clazz: Option[Class[_]]) extends Intent {

  private var action: Option[String] = None
  private var extras: Map[String, AnyRef] = Map.empty

  override def setAction(action: String) = this.action = Some(action)
  override def putExtra(key: String, extra: String) = extras = extras + (key -> extra)

  override def toString = s"$context, $clazz, $action, $extras"
}

object JVMIntentUtil extends IntentUtil {
  val ACTION_MEDIA_SCANNER_SCAN_FILE = "action.MEDIA_SCANNER_SCAN_FILE"

  override def apply(context: Context, clazz: Class[_]) = new JVMIntent(Some(context), Some(clazz))

  override def scanFileIntent(uri: URI): Intent = {
    val intent = new JVMIntent(None, None)
    intent.setAction(ACTION_MEDIA_SCANNER_SCAN_FILE)
    intent
  }
}
