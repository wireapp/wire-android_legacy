package com.wire.testinggallery.models;

import android.app.Activity;
import android.content.Intent;

import com.wire.testinggallery.DocumentResolver;

public class Video extends FileType {
    public Video() {
        position = 1;
        name = "video";
        mimeType = "video/mp4";
        extension = "mp4";
    }

    public void handle(Activity activity) {
        activity.setResult(Activity.RESULT_OK, new Intent().setData(new DocumentResolver(activity.getContentResolver()).getVideoUri()));
        activity.finish();
    }
}
