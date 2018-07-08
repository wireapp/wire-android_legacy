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

public interface IDrawingController {

    public enum DrawingDestination {
        SINGLE_IMAGE_VIEW,
        CAMERA_PREVIEW_VIEW,
        SKETCH_BUTTON
    }

    public enum DrawingMethod {
        DRAW,
        EMOJI,
        TEXT
    }

    void addDrawingObserver(DrawingObserver drawingObserver);

    void removeDrawingObserver(DrawingObserver drawingObserver);

    void showDrawing(ImageAsset image, IDrawingController.DrawingDestination drawingDestination);

    void showDrawing(ImageAsset image, IDrawingController.DrawingDestination drawingDestination, DrawingMethod method);

    void hideDrawing(IDrawingController.DrawingDestination drawingDestination, boolean imageSent);

    void tearDown();
}
