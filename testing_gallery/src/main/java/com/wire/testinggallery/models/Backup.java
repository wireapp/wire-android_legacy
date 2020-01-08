package com.wire.testinggallery.models;

import android.app.Activity;
import android.content.Intent;

import com.wire.testinggallery.DocumentResolver;
import com.wire.testinggallery.utils.Extensions;

public class Backup extends FileType {
    public Backup(){
        position = 4;
        name = "backup";
        mimeType = "application/octet-stream";
        extension = "android_wbu";
    }

    public void handle(Activity activity){
        activity.setResult(Activity.RESULT_OK, new Intent().setData(DocumentResolver.getFile(Extensions.BACKUP)));
        activity.finish();
    }
}
