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
package com.waz.zclient.controllers.confirmation;

import android.support.annotation.IntDef;

public interface IConfirmationController {

    @IntDef({CONVERSATION_LIST,
             PARTICIPANTS,
             USER_PROFILE,
             CONVERSATION
    })
    @interface ConfirmationMenuRequester { }

    int CONVERSATION_LIST = 0;
    int PARTICIPANTS = 1;
    int USER_PROFILE = 2;
    int CONVERSATION = 3;

    void tearDown();

    void addConfirmationObserver(ConfirmationObserver confirmationObserver);

    void removeConfirmationObserver(ConfirmationObserver confirmationObserver);

    void requestConfirmation(ConfirmationRequest confirmationRequest, @ConfirmationMenuRequester int requester);
}
