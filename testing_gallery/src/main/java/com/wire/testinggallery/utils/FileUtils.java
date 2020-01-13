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
package com.wire.testinggallery.utils;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;

import com.wire.testinggallery.R;
import com.wire.testinggallery.models.Audio;
import com.wire.testinggallery.models.Backup;
import com.wire.testinggallery.models.FileType;
import com.wire.testinggallery.models.Image;
import com.wire.testinggallery.models.PlainText;
import com.wire.testinggallery.models.Textfile;
import com.wire.testinggallery.models.Video;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {

    public static final List<FileType> fileTypes = new ArrayList<FileType>()
    {{
        add(new Textfile());
        add(new Video());
        add(new Audio());
        add(new Image());
        add(new Backup());
        add(new PlainText());
    }};

    public enum TEST_FILE_TYPES {
        ALL,
        VIDEO,
        AUDIO,
        BACKUP,
        PICTURE,
        TEXTFILE;
    }

    public static void copyStreams(InputStream from, OutputStream to) {
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

    public static String getFileFromArchiveAsString(File zipFile, String desiredFileName) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final FileInputStream fileInputStream = new FileInputStream(zipFile);
        final BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        final ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if (zipEntry.getName().equals(desiredFileName)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zipInputStream.read(buffer)) != -1) {
                    ((OutputStream) byteArrayOutputStream).write(buffer, 0, len);
                }
                ((OutputStream) byteArrayOutputStream).close();
                break;
            }
        }
        return new String(byteArrayOutputStream.toByteArray(), Charset.defaultCharset());
    }

    public static void prepareSdCard(Resources resources, TEST_FILE_TYPES fileType, Context context){
        switch(fileType){
            case ALL:
                copyRawFileToSdCard(resources, R.raw.textfile, context.getString(R.string.textfile_filename_with_filetype), context);
                copyRawFileToSdCard(resources, R.raw.audio, context.getString(R.string.audio_filename_with_filetype), context);
                copyRawFileToSdCard(resources, R.raw.video, context.getString(R.string.video_filename_with_filetype), context);
                copyRawFileToSdCard(resources, R.raw.backup, context.getString(R.string.backup_filename_with_filetype), context);
                copyRawFileToSdCard(resources, R.raw.image, context.getString(R.string.image_filename_with_filetype), context);
                break;
            case AUDIO:
                copyRawFileToSdCard(resources, R.raw.audio, context.getString(R.string.audio_filename_with_filetype), context);
                break;
            case VIDEO:
                copyRawFileToSdCard(resources, R.raw.video, context.getString(R.string.video_filename_with_filetype), context);
                break;
            case BACKUP:
                copyRawFileToSdCard(resources, R.raw.backup, context.getString(R.string.backup_filename_with_filetype), context);
                break;
            case PICTURE:
                copyRawFileToSdCard(resources, R.raw.image, context.getString(R.string.image_filename_with_filetype), context);
                break;
            case TEXTFILE:
                copyRawFileToSdCard(resources, R.raw.textfile, context.getString(R.string.textfile_filename_with_filetype), context);
                break;
        }
    }

    private static void copyRawFileToSdCard(Resources resources, int fileId, String name, Context context) {
        File pathSDCard = Environment.getExternalStoragePublicDirectory(context.getString(R.string.default_wire_testing_folder));
        if(!pathSDCard.isDirectory()) {
            pathSDCard.mkdirs();
        }

        if(!new File(pathSDCard.getAbsolutePath() + File.separator + name).isFile()) {
            try {
                final InputStream in = resources.openRawResource(fileId);
                FileOutputStream out = null;
                out = new FileOutputStream(pathSDCard.getAbsolutePath() + File.separator + name);
                byte[] buff = new byte[1024];
                int read = 0;
                try {
                    while ((read = in.read(buff)) > 0) {
                        out.write(buff, 0, read);
                    }
                } finally {

                    in.close();
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
