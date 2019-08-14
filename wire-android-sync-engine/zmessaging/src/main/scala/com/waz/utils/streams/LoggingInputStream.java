/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.utils.streams;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LoggingInputStream extends FilterInputStream {

    /** Log stream. */
    private final OutputStream logStream;

    /**
     * @param inputStream input stream to forward all reads to
     * @param logStream logger stream
     */
    public LoggingInputStream(InputStream inputStream, OutputStream logStream) {
        super(inputStream);
        this.logStream = logStream;
    }

    @Override
    public int read() throws IOException {
        int read = super.read();
        logStream.write(read);
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = super.read(b, off, len);
        if (read > 0) {
            logStream.write(b, off, read);
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        logStream.close();
        super.close();
    }

}
