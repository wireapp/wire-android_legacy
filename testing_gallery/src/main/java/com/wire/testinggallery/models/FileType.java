package com.wire.testinggallery.models;

import android.app.Activity;

public abstract class FileType {
    int position;
    String name;
    String extension;
    String mimeType;
    public abstract void handle(Activity activity);
    public int getPosition() {
        return position;
    }
    public String getName() {
        return name;
    }
    public String getExtension() {
        return extension;
    }
    public String getMimeType() {
        return mimeType;
    }


}
