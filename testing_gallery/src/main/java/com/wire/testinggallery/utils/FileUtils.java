package com.wire.testinggallery.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {
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
}
