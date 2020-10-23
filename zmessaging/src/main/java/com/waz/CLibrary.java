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
package com.waz;

import com.sun.jna.Library;
import com.sun.jna.Structure;
import com.waz.utils.jna.Size_t;

import java.util.Arrays;
import java.util.List;

public interface CLibrary extends Library {

    class Members extends Structure {
        public static class ByReference extends Members implements Structure.ByReference { }
        public Member.ByReference membv;
        public Size_t membc;

        public Members() { }
        public Member.ByReference[] toArray(int size) {
            return (Member.ByReference[]) membv.toArray(size);
        }
        @Override
        protected List getFieldOrder() {
            return Arrays.asList(new String[] {"membv", "membc"});
        }
    }

    class Member extends Structure {
        public static class ByReference extends Member implements Structure.ByReference { }
        public String userid;
        public int audio_estab;
        public int video_recv;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList(new String[] {"userid", "audio_estab", "video_recv"});
        }
    }
}
