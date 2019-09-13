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

import android.content.{Intent => AIntent}
import scala.language.implicitConversions

trait Intent {
  def setAction(action: String): Unit
  def putExtra(key: String, extra: String): Unit
}

class AndroidIntent(val intent: AIntent) extends Intent {
  override def setAction(action: String) = intent.setAction(action)
  override def putExtra(key: String, extra: String) = intent.putExtra(key, extra)
}

object Intent {
  private var util: IntentUtil = AndroidIntentUtil

  def setUtil(util: IntentUtil): Unit = this.util = util

  def apply(context: Context, clazz: Class[_]): Intent = util.apply(context, clazz)

  def scanFileIntent(uri: URI): Intent = util.scanFileIntent(uri: URI)

  implicit def wrap(aIntent: AIntent): Intent = new AndroidIntent(aIntent)
  implicit def unwrap(intent: Intent): AIntent = intent match {
    case i:AndroidIntent => i.intent
    case _ => null
  }
}

trait IntentUtil {
  def apply(context: Context, clazz: Class[_]): Intent

  def scanFileIntent(uri: URI): Intent
}

object AndroidIntentUtil extends IntentUtil {
  override def apply(context: Context, clazz: Class[_]) = new AndroidIntent(new AIntent(Context.unwrap(context), clazz))

  override def scanFileIntent(uri: URI) = new AndroidIntent(new AIntent(AIntent.ACTION_MEDIA_SCANNER_SCAN_FILE, URI.unwrap(uri)))
}
