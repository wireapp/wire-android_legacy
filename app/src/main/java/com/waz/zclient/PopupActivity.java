/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.Toolbar;
import android.view.View;
import com.waz.zclient.core.controllers.tracking.events.notifications.OpenedQuickReplyEvent;
import com.waz.zclient.pages.main.popup.QuickReplyFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.ViewUtils;

public class PopupActivity extends BaseActivity implements QuickReplyFragment.Container {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewUtils.unlockOrientation(this);
        setContentView(R.layout.popup_reply);

        final Toolbar toolbar = ViewUtils.getView(this, R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("");
        toolbar.setNavigationIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.action_back_light, getTheme()));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KeyboardUtils.closeKeyboardIfShown(PopupActivity.this);
                onBackPressed();
            }
        });

        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        final String conversationId = IntentUtils.getLaunchConversationId(intent);
        if (conversationId == null) {
            finish();
            return;
        }

        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.fl__quick_reply__container,
                                            QuickReplyFragment.newInstance(conversationId),
                                            QuickReplyFragment.TAG)
                                   .commit();
    }

    @Override
    protected int getBaseTheme() {
        return R.style.Theme_Popup;
    }

    @Override
    public void onStart() {
        super.onStart();
        injectJava(GlobalTrackingController.class).tagEvent(new OpenedQuickReplyEvent());
    }
}
