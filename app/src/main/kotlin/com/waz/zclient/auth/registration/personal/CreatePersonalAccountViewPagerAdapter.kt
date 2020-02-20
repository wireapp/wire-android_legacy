package com.waz.zclient.auth.registration.personal

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter


class CreatePersonalAccountViewPagerAdapter(fragmentManager: FragmentManager)
    : FragmentPagerAdapter(fragmentManager) {

    override fun getCount(): Int = TABS_COUNT

    override fun getItem(position: Int): Fragment {
        var fragment = Fragment()
        when (position) {
            EMAIL_TAB_POSITION -> fragment = CreatePersonalAccountWithEmailFragment.newInstance()
            PHONE_TAB_POSITION -> fragment = CreatePersonalAccountWithPhoneFragment.newInstance()
        }
        return fragment
    }

    override fun getPageTitle(position: Int): CharSequence {
        var title = ""
        when (position) {
            EMAIL_TAB_POSITION -> title = "EMAIL"
            PHONE_TAB_POSITION -> title = "PHONE"
        }
        return title
    }

    companion object {
        private const val EMAIL_TAB_POSITION = 0
        private const val PHONE_TAB_POSITION = 1
        private const val TABS_COUNT = 2
    }
}
