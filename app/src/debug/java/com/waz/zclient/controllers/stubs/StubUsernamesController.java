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
package com.waz.zclient.controllers.stubs;

import android.app.Activity;

import com.waz.api.Self;
import com.waz.zclient.controllers.usernames.IUsernamesController;
import com.waz.zclient.controllers.usernames.UsernamesControllerObserver;

public class StubUsernamesController implements IUsernamesController {
    @Override
    public void setActivity(Activity activity) {
    }

    @Override
    public boolean hasGeneratedUsername() {
        return false;
    }

    @Override
    public String getGeneratedUsername() {
        return null;
    }

    @Override
    public void startUsernameGenerator(String baseName) {

    }

    @Override
    public void setUser(Self self) {

    }

    @Override
    public void addUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver) {

    }

    @Override
    public void removeUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver) {

    }

    @Override
    public void logout() {

    }

    @Override
    public void tearDown() {

    }
}
