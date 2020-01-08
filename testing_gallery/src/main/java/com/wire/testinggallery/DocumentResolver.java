/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wire.testinggallery;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;

@TargetApi(Build.VERSION_CODES.FROYO)
public class DocumentResolver {

    private static final String TAG = "TestingGallery";
    private static final String WIRE_DIRECTORY = "wire";
    public static final File WIRE_TESTING_FILES_DIRECTORY =
        Environment.getExternalStoragePublicDirectory(WIRE_DIRECTORY);

    public static Uri getFile(String extension) {
        Log.i(TAG, String.format("Received request for Fileextension %s", extension));
        return fileQuery(extension);
    }

    private static Uri fileQuery(String acceptedExtension) {
        File[] files = WIRE_TESTING_FILES_DIRECTORY.listFiles();
        Log.i(TAG, String.format("%s files found in %s", files.length, WIRE_TESTING_FILES_DIRECTORY));
        File lastUpdatedFile = null;
        long theLastModifiedTime = 0;
        if (files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    continue;
                }
                long modifiedTime = file.lastModified();
                if (modifiedTime > theLastModifiedTime &&
                    fileHasAcceptableExtension(file, acceptedExtension)) {

                    theLastModifiedTime = modifiedTime;
                    lastUpdatedFile = file;
                }
            }
            if (lastUpdatedFile != null) {
                Uri uri = Uri.fromFile(lastUpdatedFile);
                Log.i(TAG, String.format("Returning recent file: %s", uri));
                return uri;
            } else {
                Log.w(TAG, String.format("There was %s files, but none of them selected",
                    files.length));
                return null;
            }
        }
        Log.w(TAG, "No files! Returning null!!");
        return null;
    }

    private static boolean fileHasAcceptableExtension(File file, String acceptedExtension) {
        String[] fileParts = file.getName().split("\\.");
        String fileExtension = fileParts[fileParts.length - 1].toLowerCase();
        return acceptedExtension.equals(fileExtension);
    }
}
