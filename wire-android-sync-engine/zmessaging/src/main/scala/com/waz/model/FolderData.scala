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
package com.waz.model

import com.waz.db.{Dao, Dao2}
import com.waz.model

import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, Managed}

case class FolderData(override val id: FolderId = FolderId(),
                      name: Name,
                      folderType: Int = FolderData.CustomFolderType)
  extends Identifiable[FolderId]

object FolderData {
  var CustomFolderType = 0
  val FavoritesFolderType = 1

  import com.waz.db.Col._
  implicit object FolderDataDao extends Dao[FolderData, FolderId] {
    val Id         = id[FolderId]('_id, "PRIMARY KEY").apply(_.id)
    val Name       = text[model.Name]('name, _.str, model.Name)(_.name)
    val FolderType = int('type).apply(_.folderType)

    override val idCol = Id
    override val table = Table("Folders", Id, Name, FolderType)

    override def apply(implicit cursor: DBCursor): FolderData = new FolderData(Id, Name, FolderType)

    def findForType(folderType: Int)(implicit db: DB): Managed[Iterator[FolderData]] = iterating(find(FolderType, folderType))
  }
}

case class ConversationFolderData(convId: ConvId, folderId: FolderId) extends Identifiable[(ConvId, FolderId)] {
  override val id: (ConvId, FolderId) = (convId, folderId)
}

object ConversationFolderData {
  import com.waz.db.Col._
  implicit object ConversationFolderDataDao extends Dao2[ConversationFolderData, ConvId, FolderId] {
    val ConvId   = id[ConvId]('conv_id).apply(_.convId)
    val FolderId = id[FolderId]('folder_id).apply(_.folderId)

    override val idCol = (ConvId, FolderId)
    override val table = Table("ConversationFolders", ConvId, FolderId)

    override def apply(implicit cursor: DBCursor): ConversationFolderData = new ConversationFolderData(ConvId, FolderId)

    def findForConv(convId: ConvId)(implicit db: DB) = iterating(find(ConvId, convId))
    def findForFolder(folderId: FolderId)(implicit db: DB) = iterating(find(FolderId, folderId))
  }
}
