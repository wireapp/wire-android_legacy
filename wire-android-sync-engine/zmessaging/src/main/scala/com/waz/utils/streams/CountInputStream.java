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

import java.io.IOException;
import java.io.InputStream;

public class CountInputStream extends InputStream {
    private long count = 0;
    private long marked = -1;

    private InputStream is;

    /**
     * get the actual number of bytes read
     * @return a long, the number of bytes read
     */
    public long getBytesRead() {
        return count;
    }

    public CountInputStream(InputStream is) {
        this.is = is;
    }

    public int available() throws IOException {
        return is.available();
    }

    public boolean markSupported() {
        return is.markSupported();
    }

    public int read() throws IOException {
        int r = is.read();
        if (r > 0) {
            count++;
        }
        return r;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int r = is.read(b, off, len);
        if (r > 0) {
            count += r;
        }
        return r;
    }

    public long skip(long skipped) throws IOException {
        long l = is.skip(skipped);
        if (l > 0) {
            count += l;
        }
        return l;
    }

    public void mark(int readlimit) {
        is.mark(readlimit);
        marked = count;
    }

    public void reset() throws IOException {
        is.reset();
        count = marked;
    }

    public void close() throws IOException {
        is.close();
        System.out.println("INPUT STREAM " + is + " HAS BEEN CLOSED");
    }

}
