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
package com.waz.zclient.controllers.singleimage;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SingleImageController implements ISingleImageController {

    private List<SingleImageObserver> observerList;
    private View imageContainer;

    public SingleImageController() {
        observerList = new ArrayList<>();
    }

    @Override
    public void addSingleImageObserver(SingleImageObserver observer) {
        observerList.add(observer);
    }

    @Override
    public void removeSingleImageObserver(SingleImageObserver observer) {
        observerList.remove(observer);
    }

    @Override
    public void hideSingleImage() {
        for (SingleImageObserver observer : observerList) {
            observer.onHideSingleImage();
        }
    }

    @Override
    public void showSingleImage(String messageId) {
        for (SingleImageObserver observer : observerList) {
            observer.onShowSingleImage(messageId);
        }
    }

    @Override
    public void setViewReferences(View imageContainer) {
        this.imageContainer = imageContainer;
    }

    @Override
    public View getImageContainer() {
        return imageContainer;
    }

    @Override
    public void tearDown() {
        observerList.clear();
        observerList = null;
        clearReferences();
    }

    @Override
    public void clearReferences() {
        imageContainer = null;
    }
}
