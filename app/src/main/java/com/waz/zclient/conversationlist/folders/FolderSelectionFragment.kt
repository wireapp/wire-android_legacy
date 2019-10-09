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
package com.waz.zclient.conversationlist.folders

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.waz.zclient.R

class FolderSelectionFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FolderSelectionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_folder_selection, container, false) as RecyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = FolderSelectionAdapter(
            arguments!!.getStringArrayList(KEY_FOLDER_NAMES),
            arguments!!.getInt(KEY_CURRENT_FOLDER_INDEX),
            ::onFolderSelected
        )
        recyclerView.adapter = adapter

    }

    private fun onFolderSelected(index: Int) {
        (parentFragment as? FolderMoveListener)?.onNewFolderSelected(index)
    }

    companion object {
        const val TAG = "FolderSelectionFragment"

        const val KEY_FOLDER_NAMES = "folderNames"
        const val KEY_CURRENT_FOLDER_INDEX = "currentFolderIndex"

        @JvmStatic
        fun newInstance(folderNames: ArrayList<String>, currentFolderIndex: Int) =
            FolderSelectionFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(KEY_FOLDER_NAMES, folderNames)
                    putInt(KEY_CURRENT_FOLDER_INDEX, currentFolderIndex)
                }
            }
    }
}
