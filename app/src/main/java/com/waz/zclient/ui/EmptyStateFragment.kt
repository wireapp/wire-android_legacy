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
package com.waz.zclient.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class EmptyStateFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_empty_state, container, false).apply {
            (this as? TextView)?.text = arguments!!.getString(KEY_EMPTY_STATE_TEXT)
        }
    }

    companion object {
        const val TAG = "EmptyStateFragment"

        private const val KEY_EMPTY_STATE_TEXT = "emptyStateText"

        @JvmStatic
        fun newInstance(emptyStateText: String): EmptyStateFragment = EmptyStateFragment().apply {
            arguments = Bundle().apply {
                putString(KEY_EMPTY_STATE_TEXT, emptyStateText)
            }
        }
    }
}
