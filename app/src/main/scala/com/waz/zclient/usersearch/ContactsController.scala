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
package com.waz.zclient.usersearch

import java.util.Locale

import com.waz.ZLog.ImplicitTag._
import com.waz.service.ContactResult.ContactMethod
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class ContactsController()(implicit injector: Injector, ec: EventContext) extends Injectable {
  import Threading.Implicits.Background

  private val zms = inject[Signal[ZMessaging]]

  def invite(contactMethod: ContactMethod, message: String, locale: Locale): Future[Unit] =
    zms.head.flatMap(_.invitations.invite(contactMethod.contact.id, contactMethod.method, contactMethod.contact.name, message, Option(locale)))

}
