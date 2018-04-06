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
package com.wire.testinggallery.backup;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class ExportFile {
    private String platform;
    private String version;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("creation_time")
    private String creationTime;
    @SerializedName("client_id")
    private String clientId;

    public static ExportFile fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, ExportFile.class);
    }

    public String getUserId() {
        return userId;
    }
}
