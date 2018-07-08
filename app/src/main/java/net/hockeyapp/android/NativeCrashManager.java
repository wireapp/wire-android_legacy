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
/*
 * This part of the Wire sofware uses source code from the breakapp project
 * (https://github.com/ashtom/breakapp) governed by the MIT license.
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Thomas Dohmke, Bit Stadium GmbH.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
*/
package net.hockeyapp.android;

import android.content.Context;
import com.waz.api.HockeyCrashReporter;
import com.waz.threading.Threading;
import timber.log.Timber;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.util.Date;
import java.util.UUID;

public class NativeCrashManager {

    public static boolean loggedDumpFiles(String identifier) {
        String[] filenames = searchForDumpFiles();
        if (filenames == null) {
            return false;
        }
        for (String dumpFilename : filenames) {
            String logFilename = createLogFile();
            if (logFilename != null) {
                uploadDumpAndLog(identifier, dumpFilename, logFilename);
            }
        }
        return filenames.length > 0;
    }

    public static void deleteDumpFiles(final Context context) {
        Threading.IO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] filenames = searchForDumpFiles();
                    if (filenames == null) {
                        return;
                    }
                    for (String dumpFilename : filenames) {
                        context.deleteFile(dumpFilename);
                    }
                } catch (Throwable t) {
                    // ignore
                }
            }
        });
    }

    public static String createLogFile() {
        final Date now = new Date();

        try {
            // Create filename from a random uuid
            String filename = UUID.randomUUID().toString();
            String path = Constants.FILES_PATH + "/" + filename + ".faketrace";
            Timber.i("Writing unhandled exception to: %s", path);

            // Write the stacktrace to disk
            BufferedWriter write = new BufferedWriter(new FileWriter(path));
            write.write("Package: " + Constants.APP_PACKAGE + "\n");
            write.write("Version: " + Constants.APP_VERSION + "\n");
            write.write("Android: " + Constants.ANDROID_VERSION + "\n");
            write.write("Manufacturer: " + Constants.PHONE_MANUFACTURER + "\n");
            write.write("Model: " + Constants.PHONE_MODEL + "\n");
            write.write("Date: " + now + "\n");
            write.write("\n");
            write.write("MinidumpContainer");
            write.flush();
            write.close();

            return filename + ".faketrace";
        } catch (Exception ignored) {
        }

        return null;
    }

    public static void uploadDumpAndLog(final String identifier,
                                        final String dumpFilename,
                                        final String logFilename) {
        try {
            HockeyCrashReporter.uploadCrashReport(identifier,
                                                  new File(Constants.FILES_PATH, dumpFilename),
                                                  new File(Constants.FILES_PATH, logFilename));
        } catch (Throwable t) {
            ExceptionHandler.saveException(t, null);
        }
    }

    private static String[] searchForDumpFiles() {
        if (Constants.FILES_PATH != null) {
            // Try to create the files folder if it doesn't exist
            File dir = new File(Constants.FILES_PATH + "/");
            boolean created = dir.mkdir();
            if (!created && !dir.exists()) {
                return new String[0];
            }

            // Filter for ".dmp" files
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".dmp");
                }
            };
            return dir.list(filter);
        } else {
            Timber.i("Can't search for exception as file path is null.");
            return new String[0];
        }
    }
}
