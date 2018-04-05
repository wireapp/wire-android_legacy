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
