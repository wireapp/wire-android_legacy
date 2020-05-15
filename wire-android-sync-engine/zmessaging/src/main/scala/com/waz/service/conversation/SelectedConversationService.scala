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
import com.waz.model.ConvId
import com.waz.utils.events.Signal

import scala.concurrent.Future

trait SelectedConversationService {
  def selectedConvIdPref: Preferences.Preference[Option[ConvId]]
  def selectedConversationId: Signal[Option[ConvId]]
  def selectConversation(id: Option[ConvId]): Future[Unit]
}

/**
 * Keeps track of general conversation list stats needed for display of conversations lists.
 */
class SelectedConversationServiceImpl(userPrefs: UserPreferences) extends SelectedConversationService {
  val selectedConvIdPref: Preferences.Preference[Option[ConvId]] = userPrefs.preference(SelectedConvId)

  def selectedConversationId: Signal[Option[ConvId]] = selectedConvIdPref.signal

  def selectConversation(id: Option[ConvId]): Future[Unit] = selectedConvIdPref := id
}
