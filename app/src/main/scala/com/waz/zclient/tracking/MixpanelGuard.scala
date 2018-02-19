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
package com.waz.zclient.tracking

import com.mixpanel.android.mpmetrics.{MPConfig, MixpanelAPI}
import com.mixpanel.android.util.OfflineMode
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.threading.CancellableFuture
import com.waz.utils.returning
import com.waz.zclient.{BuildConfig, WireContext}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MixpanelGuard {

  private def find[T](ref: AnyRef, name: String) = returning(ref.getClass.getDeclaredFields.find(_.getName == name)) { field =>
    if (field.isEmpty) warn(s"mixpanel field $name not found")
  }

  private def getField(ref: AnyRef, name: String): Option[AnyRef] = find(ref, name).map { field =>
    field.setAccessible(true)
    field.get(ref)
  }

  private def call(ref: AnyRef, name: String): Unit = {
    val method = ref.getClass.getDeclaredMethod(name)
    method.setAccessible(true)
    method.invoke(ref)
  }

  private def setField(ref: AnyRef, name: String, value: Any): Unit = find(ref, name).foreach { field =>
    field.setAccessible(true)
    field.set(ref, value)
  }

  private val offlineMode = new OfflineMode {
    override def isOffline: Boolean = true
  }

  private def status(m: MixpanelAPI): Unit = {
    val sessionTimeoutDuration = getField(m, CONFIG_NAME).map { _.asInstanceOf[MPConfig].getSessionTimeoutDuration }
    verbose(s"  $SESSION_TIMEOUT_NAME: $sessionTimeoutDuration")
    val isOffline = getField(m, CONFIG_NAME).flatMap { c => Option(c.asInstanceOf[MPConfig].getOfflineMode).map(_.isOffline) }
    verbose(s"  isOffline              : $isOffline")
    val automaticEventsEnabled = getField(m, DECIDE_NAME).flatMap { r => getField(r, AUTO_EVENTS_NAME) }.map(_.asInstanceOf[Boolean])
    verbose(s"  automaticEventsEnabled : $automaticEventsEnabled")
  }

  val CONFIG_NAME = "mConfig"
  val SESSION_TIMEOUT_NAME = "mSessionTimeoutDuration"
  val DECIDE_NAME = "mDecideMessages"
  val AUTO_EVENTS_NAME = "mAutomaticEventsEnabled"

  val MIXPANEL_CLOSE_DELAY = 3.seconds
}

class MixpanelGuard(cxt: WireContext) {

  import MixpanelGuard._

  private var mixpanel = Option.empty[MixpanelAPI]

  //For build flavours that don't have tracking enabled, this should be None
  private lazy val MixpanelApiToken = Option(BuildConfig.MIXPANEL_APP_TOKEN).filter(_.nonEmpty)

  def open(): Unit = if (mixpanel.isEmpty) {
    verbose(s"opening mixpanel instance")

    mixpanel = MixpanelApiToken.map(MixpanelAPI.getInstance(cxt.getApplicationContext, _))
    mixpanel.foreach { m =>
      getField(m, CONFIG_NAME).foreach { r => setField(r, SESSION_TIMEOUT_NAME, Int.MaxValue) }
      getField(m, CONFIG_NAME).foreach { _.asInstanceOf[MPConfig].setOfflineMode(null) }
      getField(m, DECIDE_NAME).foreach { r => setField(r, AUTO_EVENTS_NAME, true) }

      verbose(s"status after opening: ")
      status(m)
    }

    verbose(s"For build: ${BuildConfig.APPLICATION_ID}, mixpanel: $mixpanel was created using token: ${BuildConfig.MIXPANEL_APP_TOKEN}")
  }

  def close()(implicit ctx: ExecutionContext): Unit = mixpanel.foreach { m =>
    verbose(s"status before closing: ")
    status(m)

    verbose(s"closing mixpanel instance")

    m.reset()
    call(m, "flushNoDecideCheck")

    // the delay is necessary to give Mixpanel time for the flush
    // be sure not to call 'open' before that (TODO: synchronize with signals?)
    CancellableFuture.delayed(MIXPANEL_CLOSE_DELAY) {
      getField(m, CONFIG_NAME).foreach { r => setField(r, SESSION_TIMEOUT_NAME, 0) }
      getField(m, CONFIG_NAME).foreach { _.asInstanceOf[MPConfig].setOfflineMode(offlineMode) }
      getField(m, DECIDE_NAME).foreach { r => setField(r, AUTO_EVENTS_NAME, false) }

      verbose(s"status after closing: ")
      status(m)
    }

    mixpanel = None
  }

  def withApi[T](f: MixpanelAPI => T): Option[T] = mixpanel.map(f)
  def flush(): Unit = withApi { _.flush() }
}
