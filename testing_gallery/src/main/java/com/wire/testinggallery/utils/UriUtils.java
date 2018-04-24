package com.wire.testinggallery.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;

public class UriUtils {
    public static String getFilename(ContentResolver contentResolver, Uri uri, String scheme) {
        if (scheme == null || scheme.equals("file")) {
            return new File(uri.getPath()).getName();
        }
        if (scheme.equals("content")) {
            Cursor cursor =
                contentResolver.query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                String fileName = cursor.getString(nameIndex);
                cursor.close();
                return fileName;
            }
            return "";

        }
        return "";
    }
}
