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
import com.waz.zclient.core.controllers.tracking.events.registration.SmsLinkClicked;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.IntentUtils;

public class SMSCodeReceiverActivity extends BaseActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smscode_receiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (IntentUtils.isSmsIntent(getIntent())) {
            forwardSmsCode(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (IntentUtils.isSmsIntent(intent)) {
            setIntent(intent);
            forwardSmsCode(intent);
        }
    }

    private void forwardSmsCode(Intent intent) {
        getControllerFactory().getVerificationController().setVerificationCode(IntentUtils.getSmsCode(intent));
        injectJava(GlobalTrackingController.class).tagEvent(new SmsLinkClicked());
        finish();
    }
}
