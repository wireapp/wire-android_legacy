package com.waz.zclient.conversationlist.folders

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.waz.zclient.R

class FolderSelectionFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        //TODO
        return inflater.inflate(R.layout.activity_blank, container, false)
    }

    companion object {
        const val TAG = "FolderSelectionFragment"

        @JvmStatic
        fun newInstance() = FolderSelectionFragment()
    }
}
