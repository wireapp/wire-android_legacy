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

public interface IGiphyController {
    void addObserver(GiphyObserver observer);

    void removeObserver(GiphyObserver observer);

    void tearDown();

    void searchRandom();

    void search(@NonNull String keyword);

    void searchTrending();

    void close();

    void cancel();

    boolean handleInput(@NonNull String text);

    boolean isInputAllowedForGiphy(String input);
}
