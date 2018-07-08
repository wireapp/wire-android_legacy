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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BuildConfig;
import timber.log.Timber;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class AssetIntentsManager {
    public static final String SAVED_STATE_PENDING_URI = "SAVED_STATE_PENDING_URI";

    private static final String INTENT_GALLERY_TYPE = "image/*";
    private final PackageManager pm;

    @TargetApi(19)
    private static String openDocumentAction() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT;
    }

    private URI pendingFileUri;
    private Callback callback;

    public AssetIntentsManager(Activity activity, Callback callback, Bundle savedInstanceState) {
        setCallback(callback);

        if (savedInstanceState != null) {
            Uri uri = savedInstanceState.getParcelable(SAVED_STATE_PENDING_URI);
            if (uri != null) {
                pendingFileUri = new AndroidURI(uri);
            }
        }
        pm = activity.getPackageManager();
    }

    public AssetIntentsManager(Activity activity, Callback callback, URI pendingFileUri) {
        setCallback(callback);
        this.pendingFileUri = pendingFileUri;
        pm = activity.getPackageManager();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void openDocument(String mimeType, IntentType tpe, boolean allowMultiple) {
        if (BuildConfig.DEVELOPER_FEATURES_ENABLED) {
            // trying to load file from testing gallery,
            // this is needed because we are not able to override DocumentsUI on some android versions.
            Intent intent = new Intent("com.wire.testing.GET_DOCUMENT").setType(mimeType);
            if (!pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).isEmpty()) {
                callback.openIntent(intent, tpe);
                return;
            }
            Timber.i("Did not resolve testing gallery for intent: %s", intent.toString());
        }
        Intent documentIntent = new Intent(openDocumentAction()).setType(mimeType).addCategory(Intent.CATEGORY_OPENABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && allowMultiple) {
            documentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        callback.openIntent(documentIntent, tpe);
    }

    public void openFileSharing() {
        openDocument("*/*", IntentType.FILE_SHARING, true);
    }

    public void openBackupImport() {
        openDocument("*/*", IntentType.BACKUP_IMPORT, false);
    }

    public void captureVideo(Context context) {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        pendingFileUri = getOutputMediaFileUri(context, IntentType.VIDEO);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, AndroidURIUtil.unwrap(pendingFileUri));
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        }
        callback.openIntent(intent, IntentType.VIDEO);
    }

    public void openGallery() {
        openDocument(INTENT_GALLERY_TYPE, IntentType.GALLERY, false);
    }

    public void openGalleryForSketch() {
        openDocument(INTENT_GALLERY_TYPE, IntentType.SKETCH_FROM_GALLERY, false);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {

        if (callback == null) {
            throw new IllegalStateException("A callback must be set!");
        }

        IntentType type = IntentType.get(requestCode);

        if (type == IntentType.UNKNOWN) {
            return false;
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            callback.onCanceled(type);
            return true;
        }

        if (resultCode != Activity.RESULT_OK) {
            callback.onFailed(type);
            return true;
        }

        File possibleFile = null;
        if (pendingFileUri != null) {
            possibleFile = new File(pendingFileUri.getPath());
        }
        if ((type == IntentType.CAMERA || type == IntentType.VIDEO) &&
            possibleFile != null &&
            possibleFile.exists() &&
            possibleFile.length() > 0) {
                callback.onDataReceived(type, pendingFileUri);
            pendingFileUri = null;
        } else if (data != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                callback.onDataReceived(type, AndroidURIUtil.parse(data.getDataString()));
            } else if(data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    callback.onDataReceived(type, new AndroidURI(clipData.getItemAt(i).getUri()));
                }
            } else if (data.getData() != null) {
                callback.onDataReceived(type, new AndroidURI(data.getData()));
            } else {
                callback.onFailed(type);
            }
        } else {
            callback.onFailed(type);
        }

        return true;
    }

    /**
     * Create a file Uri for saving an image or video
     *
     * @param type
     */
    private static URI getOutputMediaFileUri(Context context, IntentType type) {
        File file = getOutputMediaFile(context, type);
        return file != null ? AndroidURIUtil.fromFile(file) : null;
    }

    /**
     * Create a File for saving an image or video
     *
     * @param type
     */
    private static File getOutputMediaFile(Context context, IntentType type) {
        File mediaStorageDir = context.getExternalCacheDir();
        if (mediaStorageDir == null || !mediaStorageDir.exists()) {
            return null;
        }

        java.util.Date date = new java.util.Date();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(date.getTime());

        switch (type) {
            case VIDEO:
                return new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
            case CAMERA:
                return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
        }
        return null;
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

    public void onSaveInstanceState(Bundle outState) {
        if (pendingFileUri != null) {
            outState.putParcelable(SAVED_STATE_PENDING_URI, AndroidURIUtil.unwrap(pendingFileUri));
        }
    }

    public interface Callback {
        void onDataReceived(IntentType type, URI uri);

        void onCanceled(IntentType type);

        void onFailed(IntentType type);

        void openIntent(Intent intent, AssetIntentsManager.IntentType intentType);
    }
}
