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
package com.waz.zclient.controllers.usernames;

import android.app.Activity;

import com.waz.api.Self;

public interface IUsernamesController {

    void setActivity(Activity activity);

    boolean hasGeneratedUsername();

    String getGeneratedUsername();

    void startUsernameGenerator(String baseName);

    void setUser(Self self);

    void addUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver);

    void removeUsernamesObserver(UsernamesControllerObserver usernamesControllerObserver);

    void logout();

    void tearDown();
}
