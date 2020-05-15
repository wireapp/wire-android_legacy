package com.waz.zclient.feature.auth.registration.personal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.showKeyboard
import kotlinx.android.synthetic.main.fragment_create_personal_account.*

class CreatePersonalAccountFragment : Fragment(R.layout.fragment_create_personal_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewPager()
        showKeyboard()
    }

    private fun initViewPager() {
        val titles = listOf(
            getString(R.string.authentication_tab_layout_title_email),
            getString(R.string.authentication_tab_layout_title_phone)
        )
        createPersonalAccountViewPager.adapter = CreatePersonalAccountViewPagerAdapter(
            childFragmentManager,
            titles
        )
    }

    companion object {
        fun newInstance() = CreatePersonalAccountFragment()
    }
}
