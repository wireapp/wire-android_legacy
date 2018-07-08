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
package com.waz.zclient.controllers.location;

import com.waz.api.MessageContent;

import java.util.HashSet;
import java.util.Set;

public class LocationController implements ILocationController {

    private Set<LocationObserver> observers = new HashSet<>();

    @Override
    public void addObserver(LocationObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(LocationObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void showShareLocation() {
        for (LocationObserver observer : observers) {
            observer.onShowShareLocation();
        }
    }

    @Override
    public void hideShareLocation(MessageContent.Location location) {
        for (LocationObserver observer : observers) {
            observer.onHideShareLocation(location);
        }
    }

    @Override
    public void tearDown() {
        observers.clear();
    }


}
