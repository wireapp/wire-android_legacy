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
package com.waz.zclient.conversation.folders.moveto

import java.util

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import android.util.SparseArray
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model.{ConvId, FolderData}
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.conversationlist.folders.{FolderMoveListener, FolderSelectionFragment}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.EmptyStateFragment
import com.waz.zclient.{FragmentHelper, R}

import scala.collection.JavaConverters._

class MoveToFolderFragment extends BaseFragment[MoveToFolderFragment.Container]
  with FolderMoveListener
  with FragmentHelper {

  import Threading.Implicits.Ui

  private lazy val convListController = inject[ConversationListController]
  private lazy val convId = getArguments.getSerializable(MoveToFolderFragment.KEY_CONV_ID).asInstanceOf[ConvId]

  private lazy val toolbar = view[Toolbar](R.id.fragment_move_to_folder_toolbar)

  private val folderIndexMap = new SparseArray[FolderData]()

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_move_to_folder, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    toolbar.foreach(_.setNavigationContentDescription(R.string.folders_move_to_folder_close))
    setUpClickListeners(view)

    for {
      customFolders <- convListController.getCustomFolders
      convFolderId <- convListController.getCustomFolderId(convId)
      sortedFolders = customFolders.sortBy(_.name.str)
      currentFolderIndex = convFolderId.fold(-1)(x => sortedFolders.indexWhere(f => f.id == x))
    } yield {

      folderIndexMap.clear()
      if (sortedFolders.isEmpty) {
        getChildFragmentManager.beginTransaction.replace(
          R.id.fragment_move_to_folder_framelayout_container,
          EmptyStateFragment.newInstance(getString(R.string.folders_no_custom_folder_found)),
          EmptyStateFragment.TAG
        ).commit()
      } else {
        sortedFolders.zipWithIndex.foreach { case (f, i) => folderIndexMap.put(i, f) }

        val folderNames = new util.ArrayList[String](sortedFolders.map(_.name.str).asJava)

        getChildFragmentManager.beginTransaction.replace(
          R.id.fragment_move_to_folder_framelayout_container,
          FolderSelectionFragment.newInstance(folderNames, currentFolderIndex),
          FolderSelectionFragment.TAG
        ).commit()
      }
    }
  }

  private def setUpClickListeners(view: View): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(
      new View.OnClickListener {
        override def onClick(v: View): Unit = getContainer.onCloseScreenClicked()
      }
    ))
    view.findViewById[View](R.id.fragment_move_to_folder_textview_create).setOnClickListener(
      new View.OnClickListener {
        override def onClick(v: View): Unit = getContainer.onPrepareNewFolderClicked()
      }
    )
  }

  override def onNewFolderSelected(index: Int): Unit = {
    val folder = folderIndexMap.get(index)
    for {
      _ <- convListController.moveToCustomFolder(convId, folder.id)
    } yield {
      getContainer.onConvFolderChanged()
    }
  }
}

object MoveToFolderFragment {
  val TAG = classOf[MoveToFolderFragment].getSimpleName
  val KEY_FOLDER_INDEX_MAP = "folderindexMap"

  trait Container {
    def onCloseScreenClicked(): Unit
    def onPrepareNewFolderClicked(): Unit
    def onConvFolderChanged(): Unit
  }

  val KEY_CONV_ID = "convId"

  def newInstance(convId: ConvId) = {
    returning(new MoveToFolderFragment()) { fragment =>
      val bundle = new Bundle()
      bundle.putSerializable(KEY_CONV_ID, convId)
      fragment.setArguments(bundle)
    }
  }
}
