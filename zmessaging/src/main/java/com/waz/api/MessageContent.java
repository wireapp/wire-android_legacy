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
package com.waz.api;

public interface MessageContent {

    class Location implements MessageContent {

        private final float longitude;
        private final float latitude;
        private final String name;
        private final int zoom;

        public Location(float longitude, float latitude, String name, int zoom) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.name = name;
            this.zoom = zoom;
        }

        public float getLongitude() {
            return longitude;
        }

        public float getLatitude() {
            return latitude;
        }

        public String getName() {
            return name;
        }

        public int getZoom() {
            return zoom;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Location location = (Location) o;

            if (Float.compare(location.longitude, longitude) != 0) return false;
            if (Float.compare(location.latitude, latitude) != 0) return false;
            if (zoom != location.zoom) return false;
            return name != null ? name.equals(location.name) : location.name == null;
        }

        @Override
        public int hashCode() {
            int result = (longitude != +0.0f ? Float.floatToIntBits(longitude) : 0);
            result = 31 * result + (latitude != +0.0f ? Float.floatToIntBits(latitude) : 0);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + zoom;
            return result;
        }

        @Override
        public String toString() {
            return "Location{" +
                    "longitude=" + longitude +
                    ", latitude=" + latitude +
                    ", name='" + name + '\'' +
                    ", zoom=" + zoom +
                    '}';
        }
    }
}
