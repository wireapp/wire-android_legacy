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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Supplier;
import com.wire.testinggallery.backup.ExportFile;
import com.wire.testinggallery.precondition.PreconditionCheckers;
import com.wire.testinggallery.precondition.PreconditionFixers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static android.content.pm.PackageManager.NameNotFoundException;
import static com.wire.testinggallery.DocumentResolver.WIRE_TESTING_FILES_DIRECTORY;
import static com.wire.testinggallery.precondition.PreconditionsManager.requestSilentlyRights;
import static com.wire.testinggallery.utils.FileUtils.copyStreams;
import static com.wire.testinggallery.utils.FileUtils.getFileFromArchiveAsString;
import static com.wire.testinggallery.utils.InfoDisplayManager.showToast;
import static com.wire.testinggallery.utils.UriUtils.getFilename;

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

    private AlertDialog alertDialog = null;
    private Map<Integer, Supplier<Boolean>> checkMap;
    private Map<Integer, Supplier<Void>> fixMap;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        initCheckAndFix();
        showInfoUi();
        requestSilentlyRights(this);
        mapTableHandlers();
        checkPreconditions();
        registerReceiver(broadcastReceiver,
            new IntentFilter("com.wire.testinggallery.main.receiver"));
    }

    private void showInfoUi() {
        ViewGroup view = findViewById(android.R.id.content);
        View mainView = LayoutInflater.from(this).inflate(R.layout.main_view, view, true);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
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
                        if (Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners").contains(getPackageName())) {
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
        requestSilentlyRights(this);
        checkPreconditions();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            handleFile(uri, intent.getScheme());
        }
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void handleFile(Uri backupUri, String scheme) {
        String fileName = getFilename(getContentResolver(), backupUri, scheme);
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
                showToast(this, String.format("%s was saved", fileName));
                return;
            } catch (IOException e) {
                showAlert(String.format("There was an error during file analyze: %s", e.getLocalizedMessage()));
            }
        }
        showAlert("Received file has no name!!!");
    }

    private AlertDialog showAlert(String message) {
        if (alertDialog == null) {
            alertDialog = new AlertDialog.Builder(this).create();
        }
        alertDialog.hide();
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
        alertDialog.show();
        return alertDialog;
    }

    private void mapTableHandlers() {
        for (Integer id : fixMap.keySet()) {
            final Supplier<Void> fixSupplier = fixMap.get(id);

            Button fixButton = findViewById(id);
            fixButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fixSupplier.get();
                    checkPreconditions();
                }
            });
        }
    }

    private void checkPreconditions() {
        for (Integer id : checkMap.keySet()) {
            final Supplier<Boolean> checkSupplier = checkMap.get(id);

            TextView valueLabel = findViewById(id);
            if (checkSupplier.get()) {
                valueLabel.setTextAppearance(this, R.style.checkPass);
                valueLabel.setText(R.string.check_result_pass);
            } else {
                valueLabel.setTextAppearance(this, R.style.checkFail);
                valueLabel.setText(R.string.check_result_fail);
            }
        }
    }

    private void initCheckAndFix() {
        final PreconditionCheckers checkers = new PreconditionCheckers(this);
        checkMap = new HashMap<Integer, Supplier<Boolean>>() {{
            put(R.id.permissionsValue, checkers.permissionChecker());
            put(R.id.directoryValue, checkers.directoryChecker());
            put(R.id.getDocumentResolverValue, checkers.getDocumentResolverChecker());
            put(R.id.lockScreenValue, checkers.lockScreenChecker());
            put(R.id.notificationAccessValue, checkers.notificationAccessChecker());
            put(R.id.brightnessValue, checkers.brightnessCheck());
            put(R.id.stayAwakeValue, checkers.stayAwakeCheck());
            put(R.id.defaultVideoRecorderValue, checkers.videoRecorderCheck());
            put(R.id.defaultDocumentReceiverValue, checkers.defaultDocumentReceiverCheck());
        }};

        final PreconditionFixers fixers = new PreconditionFixers(this);
        fixMap = new HashMap<Integer, Supplier<Void>>() {{
            put(R.id.permissionsFix, fixers.permissionsFix());
            put(R.id.directoryFix, fixers.directoryFix());
            put(R.id.getDocumentResolverFix, fixers.getDocumentResolverFix());
            put(R.id.lockScreenFix, fixers.lockScreenFix());
            put(R.id.notificationAccessFix, fixers.notificationAccessFix());
            put(R.id.brightnessFix, fixers.brightnessFix());
            put(R.id.stayAwakeFix, fixers.stayAwakeFix());
            put(R.id.defaultVideoRecorderFix, fixers.videoRecorderFix());
            put(R.id.defaultDocumentReceiverFix, fixers.defaultDocumentReceiverFix());
        }};
    }
}
