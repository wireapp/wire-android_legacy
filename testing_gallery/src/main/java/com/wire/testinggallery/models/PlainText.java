package com.wire.testinggallery.models;

import android.app.Activity;
import android.content.Intent;

public class PlainText extends FileType {

    private final String QA_TEST_MESSAGE = "QA TEST AUTOMATION";

    public PlainText(){
        position = -1;
        name = "Plaintext";
        mimeType = "plain/text";
        extension = "*";
    }

    public void handle(Activity activity){
        //special case, we have to return plain text, not a file
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.addCategory(Intent.CATEGORY_DEFAULT);
        activity.setResult(Activity.RESULT_OK, shareIntent.setType(mimeType).putExtra(Intent.EXTRA_TEXT, QA_TEST_MESSAGE));
        activity.finish();
    }
}
