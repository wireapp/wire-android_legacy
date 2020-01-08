package com.wire.testinggallery.models;

import android.app.Activity;
import android.content.Intent;

import com.wire.testinggallery.DocumentResolver;

public class Textfile extends FileType {
    public Textfile(){
        position = 0;
        name = "textfile";
        mimeType = "*/*";
        extension = "txt";
    }

    public void handle(Activity activity){
        activity.setResult(Activity.RESULT_OK, new Intent().setData(new DocumentResolver(activity.getContentResolver()).getDocumentUri()));
        activity.finish();
    }
}
