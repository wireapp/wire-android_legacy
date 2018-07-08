/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.conversationpager;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.conversationlist.ConversationListManagerFragment;
import com.waz.zclient.pages.BaseFragment;
import timber.log.Timber;

public class FirstPageFragment extends BaseFragment<FirstPageFragment.Container> implements OnBackPressedListener {

    public static final String TAG = FirstPageFragment.class.getName();

    public enum Page {
        CONVERSATION_LIST(ConversationListManagerFragment.class, ConversationListManagerFragment.Tag());

        Page(Class<? extends Fragment> clazz, String tag) {
            this.clazz = clazz;
            this.tag = tag;
        }
        public final Class<? extends Fragment> clazz;
        public final String tag;

    }

    private boolean openedFromBackStack;

    public static FirstPageFragment newInstance() {
        return new FirstPageFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        openedFromBackStack = savedInstanceState != null;

        return inflater.inflate(R.layout.fragment_pager_first, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (openedFromBackStack || getChildFragmentManager().findFragmentById(R.id.fl__first_page_container) != null) {
            return;
        }

        openPage(Page.CONVERSATION_LIST, new Bundle());
        getControllerFactory().getNavigationController().setLeftPage(com.waz.zclient.controllers.navigation.Page.CONVERSATION_LIST,
                                                        TAG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__first_page_container);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void openPage(Page page, Bundle args) {
        try {
            Fragment fragment = page.clazz.newInstance();
            fragment.setArguments(args);
            getChildFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fl__first_page_container, fragment, page.tag)
                .commit();
        } catch (Exception e) {
            Timber.e(e, "Failed opening fragment!");
        }
    }

    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__first_page_container);
        return fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed();
    }

    public interface Container {
    }
}
