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
package com.waz.zclient.controllers.drawing;

import com.waz.api.ImageAsset;

import java.util.HashSet;
import java.util.Set;

public class DrawingController implements IDrawingController {

    Set<DrawingObserver> observers = new HashSet<>();

    @Override
    public void addDrawingObserver(DrawingObserver drawingObserver) {
        observers.add(drawingObserver);
    }

    @Override
    public void removeDrawingObserver(DrawingObserver drawingObserver) {
        observers.remove(drawingObserver);
    }

    @Override
    public void showDrawing(ImageAsset image, IDrawingController.DrawingDestination drawingDestination) {
        for (DrawingObserver observer : observers) {
            observer.onShowDrawing(image, drawingDestination, DrawingMethod.DRAW);
        }
    }

    @Override
    public void showDrawing(ImageAsset image,
                            IDrawingController.DrawingDestination drawingDestination,
                            DrawingMethod method) {
        for (DrawingObserver observer : observers) {
            observer.onShowDrawing(image, drawingDestination, method);
        }
    }

    @Override
    public void hideDrawing(IDrawingController.DrawingDestination drawingDestination, boolean imageSent) {
        for (DrawingObserver observer : observers) {
            observer.onHideDrawing(drawingDestination, imageSent);
        }
    }

    @Override
    public void tearDown() {
        observers.clear();
        observers = null;
    }
}
