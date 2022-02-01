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

import com.waz.content.UserPreferences.SelectedConvId
import com.waz.content.{Preferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConvId
import com.waz.service.UserService
import com.wire.signals.Signal
import com.waz.log.LogSE._

import scala.concurrent.Future

trait SelectedConversationService {
  def selectedConvIdPref: Preferences.Preference[Option[ConvId]]
  def selectedConversationId: Signal[Option[ConvId]]
  def selectConversation(id: Option[ConvId]): Future[Unit]
}

/**
 * Keeps track of general conversation list stats needed for display of conversations lists.
 *
 * UserService is injected lazily (`users : => UserService` instead of `users: UserService`)
 * to prevent circular dependency.
 */
class SelectedConversationServiceImpl(userPrefs: UserPreferences, users: => UserService)
  extends SelectedConversationService with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  val selectedConvIdPref: Preferences.Preference[Option[ConvId]] = userPrefs.preference(SelectedConvId)

  val selectedConversationId: Signal[Option[ConvId]] = selectedConvIdPref.signal

  def selectConversation(id: Option[ConvId]): Future[Unit] = selectedConvIdPref := id

  selectedConversationId.foreach {
    case Some(convId) =>
      verbose(l"FCM selected conv: $convId")
      users.syncClients(convId)
    case _ =>
      verbose(l"FCM selected conv: None")
  }
}
