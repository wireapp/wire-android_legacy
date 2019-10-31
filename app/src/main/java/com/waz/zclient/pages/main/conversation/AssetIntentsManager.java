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
package com.waz.zclient.pages.main.conversation;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.waz.utils.IoUtils;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.Intents;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

import timber.log.Timber;

public class AssetIntentsManager {
    private static final String INTENT_GALLERY_TYPE = "image/*";
    private final Context context;
    private final Callback callback;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String openDocumentAction() {
        return Intent.ACTION_OPEN_DOCUMENT;
    }

    public AssetIntentsManager(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    private void openDocument(String mimeType, IntentType tpe, boolean allowMultiple) {
        if (BuildConfig.DEVELOPER_FEATURES_ENABLED) {
            // trying to load file from testing gallery,
            // this is needed because we are not able to override DocumentsUI on some android versions.
            Intent intent = new Intent("com.wire.testing.GET_DOCUMENT").setType(mimeType);
            if (!context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_ALL).isEmpty()) {
                callback.openIntent(intent, tpe);
                return;
            }
            Timber.i("Did not resolve testing gallery for intent: %s", intent.toString());
        }
        Intent documentIntent = new Intent(openDocumentAction()).setType(mimeType).addCategory(Intent.CATEGORY_OPENABLE);
        if (allowMultiple) {
            documentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        callback.openIntent(documentIntent, tpe);
    }

    public void openFileSharing() {
        openDocument("*/*", IntentType.FILE_SHARING, true);
    }

    public void openBackupImport() {
        openDocument("application/octet-stream", IntentType.BACKUP_IMPORT, false);
    }

    public void captureVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (intent.resolveActivity(this.context.getPackageManager()) != null) {
            callback.openIntent(intent, IntentType.VIDEO);
        }
    }

    public void openGallery() {
        openDocument(INTENT_GALLERY_TYPE, IntentType.GALLERY, false);
    }

    public void openGalleryForSketch() {
        openDocument(INTENT_GALLERY_TYPE, IntentType.SKETCH_FROM_GALLERY, false);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentType type = IntentType.get(requestCode);
        if (type == IntentType.UNKNOWN) {
            return false;
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            callback.onCanceled(type);
            return true;
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onFailed(type);
            return true;
        }

        Timber.d("onActivityResult - data: %s", Intents.RichIntent(data).toString());

        if(data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                callback.onDataReceived(type, new AndroidURI(clipData.getItemAt(i).getUri()));
            }
            return true;
        }

        if (data.getData() == null) {
            callback.onFailed(type);
            return false;
        }

        URI uri = new AndroidURI(data.getData());
        Timber.d("uri is %s", uri);
        if (type == IntentType.VIDEO) {
            uri = copyVideoToCache(uri);
        }

        if (uri != null) {
            callback.onDataReceived(type, uri);
        }
        return true;
    }

    @Nullable
    private URI copyVideoToCache(URI uri) {
        File mediaStorageDir = context.getExternalCacheDir();
        if (mediaStorageDir == null || !mediaStorageDir.exists()) {
            return null;
        }

        java.util.Date date = new java.util.Date();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(date.getTime());
        File targetFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
        Timber.d("target file is %s", targetFile.getAbsolutePath());

        if (targetFile.exists()) {
            targetFile.delete();
        }
        try {
            targetFile.createNewFile();
            InputStream inputStream = context.getContentResolver().openInputStream(AndroidURIUtil.unwrap(uri));
            if (inputStream != null) {
                IoUtils.copy(inputStream, targetFile);
            } else {
                Timber.e("Input stream is null for %s", uri);
            }
        } catch (IOException e) {
            Timber.e("Unable to save the file! %s", targetFile.getAbsolutePath());
            Timber.e(e);
            return null;
        } finally {
            context.getContentResolver().delete(AndroidURIUtil.unwrap(uri), null, null);
        }

        return new AndroidURI(FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", targetFile));
    }

    public enum IntentType {
        UNKNOWN(-1),
        GALLERY(9411),
        SKETCH_FROM_GALLERY(9416),
        VIDEO(9412),
        CAMERA(9413),
        FILE_SHARING(9414),
        BACKUP_IMPORT(9415);

        public int requestCode;

        IntentType(int requestCode) {
            this.requestCode = requestCode;
        }

        public static IntentType get(int requestCode) {
            if (requestCode == GALLERY.requestCode) {
                return GALLERY;
            }

            if (requestCode == SKETCH_FROM_GALLERY.requestCode) {
                return SKETCH_FROM_GALLERY;
            }

            if (requestCode == CAMERA.requestCode) {
                return CAMERA;
            }

            if (requestCode == VIDEO.requestCode) {
                return VIDEO;
            }

            if (requestCode == FILE_SHARING.requestCode) {
                return FILE_SHARING;
            }

            if (requestCode == BACKUP_IMPORT.requestCode) {
                return BACKUP_IMPORT;
            }

            return UNKNOWN;
        }
    }

    public interface Callback {
        void onDataReceived(IntentType type, URI uri);

        void onCanceled(IntentType type);

        void onFailed(IntentType type);

        void openIntent(Intent intent, AssetIntentsManager.IntentType intentType);
    }
}
