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
package com.waz.zclient.controllers.singleimage;

import android.net.Uri;
import com.waz.api.Message;
import com.waz.api.User;

public interface SingleImageObserver {
    void onShowSingleImage(Message message);

    void onShowUserImage(User user);

    void onHideSingleImage();

    void updateSingleImageReferences();

    void onShowVideo(Uri uri);

    void onHideVideo();
}
