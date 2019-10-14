/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.conversationlist

import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.ConversationFoldersUiState
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{FolderId, Uid}
import com.waz.threading.Threading.Implicits.Ui
import com.waz.utils.events.Signal
import com.waz.zclient.conversationlist.FolderStateController._
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class FolderStateController(implicit val injector: Injector)
  extends Injectable
    with DerivedLogTag {

  private lazy val userPreferences = inject[Signal[UserPreferences]]

  lazy val folderUiStates: Signal[Map[Uid, Boolean]] = for {
    prefs  <- userPreferences
    states <- prefs(ConversationFoldersUiState).signal
  } yield states

  def update(folderState: FolderState): Future[Unit] = for {
    state <- folderUiStates.head
    _     <- store(state + (folderState.id -> folderState.isExpanded))
  } yield {}

  def prune(folderIds: Set[Uid]): Future[Unit] = for {
    state               <- folderUiStates.head
    knownStates          = state.keySet
    unusedFolderStates   = knownStates -- folderIds
    _                   <- store(state -- unusedFolderStates)
  } yield {}

  private def store(states: Map[Uid, Boolean]): Future[Unit] = for {
    prefs <- userPreferences.head
    _     <- prefs(ConversationFoldersUiState).update(states)
  } yield {}
}

object FolderStateController {

  case class FolderState(id: Uid, isExpanded: Boolean)

  object FolderState {

    def apply(folderId: FolderId, isExpanded: Boolean): FolderState = {
      FolderState(Uid(folderId.str), isExpanded)
    }
  }
}
