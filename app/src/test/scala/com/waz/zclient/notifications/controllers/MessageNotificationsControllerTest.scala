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
package com.waz.zclient.notifications.controllers

import java.util.concurrent.TimeUnit

import android.app.NotificationManager
import android.content.Context
import com.waz.RobolectricUtils
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.testutils.MockZMessaging
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.utils.events.Signal
import com.waz.zclient.{Module, R}
import junit.framework.Assert._
import org.junit.runner.RunWith
import org.junit.{Before, Test}
import org.robolectric.annotation.Config
import org.robolectric.{Robolectric, RobolectricTestRunner}
import org.scalatest.junit.JUnitSuite
import org.scalatest.{Informer, Informing}
import org.threeten.bp.Instant

import scala.concurrent.duration.Duration

@RunWith(classOf[RobolectricTestRunner])
@Config(manifest = "src/test/AndroidManifest.xml", resourceDir = "../../build/intermediates/res/merged/dev/debug")
class MessageNotificationsControllerTest extends JUnitSuite with RobolectricUtils with Informing {

  import com.waz.utils.events.EventContext.Implicits.global

  implicit val timeout = Duration(1000, TimeUnit.MILLISECONDS)

  def notManager = context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
  def notManagerShadow = Robolectric.shadowOf(notManager)

  lazy val user = UserData(UserId(), "TestUser")
  lazy val conv = ConversationData(ConvId(user.id.str), RConvId(), None, user.id, ConversationType.OneToOne)

  var zms: ZMessaging = _
  var controller: MessageNotificationsController = _

  @Before
  def setUp(): Unit = {
    try {
      context.getResources.getString(R.string.pref_options_ringtones_text_key) // force resource loading
    } catch { case e: Throwable => e.printStackTrace() }

    zms = new MockZMessaging() {
      insertUser(user)
      insertConv(conv)
    }

    implicit val module = new Module {
      bind[Signal[ZMessaging]] to Signal.const(zms)
      bind[NotificationManager] to notManager
    }

    controller = new MessageNotificationsController()
  }

  @Test
  def getMessageForLike(): Unit = {
    val msg = controller.getMessage(NotificationInfo(NotId(), NotificationType.LIKE, Instant.now, "", ConvId()), false, true, true)
    assertFalse(msg.toString.isEmpty)
  }

  @Test
  def displayNotificationForReceivedLike(): Unit = {
    assertTrue(notManagerShadow.getAllNotifications.isEmpty)

    zms.notifStorage.insert(NotificationData(NotId(Uid().str), "", conv.id, user.id, NotificationType.LIKE, Instant.now))

    withDelay {
      assertEquals(1, notManagerShadow.getAllNotifications.size())
    }
  }

  override protected def info: Informer = new Informer {
    override def apply(message: String, payload: Option[Any]): Unit = println(message)
  }
}
