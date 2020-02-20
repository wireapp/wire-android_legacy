package com.waz.zclient.auth.registration.personal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import kotlinx.android.synthetic.main.fragment_create_personal_account.*

class CreatePersonalAccountFragment : Fragment(R.layout.fragment_create_personal_account) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createPersonalAccountViewPager.adapter =
            CreatePersonalAccountViewPagerAdapter(requireActivity().supportFragmentManager)
    }

    companion object {
        fun newInstance() = CreatePersonalAccountFragment()
    }
}
