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

import android.net.Uri;
import android.view.View;
import com.waz.api.Message;
import com.waz.api.User;
import com.waz.zclient.controllers.singleimage.ISingleImageController;
import com.waz.zclient.controllers.singleimage.SingleImageObserver;

public class StubSingleImageController implements ISingleImageController {

    @Override
    public void hideSingleImage() {
        ;
    }

    @Override
    public Message getMessage() {
        return null;
    }

    @Override
    public View getImageContainer() {
        return null;
    }

    @Override
    public void removeSingleImageObserver(SingleImageObserver observer) {
        ;
    }

    @Override
    public void clearReferences() {
        ;
    }

    @Override
    public void addSingleImageObserver(SingleImageObserver observer) {
        ;
    }

    @Override
    public boolean isContainerOutOfScreen() {
        return false;
    }

    @Override
    public void tearDown() {
        ;
    }

    @Override
    public void setContainerOutOfScreen(boolean containerOutOfScreen) {
        ;
    }

    @Override
    public void updateViewReferences() {
        ;
    }

    @Override
    public void setViewReferences(View imageContainer) {

    }

    @Override
    public void showSingleImage(Message message) {
        ;
    }

    @Override
    public void showSingleImage(User user) {
        ;
    }

    @Override
    public void showVideo(Uri uri) {

    }

    @Override
    public void hideVideo() {

    }
}
