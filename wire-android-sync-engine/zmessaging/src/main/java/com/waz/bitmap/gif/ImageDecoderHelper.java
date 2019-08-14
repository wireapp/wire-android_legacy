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
package com.waz.bitmap.gif;


// The implementation below is based on sample code from the book "Java for Programmers" by Prof. D. Lyon.
// Author seems to be Kevin Weiner, FM Software. The LZW decoder was adapted from John Cristy's ImageMagick
// (which is licensed under Apache License 2.0, see http://www.apache.org/licenses/LICENSE-2.0).
// Kevin Weiner explicitly does not assert any copyright on the implementation
// (see also http://show.docjava.com/book/cgij/exportToHTML/ip/gif/stills/GifDecoder.java.html).

public class ImageDecoderHelper {

    static final int MAX_STACK_SIZE = 4096;
    static final int PIXEL_STACK_SIZE = 8192;
    static final int NULL_CODE = -1;

    // LZW decoder working arrays
    private final short[] prefix = new short[MAX_STACK_SIZE];
    private final byte[] suffix = new byte[MAX_STACK_SIZE];
    private final byte[] pixelStack = new byte[PIXEL_STACK_SIZE];

    /**
     * Decodes LZW image data into pixel array. Adapted from John Cristy's ImageMagick.
     */
    public void decodeBitmapData(final DataSource input, int width, int height, PixelConsumer consumer) {

        byte[] block = input.block();

        final int npix = width * height;
        int available, clear, code_mask, code_size, end_of_information, in_code, old_code, bits, code, count, i, datum, data_size, first, top, bi;

        // Initialize GIF data stream decoder.
        data_size = input.read();
        clear = 1 << data_size;
        end_of_information = clear + 1;
        available = clear + 2;
        old_code = NULL_CODE;
        code_size = data_size + 1;
        code_mask = (1 << code_size) - 1;
        if (clear >= MAX_STACK_SIZE) return;
        for (code = 0; code < clear; code++) {
            prefix[code] = 0; // XXX ArrayIndexOutOfBoundsException
            suffix[code] = (byte)code;
        }

        // Decode GIF pixel stream.
        datum = bits = count = first = top = bi = 0;
        for (i = 0; i < npix;) {
            if (bits < code_size) {
                // Load bytes until there are enough bits for a code.
                if (count == 0) {
                    // Read a new data block.
                    count = input.readBlock();
                    if (count <= 0) {
                        break;
                    }
                    bi = 0;
                }
                datum += (block[bi] & 0xff) << bits;
                bits += 8;
                bi++;
                count--;
                continue;
            }
            // Get the next code.
            code = datum & code_mask;
            datum >>= code_size;
            bits -= code_size;
            if (code >= MAX_STACK_SIZE) return;
            // Interpret the code
            if (code == end_of_information) {
                break;
            }
            if (code == clear) {
                // Reset decoder.
                code_size = data_size + 1;
                code_mask = (1 << code_size) - 1;
                available = clear + 2;
                old_code = NULL_CODE;
                continue;
            }
            if (old_code == NULL_CODE) {
                pixelStack[top++] = suffix[code];
                old_code = code;
                first = code;
                continue;
            }
            in_code = code;
            if (code >= available) {
                pixelStack[top++] = (byte)first;
                code = old_code;
            }
            while (code > clear) {
                if (top >= PIXEL_STACK_SIZE) return;
                pixelStack[top++] = suffix[code];
                code = prefix[code];
            }
            first = suffix[code] & 0xff;
            pixelStack[top++] = (byte)first;

            if (available < MAX_STACK_SIZE) {
                prefix[available] = (short)old_code;
                suffix[available] = (byte)first;
                available++;
                if (((available & code_mask) == 0) && (available < MAX_STACK_SIZE)) {
                    code_size++;
                    code_mask += available;
                }
            }
            old_code = in_code;

            // Pop a pixel off the pixel stack.
            while (top > 0) {
                top--;
                consumer.apply(pixelStack[top] & 0xff);
                i++;
            }
        }
    }

    public static class LineProducer {
        int start, end, line, pass, inc = 0;
        boolean interlace = false;

        public void reset(Gif.Frame frame) {
            interlace = frame.interlace();
            start = frame.bounds().y();
            end = start + frame.bounds().h();
            pass = 1;
            inc = interlace ? 8 : 1;
            line = start - inc;
        }

        public int getNextLine() {
            line += inc;
            if (interlace) {
                while (line >= end) {
                    switch (++pass) {
                        case 2:
                            line = start + 4;
                            break;
                        case 3:
                            line = start + 2;
                            inc = 4;
                            break;
                        case 4:
                            line = start + 1;
                            inc = 2;
                            break;
                        default:
                            line = start;
                            inc = 1;
                            break;
                    }
                }
            }
            return line;
        }
    }

    public static class PixelConsumer {
        private final int[] pixels;
        private final int[] gct;
        private final int width;
        private final int height;

        private final LineProducer lines = new LineProducer();

        int transIndex = -1;
        Gif.Bounds b = null;
        int left, lineWidth, idx, lineEnd = 0;
        int[] act;


        public PixelConsumer(int[] pixels, int[] gct, int width, int height) {
            this.pixels = pixels;
            this.gct = gct;
            this.width = width;
            this.height = height;
            act = gct;
        }

        public void reset(Gif.Frame frame) {
            act = frame.lct();
            if (act.length == 0) {
                act = gct;
            }
            transIndex = frame.transparency() ? frame.transIndex() : -1;
            lines.reset(frame);
            b = frame.bounds();
            left = b.x();
            lineWidth = b.w();
        }


        public void apply(int colorIndex) {
            if (idx >= lineEnd) {
                int line = lines.getNextLine();
                idx = left + line * width;
                lineEnd = idx + lineWidth;
            }
            if (idx < pixels.length) {
                if (colorIndex != transIndex) {
                    pixels[idx] = act[colorIndex];
                }
                idx += 1;
            }
        }
    }

    public static void clearRect(int[] pixels, int width, int x, int y, int w, int h, int color) {
        int dst = x + y * width;
        for (int i = 0; i < h; ++i) {
            for (int j = 0; j < w; ++j) {
                pixels[dst++] = color;
            }
            dst += width - w;
        }
    }

    public static void clear(int[] pixels, int color) {
        for (int i = 0; i < pixels.length; ++i) {
            pixels[i] = color;
        }
    }
}
