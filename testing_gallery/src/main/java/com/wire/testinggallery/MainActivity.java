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
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.wire.testinggallery.backup.ExportFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.NameNotFoundException;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.widget.Toast.LENGTH_LONG;
import static com.wire.testinggallery.DocumentResolver.WIRE_TESTING_FILES_DIRECTORY;

public class MainActivity extends AppCompatActivity {
    private static final String COMMAND = "command";
    private static final String PACKAGE_NAME = "package";
    private static final String CUSTOM_TEXT = "text";
    private static final String COMMAND_SHARE_TEXT = "share_text";
    private static final String COMMAND_SHARE_IMAGE = "share_image";
    private static final String COMMAND_SHARE_VIDEO = "share_video";
    private static final String COMMAND_SHARE_AUDIO = "share_audio";
    private static final String COMMAND_SHARE_FILE = "share_file";
    private static final String COMMAND_CHECK_NOTIFICATION_ACCESS = "check_notification_access";
    private static final String DEFAULT_TEST_TEXT = "QA AUTOMATION TEST";
    private static final String DEFAULT_PACKAGE_NAME = "com.wire.candidate";
    private static final int TESTING_GALLERY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 23456789;
    private static final int TESTING_GALLERY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 23456790;


    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        showInfoUi();
        checkRightsAndDirectory();
        registerReceiver(broadcastReceiver,
            new IntentFilter("com.wire.testinggallery.main.receiver"));
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkRightsAndDirectory() {
        if (ActivityCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) !=
            PERMISSION_GRANTED) {
            requestPermissions(new String[]{READ_EXTERNAL_STORAGE},
                TESTING_GALLERY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
        if (ActivityCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) !=
            PERMISSION_GRANTED) {
            requestPermissions(new String[]{WRITE_EXTERNAL_STORAGE},
                TESTING_GALLERY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        if (!WIRE_TESTING_FILES_DIRECTORY.exists() && !WIRE_TESTING_FILES_DIRECTORY.mkdirs()) {
            showToast("Unable to create directory for testing files!!");
        }
    }

    private void showInfoUi() {
        ViewGroup view = findViewById(android.R.id.content);
        View mainView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.main_view, view, true);
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            TextView versionValueTextView = mainView.findViewById(R.id.version_value);
            versionValueTextView.setText(version);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @TargetApi(Build.VERSION_CODES.DONUT)
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent shareIntent;
            String command = intent.getStringExtra(COMMAND);
            String packageName = intent.getStringExtra(PACKAGE_NAME) == null ?
                DEFAULT_PACKAGE_NAME : intent.getStringExtra(PACKAGE_NAME);
            if (command.startsWith("share")) {
                if (command.equals(COMMAND_SHARE_TEXT)) {
                    String text = intent.getStringExtra(CUSTOM_TEXT);
                    if (text == null) {
                        text = DEFAULT_TEST_TEXT;
                    }
                    shareIntent = ShareCompat.IntentBuilder.from(MainActivity.this)
                        .setType("text/plain")
                        .setText(text)
                        .getIntent();
                } else {
                    Uri uri = getLatestAssetUriByCommand(command);
                    shareIntent = ShareCompat.IntentBuilder.from(MainActivity.this)
                        .setType(getContentResolver().getType(uri))
                        .setStream(uri)
                        .getIntent();
                }
                shareIntent.setPackage(packageName);
                startActivity(shareIntent);
            } else {
                switch (command) {
                    case COMMAND_CHECK_NOTIFICATION_ACCESS:
                        if (Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners").contains(getApplicationContext().getPackageName())) {
                            setResultData("VERIFIED");
                        } else {
                            setResultData("UNVERIFIED");
                        }
                        break;
                    default:
                        throw new RuntimeException(String.format("Cannot identify your command [%s]", command));
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    private Uri getLatestAssetUriByCommand(String command) {
        DocumentResolver resolver = new DocumentResolver(getContentResolver());
        switch (command) {
            case COMMAND_SHARE_FILE:
                return resolver.getDocumentUri();
            case COMMAND_SHARE_IMAGE:
                return resolver.getImageUri();
            case COMMAND_SHARE_VIDEO:
                return resolver.getVideoUri();
            case COMMAND_SHARE_AUDIO:
                return resolver.getAudioUri();
            default:
                throw new RuntimeException(String.format("Cannot identify the command : %s", command));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRightsAndDirectory();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            handleFile(uri);
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(),
            message, LENGTH_LONG)
            .show();
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void handleFile(Uri backupUri) {
        String fileName = getFilename(backupUri);
        if (!fileName.isEmpty()) {
            File targetFile = new File(String.format("%s/%s", WIRE_TESTING_FILES_DIRECTORY, fileName));
            if (targetFile.exists()) {
                targetFile.delete();
            }
            try {
                targetFile.createNewFile();
                InputStream inputStream = getContentResolver().openInputStream(backupUri);
                FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                assert inputStream != null;
                copyStreams(inputStream, fileOutputStream);

            } catch (IOException e) {
                setIntent(null);
                showAlert("Unable to save a file!");
                return;
            }

            try {
                if (fileName.toLowerCase().endsWith("_wbu")) {
                    ExportFile exportFile = ExportFile.fromJson(getFileFromArchiveAsString(targetFile, "export.json"));
                    setIntent(null);
                    showAlert(String.format("%s was saved\nBackup user id:%s", fileName, exportFile.getUserId()));
                    return;
                }
                setIntent(null);
                showToast(String.format("%s was saved", fileName));
                return;
            } catch (IOException e) {
                showAlert(String.format("There was an error during file analyze: %s", e.getLocalizedMessage()));
            }
        }
        showAlert("Received file has no name!!!");
    }

    private String getFilename(Uri uri) {
        Cursor cursor =
            getContentResolver().query(uri, null, null, null, null);
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        cursor.moveToFirst();
        return cursor.getString(nameIndex);
    }

    private void showAlert(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
        alertDialog.show();
    }

    private String getFileFromArchiveAsString(File zipFile, String desiredFileName) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OutputStream out = byteArrayOutputStream;
        FileInputStream fileInputStream = new FileInputStream(zipFile);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if (zipEntry.getName().equals(desiredFileName)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zipInputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.close();
                break;
            }
        }
        return new String(byteArrayOutputStream.toByteArray(), Charset.defaultCharset());
    }

    private void copyStreams(InputStream from, OutputStream to) {
        try {
            byte[] buffer = new byte[4096];
            int bytes_read;
            while ((bytes_read = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytes_read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (from != null) {
                try {
                    from.close();
                } catch (IOException ignored) {
                }
            }
            if (to != null) {
                try {
                    to.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
