package com.waz.zclient.settings.presentation.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.user.data.model.UserEntity
import kotlinx.android.synthetic.main.fragment_account.*

class AccountFragment : Fragment() {

    private val settingsViewModelFactory: SettingsViewModelFactory by lazy { SettingsViewModelFactory() }
    private lateinit var settingsAccountViewModel: SettingsAccountViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsAccountViewModel = ViewModelProvider(this, settingsViewModelFactory).get(SettingsAccountViewModel::class.java)

        settingsAccountViewModel.getProfile()

        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<UserEntity> {

            username.text = it.name
        })

    }

    companion object {
        fun newInstance() = AccountFragment()
    }
}


