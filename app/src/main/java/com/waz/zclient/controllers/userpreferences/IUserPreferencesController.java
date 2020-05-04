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
package com.waz.zclient.controllers.userpreferences;

import androidx.annotation.IntDef;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IUserPreferencesController {

    @IntDef({SEND_LOCATION_MESSAGE,
             LIKED_MESSAGE,
             DO_NOT_SHOW_SHARE_CONTACTS_DIALOG})
    @interface Action { }
    int SEND_LOCATION_MESSAGE = 0;
    int LIKED_MESSAGE = 1;
    int DO_NOT_SHOW_SHARE_CONTACTS_DIALOG = 2;

    void tearDown();

    void reset();

    void setVerificationCode(String code);

    void removeVerificationCode();

    String getVerificationCode();

    boolean hasVerificationCode();

    void setPerformedAction(@Action int action);

    boolean hasPerformedAction(@Action int action);

    void addRecentEmoji(String emoji);

    List<String> getRecentEmojis();

    void setUnsupportedEmoji(Collection<String> emoji, int version);

    Set<String> getUnsupportedEmojis();

    boolean hasCheckedForUnsupportedEmojis(int version);
}
