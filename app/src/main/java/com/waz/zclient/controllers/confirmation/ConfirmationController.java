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

import java.util.HashSet;
import java.util.Set;

public class ConfirmationController implements IConfirmationController {

    private Set<ConfirmationObserver> confirmationObservers;

    public ConfirmationController() {
        confirmationObservers = new HashSet<>();
    }

    @Override
    public void tearDown() {
        if (confirmationObservers != null) {
            confirmationObservers.clear();
            confirmationObservers = null;
        }
    }

    @Override
    public void addConfirmationObserver(ConfirmationObserver confirmationObserver) {
        confirmationObservers.add(confirmationObserver);
    }

    @Override
    public void removeConfirmationObserver(ConfirmationObserver confirmationObserver) {
        confirmationObservers.remove(confirmationObserver);
    }


    @Override
    public void requestConfirmation(ConfirmationRequest confirmationRequest, @ConfirmationMenuRequester int requester) {
        for (ConfirmationObserver confirmationObserver : confirmationObservers) {
            confirmationObserver.onRequestConfirmation(confirmationRequest, requester);
        }
    }
}
