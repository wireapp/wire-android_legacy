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
package com.wire.testinggallery;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class CaptureDocumentActivity extends AppCompatActivity {

    public final String DEFAULT_PLAIN_TEXT = "QA AUTOMATION TEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String mime = getIntent().getType();

        if(mime == null)
            return;

        if (mime.startsWith("image")) {
            setResult(Activity.RESULT_OK, new Intent().setData(getResultUri("png")));
            finish();
        } else if (mime.equals("text/plain")) {
            //special case, we have to return plain text, not a file
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shareIntent.addCategory(Intent.CATEGORY_DEFAULT);
            setResult(Activity.RESULT_OK, shareIntent.setType("text/plain").putExtra(Intent.EXTRA_TEXT, DEFAULT_PLAIN_TEXT));
            finish();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select file to return");
            builder.setItems(new CharSequence[]
                    {"File (.txt)", "Video (.mp4)", "Image (.png)", "Audio (.m4a)", "Backup (.android_wbu)"},
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        switch (which) {
                            case 0:
                                setResult(Activity.RESULT_OK, new Intent().setData(getResultUri("txt")));
                                finish();
                                break;
                            case 1:
                                setResult(Activity.RESULT_OK, new Intent().setData(getResultUri("mp4")));
                                finish();
                                break;
                            case 2:
                                setResult(Activity.RESULT_OK, new Intent().setData(getResultUri("png")));
                                finish();
                                break;
                            case 3:
                                setResult(Activity.RESULT_OK, new Intent().setData(getResultUri("m4a")));
                                finish();
                                break;
                            case 4:
                                setResult(Activity.RESULT_OK, new Intent().setData(getResultUri("android_wbu")));
                                finish();
                                break;
                        }
                    }
                });
            builder.create().show();
        }
    }

    private Uri getResultUri(String fileType) {
        DocumentResolver resolver = new DocumentResolver(getContentResolver());
        //String mime = getIntent().getType();
        if (fileType.startsWith("png")) {
            return resolver.getImageUri();
        } else if (fileType.startsWith("mp4")) {
            return resolver.getVideoUri();
        } else if (fileType.equals("android_wbu")) {
            return resolver.getBackupUri();
        } else if (fileType.equals("txt")) {
            return resolver.getDocumentUri();
        } else if(fileType.equals("m4a")) {
            return resolver.getAudioUri();
        }
        return null;
    }
}
