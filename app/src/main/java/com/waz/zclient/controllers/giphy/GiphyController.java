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
package com.waz.zclient.controllers.giphy;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class GiphyController implements IGiphyController {

    public static final int MAXIMUM_ALLOWED_WORD_COUNT = 6;
    public static final int MAX_GIPHY_CHARS = 16;
    private static final String GIPHY_QUERY_REGEX = "(\\/giphy)?(.*)";
    private Set<GiphyObserver> observers = new HashSet<>();

    @Override
    public void addObserver(GiphyObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(GiphyObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void tearDown() {
        observers.clear();
    }

    @Override
    public void searchRandom() {
        for (GiphyObserver observer : observers) {
            observer.onRandomSearch();
        }
    }

    @Override
    public void search(@NonNull String keyword) {
        for (GiphyObserver observer : observers) {
            observer.onSearch(keyword);
        }
    }

    @Override
    public void searchTrending() {
        for (GiphyObserver observer : observers) {
            observer.onTrendingSearch();
        }
    }

    @Override
    public void close() {
        final CopyOnWriteArraySet<GiphyObserver> giphyObservers = new CopyOnWriteArraySet<>(observers);
        for (GiphyObserver observer : giphyObservers) {
            observer.onCloseGiphy();
        }
    }

    @Override
    public void cancel() {
        final CopyOnWriteArraySet<GiphyObserver> giphyObservers = new CopyOnWriteArraySet<>(observers);
        for (GiphyObserver observer : giphyObservers) {
            observer.onCancelGiphy();
        }
    }

    @Override
    public boolean handleInput(@NonNull final String text) {
        final String input = text.trim();
        if (TextUtils.isEmpty(input)) {
            searchTrending();
        } else {
            final String query = extractSearchQuery(input);
            search(query);
        }

        return true;
    }

    @Override
    public boolean isInputAllowedForGiphy(@NonNull final String input) {
        final String query = extractSearchQuery(input);
        if (query.length() >= MAX_GIPHY_CHARS) {
            return false;
        }
        final String[] words = query.split(" ");
        return words.length <= MAXIMUM_ALLOWED_WORD_COUNT;
    }

    private String extractSearchQuery(String input) {
        return input.replaceFirst(GIPHY_QUERY_REGEX, "$2").trim();
    }
}
