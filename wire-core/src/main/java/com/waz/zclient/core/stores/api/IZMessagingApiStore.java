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
package com.waz.zclient.core.stores.api;

import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.stores.IStore;

public interface IZMessagingApiStore extends IStore {

    void addApiObserver(ZMessagingApiStoreObserver observer);

    void removeApiObserver(ZMessagingApiStoreObserver observer);

    boolean isInitialized();

    ZMessagingApi getApi();

    void logout();

    void delete();

    void tearDown();
}
