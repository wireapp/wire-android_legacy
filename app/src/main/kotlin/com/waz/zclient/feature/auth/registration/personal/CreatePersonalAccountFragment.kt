package com.waz.zclient.feature.auth.registration.personal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import kotlinx.android.synthetic.main.fragment_create_personal_account.*

class CreatePersonalAccountFragment : Fragment(R.layout.fragment_create_personal_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewPager()
        initTabLayout()
    }

    private fun initViewPager() {
        createPersonalAccountViewPager.adapter =
            CreatePersonalAccountViewPagerAdapter(requireActivity())
    }

    private fun initTabLayout() {
        val tabTitles = listOf(getString(R.string.authentication_tab_layout_title_email),
            getString(R.string.authentication_tab_layout_title_phone))
        //we cannot use this class until we upgrade material library to version 1.1.0
        //please see the note at ANDROIDX_MATERIAL in Dependencies.kt
//        TabLayoutMediator(createPersonalAccountTabLayout, createPersonalAccountViewPager) { tab, position ->
//            tab.text = tabTitles[position]
//        }.attach()
    }

    companion object {
        fun newInstance() = CreatePersonalAccountFragment()
    }
}
