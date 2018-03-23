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
package com.waz.zclient.core.api.scala;

import com.waz.api.ErrorsList;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.stores.inappnotification.InAppNotificationStore;

public class ScalaInAppNotificationStore extends InAppNotificationStore implements ErrorsList.ErrorListener {

    private ErrorsList syncErrors;

    public ScalaInAppNotificationStore(ZMessagingApi zMessagingApi) {
        syncErrors = zMessagingApi.getErrors();
        syncErrors.addErrorListener(this);
    }

    @Override
    public void tearDown() {
        syncErrors.removeErrorListener(this);
        syncErrors = null;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Notifications
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void dismissError(String errorId) {
        for (int i = 0, length = syncErrors.size(); i < length; i++) {
            ErrorsList.ErrorDescription error = syncErrors.get(i);
            if (error.getId().equals(errorId)) {
                error.dismiss();
            }
        }
    }

    @Override
    public void onError(ErrorsList.ErrorDescription error) {
        notifySyncErrorObservers(error);
    }

}
