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
package com.waz.zclient.testutils;

import com.waz.api.UiObservable;
import com.waz.api.UpdateListener;
import timber.log.Timber;

import java.util.HashSet;
import java.util.Set;

public class MockObservable implements UiObservable, Comparable<MockObservable> {

    protected final int id;

    Set<UpdateListener> updateListeners = new HashSet<>();

    public MockObservable(int id) {
        this.id = id;
    }

    public void triggerInternalUpdate() {
        for (UpdateListener listener : updateListeners) {
            listener.updated();
        }
    }

    @Override
    public void addUpdateListener(UpdateListener listener) {
        Timber.d("%s: addUpdateListener", this);
        updateListeners.add(listener);
    }

    @Override
    public void removeUpdateListener(UpdateListener listener) {
        Timber.d("%s: removeUpdateListener", this);
        updateListeners.remove(listener);
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", getClass().getSimpleName(), id);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MockObservable)) {
            return false;
        }
        return this.id == ((MockObservable) o).id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public int compareTo(MockObservable another) {
        return id - another.id;
    }
}
