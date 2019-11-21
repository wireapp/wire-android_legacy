package com.waz.zclient.settings.presentation.ui.misc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.utilities.extension.withArgs
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings_recycler_view.adapter = SettingsListAdapter(resources.getStringArray(R.array.settings_titles), resources.getStringArray(R.array.settings_icons))
    }

    companion object {
        fun newInstance() = SettingsFragment().withArgs {
            putString("", "")
        }
    }
}


