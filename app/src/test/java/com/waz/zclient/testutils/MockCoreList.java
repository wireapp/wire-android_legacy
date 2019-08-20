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

import com.waz.api.CoreList;
import com.waz.api.UpdateListener;
import com.waz.sync.client.OtrClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MockCoreList<T> implements CoreList<T> {
    private List<T> elements = new ArrayList<>();
    private Set<UpdateListener> updateListeners = new HashSet<>();

    public void add(List<T> elements) {
        this.elements.addAll(elements);
        notifyUpdated();
    }

    public boolean add(T element) {
        if (elements.add(element)) {
            notifyUpdated();
            return true;
        }
        return false;
    }

    public boolean remove(OtrClient client) {
        if (elements.remove(client)) {
            notifyUpdated();
            return true;
        }
        return false;
    }

    @Override
    public T get(int position) {
        return elements.get(position);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public Iterator<T> iterator() {
        return elements.iterator();
    }

    @Override
    public void addUpdateListener(UpdateListener listener) {
        updateListeners.add(listener);
    }

    @Override
    public void removeUpdateListener(UpdateListener listener) {
        updateListeners.remove(listener);
    }

    private void notifyUpdated() {
        for (UpdateListener listener : updateListeners) {
            listener.updated();
        }
    }
}
