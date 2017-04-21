/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SharingCommandReceiver extends BroadcastReceiver {
    private static final String PACKAGE_COMMAND = "package";
    private static final String FILENAME_COMMAND = "filename";

    // am broadcast -a com.wire.testinggallery.sharing --es package <package name> --es filename <name>
    @Override
    public void onReceive(Context context, Intent intent) {
        String requestedPackage = intent.getStringExtra(PACKAGE_COMMAND);
        if (isPackageExists(requestedPackage, context)) {

            String fileName = intent.getStringExtra(FILENAME_COMMAND);
            String[] fileNames = fileName.split(",");
            if (fileNames.length > 0) {

                if (fileNames.length == 1) {
                    shareOneFile(context, requestedPackage, fileNames[0]);
                } else {
                    shareManyFiles(context, requestedPackage, fileNames);
                }

            }

        }
    }

    private void shareManyFiles(Context context, String requestedPackage, String... fileNames) {
        List<File> files = new ArrayList<>();
        for (String fileName : fileNames) {
            File sharingFile = getSharingFile(fileName);
            if (sharingFile.exists()) {
                files.add(sharingFile);
            } else {
                throw new Resources.NotFoundException(
                    String.format("File [%s] not Found!", sharingFile.getAbsoluteFile())
                );
            }
        }
        shareMultipleFilesToPackage(context, requestedPackage, files);
        setResultCode(Activity.RESULT_OK);

    }

    private void shareOneFile(Context context, String requestedPackage, String fileName) {
        File file = getSharingFile(fileName);
        if (file.exists()) {
            shareFileToPackage(context, requestedPackage, file);
            setResultCode(Activity.RESULT_OK);
        }
    }

    private void shareMultipleFilesToPackage(Context context, String requestedPackage, List<File> files) {
        Intent shareIntent = createFilesShareIntent(requestedPackage, files);
        context.startActivity(shareIntent);
    }

    private void shareFileToPackage(Context context, String requestedPackage, File file) {
        Intent shareIntent = createFileShareIntent(requestedPackage, file);
        context.startActivity(shareIntent);
    }

    private Intent createFilesShareIntent(String requestedPackage, List<File> files) {
        Intent shareIntent = createShareIntent(requestedPackage);
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, getUrisFromFiles(files));
        return shareIntent;
    }

    private ArrayList<Uri> getUrisFromFiles(List<File> files) { //NOPMD
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            uris.add(Uri.fromFile(file));
        }
        return uris;
    }

    @NonNull
    private Intent createFileShareIntent(String requestedPackage, File file) {
        Intent shareIntent = createShareIntent(requestedPackage);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        return shareIntent;
    }

    @NonNull
    private Intent createShareIntent(String requestedPackage) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.setPackage(requestedPackage);
        shareIntent.addCategory(Intent.CATEGORY_DEFAULT);
        shareIntent.setType("*/*");
        return shareIntent;
    }

    private boolean isPackageExists(String requestedPackage, Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.getLaunchIntentForPackage(requestedPackage) != null;
    }

    @NonNull
    private File getSharingFile(String fileName) {
        File wireTestingFilesDirectory = DocumentResolver.WIRE_TESTING_FILES_DIRECTORY;
        return new File(wireTestingFilesDirectory, fileName);
    }
}
