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
package com.waz.zclient.utils;

import com.waz.zclient.R;

import java.util.LinkedList;
import java.util.List;

/**
 * There seems to be an issue with decoding XML with emojis on some devices
 * https://code.google.com/p/android/issues/detail?id=81341
 *
 * Emojis fetched from
 * http://unicode.org/emoji/charts/full-emoji-list.html
 * http://unicode.org/emoji/charts/emoji-ordering.html
 *
 * Converted to using unicode escapes with command
 * native2ascii -encoding utf8 input.txt output.txt
 *
 */
public class Emojis {

    public static List<String[]> getAllEmojisSortedByCategory() {
        List<String[]> all = new LinkedList<>();
        all.add(PEOPLE);
        all.add(NATURE);
        all.add(FOOD_AND_DRINK);
        all.add(ACTIVITY);
        all.add(TRAVEL_AND_PLACES);
        all.add(OBJECTS);
        all.add(SYMBOLS);
        all.add(FLAGS);
        return all;
    }

    public static final int[] EMOJI_KEYBOARD_TAB_LABELS = {
        R.string.glyph__clock,
        R.string.glyph__emoji,
        R.string.glyph__emoji_flower,
        R.string.glyph__emoji_cake,
        R.string.glyph__emoji_car,
        R.string.glyph__emoji_ball,
        R.string.glyph__emoji_crown,
        R.string.glyph__emoji_symbol,
        R.string.glyph__emoji_flag,
        R.string.glyph__backspace
    };

    public static final int VERSION = 2;

    public static final String[] ACTIVITY = new String[] {
        "\ud83d\udc7e", // Alien Monster
        "\ud83d\udd74", // Man in Business Suit Levitating
        "\ud83c\udfaa", // Circus Tent
        "\ud83c\udfad", // Performing Arts
        "\ud83c\udfa8", // Artist Palette
        "\ud83c\udfb0", // Slot Machine
        "\ud83d\udea3", // Rowboat
        "\ud83d\udec0", // Bath
        "\ud83c\udf97", // Reminder Ribbon
        "\ud83c\udf9f", // Admission Tickets
        "\ud83c\udfab", // Ticket
        "\ud83c\udf96", // Military Medal
        "\ud83c\udfc6", // Trophy
        "\ud83c\udfc5", // Sports Medal
        "\u26bd", // Soccer Ball
        "\u26be", // Baseball
        "\ud83c\udfc0", // Basketball and Hoop
        "\ud83c\udfd0", // Volleyball
        "\ud83c\udfc8", // American Football
        "\ud83c\udfc9", // Rugby Football
        "\ud83c\udfbe", // Tennis Racquet and Ball
        "\ud83c\udfb1", // Billiards
        "\ud83c\udfb3", // Bowling
        "\ud83c\udfcf", // Cricket Bat and Ball
        "\ud83c\udfd1", // Field Hockey Stick and Ball
        "\ud83c\udfd2", // Ice Hockey Stick and Puck
        "\ud83c\udfd3", // Table Tennis Paddle and Ball
        "\ud83c\udff8", // Badminton Racquet and Shuttlecock
        "\u26f3", // Flag in Hole
        "\ud83c\udfcc", // Golfer
        "\u26f8", // Ice Skate
        "\ud83c\udfa3", // Fishing Pole and Fish
        "\ud83c\udfbd", // Running Shirt With Sash
        "\ud83c\udfbf", // Ski and Ski Boot
        "\u26f7", // Skier
        "\ud83c\udfc2", // Snowboarder
        "\ud83c\udfc4", // Surfer
        "\ud83c\udfc7", // Horse Racing
        "\ud83c\udfca", // Swimmer
        "\u26f9", // Person With Ball
        "\ud83c\udfcb", // Weight Lifter
        "\ud83d\udeb4", // Bicyclist
        "\ud83d\udeb5", // Mountain Bicyclist
        "\ud83c\udfaf", // Direct Hit
        "\ud83c\udfae", // Video Game
        "\ud83c\udfb2", // Game Die
        "\ud83c\udfb7", // Saxophone
        "\ud83c\udfb8", // Guitar
        "\ud83c\udfba", // Trumpet
        "\ud83c\udfbb", // Violin
        "\ud83c\udfac", // Clapper Board
        "\ud83c\udff9" // Bow and Arrow
    };

    public static final String[] FLAGS = new String[] {
        "\ud83c\udde6\ud83c\udde8", // Flag for Ascension Island
        "\ud83c\udde6\ud83c\udde9", // Flag for Andorra
        "\ud83c\udde6\ud83c\uddea", // Flag for United Arab Emirates
        "\ud83c\udde6\ud83c\uddeb", // Flag for Afghanistan
        "\ud83c\udde6\ud83c\uddec", // Flag for Antigua & Barbuda
        "\ud83c\udde6\ud83c\uddee", // Flag for Anguilla
        "\ud83c\udde6\ud83c\uddf1", // Flag for Albania
        "\ud83c\udde6\ud83c\uddf2", // Flag for Armenia
        "\ud83c\udde6\ud83c\uddf4", // Flag for Angola
        "\ud83c\udde6\ud83c\uddf6", // Flag for Antarctica
        "\ud83c\udde6\ud83c\uddf7", // Flag for Argentina
        "\ud83c\udde6\ud83c\uddf8", // Flag for American Samoa
        "\ud83c\udde6\ud83c\uddf9", // Flag for Austria
        "\ud83c\udde6\ud83c\uddfa", // Flag for Australia
        "\ud83c\udde6\ud83c\uddfc", // Flag for Aruba
        "\ud83c\udde6\ud83c\uddfd", // Flag for \u00c5land Islands
        "\ud83c\udde6\ud83c\uddff", // Flag for Azerbaijan
        "\ud83c\udde7\ud83c\udde6", // Flag for Bosnia & Herzegovina
        "\ud83c\udde7\ud83c\udde7", // Flag for Barbados
        "\ud83c\udde7\ud83c\udde9", // Flag for Bangladesh
        "\ud83c\udde7\ud83c\uddea", // Flag for Belgium
        "\ud83c\udde7\ud83c\uddeb", // Flag for Burkina Faso
        "\ud83c\udde7\ud83c\uddec", // Flag for Bulgaria
        "\ud83c\udde7\ud83c\udded", // Flag for Bahrain
        "\ud83c\udde7\ud83c\uddee", // Flag for Burundi
        "\ud83c\udde7\ud83c\uddef", // Flag for Benin
        "\ud83c\udde7\ud83c\uddf1", // Flag for St. Barth\u00e9lemy
        "\ud83c\udde7\ud83c\uddf2", // Flag for Bermuda
        "\ud83c\udde7\ud83c\uddf3", // Flag for Brunei
        "\ud83c\udde7\ud83c\uddf4", // Flag for Bolivia
        "\ud83c\udde7\ud83c\uddf6", // Flag for Caribbean Netherlands
        "\ud83c\udde7\ud83c\uddf7", // Flag for Brazil
        "\ud83c\udde7\ud83c\uddf8", // Flag for Bahamas
        "\ud83c\udde7\ud83c\uddf9", // Flag for Bhutan
        "\ud83c\udde7\ud83c\uddfb", // Flag for Bouvet Island
        "\ud83c\udde7\ud83c\uddfc", // Flag for Botswana
        "\ud83c\udde7\ud83c\uddfe", // Flag for Belarus
        "\ud83c\udde7\ud83c\uddff", // Flag for Belize
        "\ud83c\udde8\ud83c\udde6", // Flag for Canada
        "\ud83c\udde8\ud83c\udde8", // Flag for Cocos Islands
        "\ud83c\udde8\ud83c\udde9", // Flag for Congo - Kinshasa
        "\ud83c\udde8\ud83c\uddeb", // Flag for Central African Republic
        "\ud83c\udde8\ud83c\uddec", // Flag for Congo - Brazzaville
        "\ud83c\udde8\ud83c\udded", // Flag for Switzerland
        "\ud83c\udde8\ud83c\uddee", // Flag for C\u00f4te D\u2019Ivoire
        "\ud83c\udde8\ud83c\uddf0", // Flag for Cook Islands
        "\ud83c\udde8\ud83c\uddf1", // Flag for Chile
        "\ud83c\udde8\ud83c\uddf2", // Flag for Cameroon
        "\ud83c\udde8\ud83c\uddf3", // Flag for China
        "\ud83c\udde8\ud83c\uddf4", // Flag for Colombia
        "\ud83c\udde8\ud83c\uddf5", // Flag for Clipperton Island
        "\ud83c\udde8\ud83c\uddf7", // Flag for Costa Rica
        "\ud83c\udde8\ud83c\uddfa", // Flag for Cuba
        "\ud83c\udde8\ud83c\uddfb", // Flag for Cape Verde
        "\ud83c\udde8\ud83c\uddfc", // Flag for Cura\u00e7ao
        "\ud83c\udde8\ud83c\uddfd", // Flag for Christmas Island
        "\ud83c\udde8\ud83c\uddfe", // Flag for Cyprus
        "\ud83c\udde8\ud83c\uddff", // Flag for Czech Republic
        "\ud83c\udde9\ud83c\uddea", // Flag for Germany
        "\ud83c\udde9\ud83c\uddec", // Flag for Diego Garcia
        "\ud83c\udde9\ud83c\uddef", // Flag for Djibouti
        "\ud83c\udde9\ud83c\uddf0", // Flag for Denmark
        "\ud83c\udde9\ud83c\uddf2", // Flag for Dominica
        "\ud83c\udde9\ud83c\uddf4", // Flag for Dominican Republic
        "\ud83c\udde9\ud83c\uddff", // Flag for Algeria
        "\ud83c\uddea\ud83c\udde6", // Flag for Ceuta & Melilla
        "\ud83c\uddea\ud83c\udde8", // Flag for Ecuador
        "\ud83c\uddea\ud83c\uddea", // Flag for Estonia
        "\ud83c\uddea\ud83c\uddec", // Flag for Egypt
        "\ud83c\uddea\ud83c\udded", // Flag for Western Sahara
        "\ud83c\uddea\ud83c\uddf7", // Flag for Eritrea
        "\ud83c\uddea\ud83c\uddf8", // Flag for Spain
        "\ud83c\uddea\ud83c\uddf9", // Flag for Ethiopia
        "\ud83c\uddea\ud83c\uddfa", // Flag for European Union
        "\ud83c\uddeb\ud83c\uddee", // Flag for Finland
        "\ud83c\uddeb\ud83c\uddef", // Flag for Fiji
        "\ud83c\uddeb\ud83c\uddf0", // Flag for Falkland Islands
        "\ud83c\uddeb\ud83c\uddf2", // Flag for Micronesia
        "\ud83c\uddeb\ud83c\uddf4", // Flag for Faroe Islands
        "\ud83c\uddeb\ud83c\uddf7", // Flag for France
        "\ud83c\uddec\ud83c\udde6", // Flag for Gabon
        "\ud83c\uddec\ud83c\udde7", // Flag for United Kingdom
        "\ud83c\uddec\ud83c\udde9", // Flag for Grenada
        "\ud83c\uddec\ud83c\uddea", // Flag for Georgia
        "\ud83c\uddec\ud83c\uddeb", // Flag for French Guiana
        "\ud83c\uddec\ud83c\uddec", // Flag for Guernsey
        "\ud83c\uddec\ud83c\udded", // Flag for Ghana
        "\ud83c\uddec\ud83c\uddee", // Flag for Gibraltar
        "\ud83c\uddec\ud83c\uddf1", // Flag for Greenland
        "\ud83c\uddec\ud83c\uddf2", // Flag for Gambia
        "\ud83c\uddec\ud83c\uddf3", // Flag for Guinea
        "\ud83c\uddec\ud83c\uddf5", // Flag for Guadeloupe
        "\ud83c\uddec\ud83c\uddf6", // Flag for Equatorial Guinea
        "\ud83c\uddec\ud83c\uddf7", // Flag for Greece
        "\ud83c\uddec\ud83c\uddf8", // Flag for South Georgia & South Sandwich Islands
        "\ud83c\uddec\ud83c\uddf9", // Flag for Guatemala
        "\ud83c\uddec\ud83c\uddfa", // Flag for Guam
        "\ud83c\uddec\ud83c\uddfc", // Flag for Guinea-Bissau
        "\ud83c\uddec\ud83c\uddfe", // Flag for Guyana
        "\ud83c\udded\ud83c\uddf0", // Flag for Hong Kong
        "\ud83c\udded\ud83c\uddf2", // Flag for Heard & McDonald Islands
        "\ud83c\udded\ud83c\uddf3", // Flag for Honduras
        "\ud83c\udded\ud83c\uddf7", // Flag for Croatia
        "\ud83c\udded\ud83c\uddf9", // Flag for Haiti
        "\ud83c\udded\ud83c\uddfa", // Flag for Hungary
        "\ud83c\uddee\ud83c\udde8", // Flag for Canary Islands
        "\ud83c\uddee\ud83c\udde9", // Flag for Indonesia
        "\ud83c\uddee\ud83c\uddea", // Flag for Ireland
        "\ud83c\uddee\ud83c\uddf1", // Flag for Israel
        "\ud83c\uddee\ud83c\uddf2", // Flag for Isle of Man
        "\ud83c\uddee\ud83c\uddf3", // Flag for India
        "\ud83c\uddee\ud83c\uddf4", // Flag for British Indian Ocean Territory
        "\ud83c\uddee\ud83c\uddf6", // Flag for Iraq
        "\ud83c\uddee\ud83c\uddf7", // Flag for Iran
        "\ud83c\uddee\ud83c\uddf8", // Flag for Iceland
        "\ud83c\uddee\ud83c\uddf9", // Flag for Italy
        "\ud83c\uddef\ud83c\uddea", // Flag for Jersey
        "\ud83c\uddef\ud83c\uddf2", // Flag for Jamaica
        "\ud83c\uddef\ud83c\uddf4", // Flag for Jordan
        "\ud83c\uddef\ud83c\uddf5", // Flag for Japan
        "\ud83c\uddf0\ud83c\uddea", // Flag for Kenya
        "\ud83c\uddf0\ud83c\uddec", // Flag for Kyrgyzstan
        "\ud83c\uddf0\ud83c\udded", // Flag for Cambodia
        "\ud83c\uddf0\ud83c\uddee", // Flag for Kiribati
        "\ud83c\uddf0\ud83c\uddf2", // Flag for Comoros
        "\ud83c\uddf0\ud83c\uddf3", // Flag for St. Kitts & Nevis
        "\ud83c\uddf0\ud83c\uddf5", // Flag for North Korea
        "\ud83c\uddf0\ud83c\uddf7", // Flag for South Korea
        "\ud83c\uddf0\ud83c\uddfc", // Flag for Kuwait
        "\ud83c\uddf0\ud83c\uddfe", // Flag for Cayman Islands
        "\ud83c\uddf0\ud83c\uddff", // Flag for Kazakhstan
        "\ud83c\uddf1\ud83c\udde6", // Flag for Laos
        "\ud83c\uddf1\ud83c\udde7", // Flag for Lebanon
        "\ud83c\uddf1\ud83c\udde8", // Flag for St. Lucia
        "\ud83c\uddf1\ud83c\uddee", // Flag for Liechtenstein
        "\ud83c\uddf1\ud83c\uddf0", // Flag for Sri Lanka
        "\ud83c\uddf1\ud83c\uddf7", // Flag for Liberia
        "\ud83c\uddf1\ud83c\uddf8", // Flag for Lesotho
        "\ud83c\uddf1\ud83c\uddf9", // Flag for Lithuania
        "\ud83c\uddf1\ud83c\uddfa", // Flag for Luxembourg
        "\ud83c\uddf1\ud83c\uddfb", // Flag for Latvia
        "\ud83c\uddf1\ud83c\uddfe", // Flag for Libya
        "\ud83c\uddf2\ud83c\udde6", // Flag for Morocco
        "\ud83c\uddf2\ud83c\udde8", // Flag for Monaco
        "\ud83c\uddf2\ud83c\udde9", // Flag for Moldova
        "\ud83c\uddf2\ud83c\uddea", // Flag for Montenegro
        "\ud83c\uddf2\ud83c\uddeb", // Flag for St. Martin
        "\ud83c\uddf2\ud83c\uddec", // Flag for Madagascar
        "\ud83c\uddf2\ud83c\udded", // Flag for Marshall Islands
        "\ud83c\uddf2\ud83c\uddf0", // Flag for Macedonia
        "\ud83c\uddf2\ud83c\uddf1", // Flag for Mali
        "\ud83c\uddf2\ud83c\uddf2", // Flag for Myanmar
        "\ud83c\uddf2\ud83c\uddf3", // Flag for Mongolia
        "\ud83c\uddf2\ud83c\uddf4", // Flag for Macau
        "\ud83c\uddf2\ud83c\uddf5", // Flag for Northern Mariana Islands
        "\ud83c\uddf2\ud83c\uddf6", // Flag for Martinique
        "\ud83c\uddf2\ud83c\uddf7", // Flag for Mauritania
        "\ud83c\uddf2\ud83c\uddf8", // Flag for Montserrat
        "\ud83c\uddf2\ud83c\uddf9", // Flag for Malta
        "\ud83c\uddf2\ud83c\uddfa", // Flag for Mauritius
        "\ud83c\uddf2\ud83c\uddfb", // Flag for Maldives
        "\ud83c\uddf2\ud83c\uddfc", // Flag for Malawi
        "\ud83c\uddf2\ud83c\uddfd", // Flag for Mexico
        "\ud83c\uddf2\ud83c\uddfe", // Flag for Malaysia
        "\ud83c\uddf2\ud83c\uddff", // Flag for Mozambique
        "\ud83c\uddf3\ud83c\udde6", // Flag for Namibia
        "\ud83c\uddf3\ud83c\udde8", // Flag for New Caledonia
        "\ud83c\uddf3\ud83c\uddea", // Flag for Niger
        "\ud83c\uddf3\ud83c\uddeb", // Flag for Norfolk Island
        "\ud83c\uddf3\ud83c\uddec", // Flag for Nigeria
        "\ud83c\uddf3\ud83c\uddee", // Flag for Nicaragua
        "\ud83c\uddf3\ud83c\uddf1", // Flag for Netherlands
        "\ud83c\uddf3\ud83c\uddf4", // Flag for Norway
        "\ud83c\uddf3\ud83c\uddf5", // Flag for Nepal
        "\ud83c\uddf3\ud83c\uddf7", // Flag for Nauru
        "\ud83c\uddf3\ud83c\uddfa", // Flag for Niue
        "\ud83c\uddf3\ud83c\uddff", // Flag for New Zealand
        "\ud83c\uddf4\ud83c\uddf2", // Flag for Oman
        "\ud83c\uddf5\ud83c\udde6", // Flag for Panama
        "\ud83c\uddf5\ud83c\uddea", // Flag for Peru
        "\ud83c\uddf5\ud83c\uddeb", // Flag for French Polynesia
        "\ud83c\uddf5\ud83c\uddec", // Flag for Papua New Guinea
        "\ud83c\uddf5\ud83c\udded", // Flag for Philippines
        "\ud83c\uddf5\ud83c\uddf0", // Flag for Pakistan
        "\ud83c\uddf5\ud83c\uddf1", // Flag for Poland
        "\ud83c\uddf5\ud83c\uddf2", // Flag for St. Pierre & Miquelon
        "\ud83c\uddf5\ud83c\uddf3", // Flag for Pitcairn Islands
        "\ud83c\uddf5\ud83c\uddf7", // Flag for Puerto Rico
        "\ud83c\uddf5\ud83c\uddf8", // Flag for Palestinian Territories
        "\ud83c\uddf5\ud83c\uddf9", // Flag for Portugal
        "\ud83c\uddf5\ud83c\uddfc", // Flag for Palau
        "\ud83c\uddf5\ud83c\uddfe", // Flag for Paraguay
        "\ud83c\uddf6\ud83c\udde6", // Flag for Qatar
        "\ud83c\uddf7\ud83c\uddea", // Flag for R\u00e9union
        "\ud83c\uddf7\ud83c\uddf4", // Flag for Romania
        "\ud83c\uddf7\ud83c\uddf8", // Flag for Serbia
        "\ud83c\uddf7\ud83c\uddfa", // Flag for Russia
        "\ud83c\uddf7\ud83c\uddfc", // Flag for Rwanda
        "\ud83c\uddf8\ud83c\udde6", // Flag for Saudi Arabia
        "\ud83c\uddf8\ud83c\udde7", // Flag for Solomon Islands
        "\ud83c\uddf8\ud83c\udde8", // Flag for Seychelles
        "\ud83c\uddf8\ud83c\udde9", // Flag for Sudan
        "\ud83c\uddf8\ud83c\uddea", // Flag for Sweden
        "\ud83c\uddf8\ud83c\uddec", // Flag for Singapore
        "\ud83c\uddf8\ud83c\udded", // Flag for St. Helena
        "\ud83c\uddf8\ud83c\uddee", // Flag for Slovenia
        "\ud83c\uddf8\ud83c\uddef", // Flag for Svalbard & Jan Mayen
        "\ud83c\uddf8\ud83c\uddf0", // Flag for Slovakia
        "\ud83c\uddf8\ud83c\uddf1", // Flag for Sierra Leone
        "\ud83c\uddf8\ud83c\uddf2", // Flag for San Marino
        "\ud83c\uddf8\ud83c\uddf3", // Flag for Senegal
        "\ud83c\uddf8\ud83c\uddf4", // Flag for Somalia
        "\ud83c\uddf8\ud83c\uddf7", // Flag for Suriname
        "\ud83c\uddf8\ud83c\uddf8", // Flag for South Sudan
        "\ud83c\uddf8\ud83c\uddf9", // Flag for S\u00e3o Tom\u00e9 & Pr\u00edncipe
        "\ud83c\uddf8\ud83c\uddfb", // Flag for El Salvador
        "\ud83c\uddf8\ud83c\uddfd", // Flag for Sint Maarten
        "\ud83c\uddf8\ud83c\uddfe", // Flag for Syria
        "\ud83c\uddf8\ud83c\uddff", // Flag for Swaziland
        "\ud83c\uddf9\ud83c\udde6", // Flag for Tristan Da Cunha
        "\ud83c\uddf9\ud83c\udde8", // Flag for Turks & Caicos Islands
        "\ud83c\uddf9\ud83c\udde9", // Flag for Chad
        "\ud83c\uddf9\ud83c\uddeb", // Flag for French Southern Territories
        "\ud83c\uddf9\ud83c\uddec", // Flag for Togo
        "\ud83c\uddf9\ud83c\udded", // Flag for Thailand
        "\ud83c\uddf9\ud83c\uddef", // Flag for Tajikistan
        "\ud83c\uddf9\ud83c\uddf0", // Flag for Tokelau
        "\ud83c\uddf9\ud83c\uddf1", // Flag for Timor-Leste
        "\ud83c\uddf9\ud83c\uddf2", // Flag for Turkmenistan
        "\ud83c\uddf9\ud83c\uddf3", // Flag for Tunisia
        "\ud83c\uddf9\ud83c\uddf4", // Flag for Tonga
        "\ud83c\uddf9\ud83c\uddf7", // Flag for Turkey
        "\ud83c\uddf9\ud83c\uddf9", // Flag for Trinidad & Tobago
        "\ud83c\uddf9\ud83c\uddfb", // Flag for Tuvalu
        "\ud83c\uddf9\ud83c\uddfc", // Flag for Taiwan
        "\ud83c\uddf9\ud83c\uddff", // Flag for Tanzania
        "\ud83c\uddfa\ud83c\udde6", // Flag for Ukraine
        "\ud83c\uddfa\ud83c\uddec", // Flag for Uganda
        "\ud83c\uddfa\ud83c\uddf2", // Flag for U.S. Outlying Islands
        "\ud83c\uddfa\ud83c\uddf8", // Flag for United States
        "\ud83c\uddfa\ud83c\uddfe", // Flag for Uruguay
        "\ud83c\uddfa\ud83c\uddff", // Flag for Uzbekistan
        "\ud83c\uddfb\ud83c\udde6", // Flag for Vatican City
        "\ud83c\uddfb\ud83c\udde8", // Flag for St. Vincent & Grenadines
        "\ud83c\uddfb\ud83c\uddea", // Flag for Venezuela
        "\ud83c\uddfb\ud83c\uddec", // Flag for British Virgin Islands
        "\ud83c\uddfb\ud83c\uddee", // Flag for U.S. Virgin Islands
        "\ud83c\uddfb\ud83c\uddf3", // Flag for Vietnam
        "\ud83c\uddfb\ud83c\uddfa", // Flag for Vanuatu
        "\ud83c\uddfc\ud83c\uddeb", // Flag for Wallis & Futuna
        "\ud83c\uddfc\ud83c\uddf8", // Flag for Samoa
        "\ud83c\uddfd\ud83c\uddf0", // Flag for Kosovo
        "\ud83c\uddfe\ud83c\uddea", // Flag for Yemen
        "\ud83c\uddfe\ud83c\uddf9", // Flag for Mayotte
        "\ud83c\uddff\ud83c\udde6", // Flag for South Africa
        "\ud83c\uddff\ud83c\uddf2", // Flag for Zambia
        "\ud83c\uddff\ud83c\uddfc", // Flag for Zimbabwe
        "\ud83c\uddfd\ud83c\uddea", // Flag for England
        "\ud83c\uddfd\ud83c\uddf8", // Flag for Scotland
        "\ud83c\uddfd\ud83c\uddfc" // Flag for Wales
    };

    public static final String[] FOOD_AND_DRINK = new String[] {
        "\ud83c\udf47", // Grapes
        "\ud83c\udf48", // Melon
        "\ud83c\udf49", // Watermelon
        "\ud83c\udf4a", // Tangerine
        "\ud83c\udf4b", // Lemon
        "\ud83c\udf4c", // Banana
        "\ud83c\udf4d", // Pineapple
        "\ud83c\udf4e", // Red Apple
        "\ud83c\udf4f", // Green Apple
        "\ud83c\udf50", // Pear
        "\ud83c\udf51", // Peach
        "\ud83c\udf52", // Cherries
        "\ud83c\udf53", // Strawberry
        "\ud83c\udf45", // Tomato
        "\ud83c\udf46", // Aubergine
        "\ud83c\udf3d", // Ear of Maize
        "\ud83c\udf36", // Hot Pepper
        "\ud83c\udf44", // Mushroom
        "\ud83c\udf30", // Chestnut
        "\ud83c\udf5e", // Bread
        "\ud83e\uddc0", // Cheese Wedge
        "\ud83c\udf56", // Meat on Bone
        "\ud83c\udf57", // Poultry Leg
        "\ud83c\udf54", // Hamburger
        "\ud83c\udf5f", // French Fries
        "\ud83c\udf55", // Slice of Pizza
        "\ud83c\udf2d", // Hot Dog
        "\ud83c\udf2e", // Taco
        "\ud83c\udf2f", // Burrito
        "\ud83c\udf73", // Cooking
        "\ud83c\udf72", // Pot of Food
        "\ud83c\udf7f", // Popcorn
        "\ud83c\udf71", // Bento Box
        "\ud83c\udf58", // Rice Cracker
        "\ud83c\udf59", // Rice Ball
        "\ud83c\udf5a", // Cooked Rice
        "\ud83c\udf5b", // Curry and Rice
        "\ud83c\udf5c", // Steaming Bowl
        "\ud83c\udf5d", // Spaghetti
        "\ud83c\udf60", // Roasted Sweet Potato
        "\ud83c\udf62", // Oden
        "\ud83c\udf63", // Sushi
        "\ud83c\udf64", // Fried Shrimp
        "\ud83c\udf65", // Fish Cake With Swirl Design
        "\ud83c\udf61", // Dango
        "\ud83c\udf66", // Soft Ice Cream
        "\ud83c\udf67", // Shaved Ice
        "\ud83c\udf68", // Ice Cream
        "\ud83c\udf69", // Doughnut
        "\ud83c\udf6a", // Cookie
        "\ud83c\udf82", // Birthday Cake
        "\ud83c\udf70", // Shortcake
        "\ud83c\udf6b", // Chocolate Bar
        "\ud83c\udf6c", // Candy
        "\ud83c\udf6d", // Lollipop
        "\ud83c\udf6e", // Custard
        "\ud83c\udf6f", // Honey Pot
        "\ud83c\udf7c", // Baby Bottle
        "\u2615", // Hot Beverage
        "\ud83c\udf75", // Teacup Without Handle
        "\ud83c\udf76", // Sake Bottle and Cup
        "\ud83c\udf7e", // Bottle With Popping Cork
        "\ud83c\udf77", // Wine Glass
        "\ud83c\udf78", // Cocktail Glass
        "\ud83c\udf79", // Tropical Drink
        "\ud83c\udf7a", // Beer Mug
        "\ud83c\udf7b", // Clinking Beer Mugs
        "\ud83c\udf7d", // Fork and Knife With Plate
        "\ud83c\udf74" // Fork and Knife
    };

    public static final String[] NATURE = new String[] {
        "\ud83d\ude48", // See-No-Evil Monkey
        "\ud83d\ude49", // Hear-No-Evil Monkey
        "\ud83d\ude4a", // Speak-No-Evil Monkey
        "\ud83d\udca6", // Splashing Sweat Symbol
        "\ud83d\udca8", // Dash Symbol
        "\ud83d\udc35", // Monkey Face
        "\ud83d\udc12", // Monkey
        "\ud83d\udc36", // Dog Face
        "\ud83d\udc15", // Dog
        "\ud83d\udc29", // Poodle
        "\ud83d\udc3a", // Wolf Face
        "\ud83d\udc31", // Cat Face
        "\ud83d\udc08", // Cat
        "\ud83e\udd81", // Lion Face
        "\ud83d\udc2f", // Tiger Face
        "\ud83d\udc05", // Tiger
        "\ud83d\udc06", // Leopard
        "\ud83d\udc34", // Horse Face
        "\ud83d\udc0e", // Horse
        "\ud83e\udd84", // Unicorn Face
        "\ud83d\udc2e", // Cow Face
        "\ud83d\udc02", // Ox
        "\ud83d\udc03", // Water Buffalo
        "\ud83d\udc04", // Cow
        "\ud83d\udc37", // Pig Face
        "\ud83d\udc16", // Pig
        "\ud83d\udc17", // Boar
        "\ud83d\udc3d", // Pig Nose
        "\ud83d\udc0f", // Ram
        "\ud83d\udc11", // Sheep
        "\ud83d\udc10", // Goat
        "\ud83d\udc2a", // Dromedary Camel
        "\ud83d\udc2b", // Bactrian Camel
        "\ud83d\udc18", // Elephant
        "\ud83d\udc2d", // Mouse Face
        "\ud83d\udc01", // Mouse
        "\ud83d\udc00", // Rat
        "\ud83d\udc39", // Hamster Face
        "\ud83d\udc30", // Rabbit Face
        "\ud83d\udc07", // Rabbit
        "\ud83d\udc3f", // Chipmunk
        "\ud83d\udc3b", // Bear Face
        "\ud83d\udc28", // Koala
        "\ud83d\udc3c", // Panda Face
        "\ud83d\udc3e", // Paw Prints
        "\ud83e\udd83", // Turkey
        "\ud83d\udc14", // Chicken
        "\ud83d\udc13", // Rooster
        "\ud83d\udc23", // Hatching Chick
        "\ud83d\udc24", // Baby Chick
        "\ud83d\udc25", // Front-Facing Baby Chick
        "\ud83d\udc26", // Bird
        "\ud83d\udc27", // Penguin
        "\ud83d\udd4a", // Dove of Peace
        "\ud83d\udc38", // Frog Face
        "\ud83d\udc0a", // Crocodile
        "\ud83d\udc22", // Turtle
        "\ud83d\udc0d", // Snake
        "\ud83d\udc32", // Dragon Face
        "\ud83d\udc09", // Dragon
        "\ud83d\udc33", // Spouting Whale
        "\ud83d\udc0b", // Whale
        "\ud83d\udc2c", // Dolphin
        "\ud83d\udc1f", // Fish
        "\ud83d\udc20", // Tropical Fish
        "\ud83d\udc21", // Blowfish
        "\ud83d\udc19", // Octopus
        "\ud83d\udc1a", // Spiral Shell
        "\ud83e\udd80", // Crab
        "\ud83d\udc0c", // Snail
        "\ud83d\udc1b", // Bug
        "\ud83d\udc1c", // Ant
        "\ud83d\udc1d", // Honeybee
        "\ud83d\udc1e", // Lady Beetle
        "\ud83d\udd77", // Spider
        "\ud83d\udd78", // Spider Web
        "\ud83e\udd82", // Scorpion
        "\ud83d\udc90", // Bouquet
        "\ud83c\udf38", // Cherry Blossom
        "\ud83d\udcae", // White Flower
        "\ud83c\udff5", // Rosette
        "\ud83c\udf39", // Rose
        "\ud83c\udf3a", // Hibiscus
        "\ud83c\udf3b", // Sunflower
        "\ud83c\udf3c", // Blossom
        "\ud83c\udf37", // Tulip
        "\ud83c\udf31", // Seedling
        "\ud83c\udf32", // Evergreen Tree
        "\ud83c\udf33", // Deciduous Tree
        "\ud83c\udf34", // Palm Tree
        "\ud83c\udf35", // Cactus
        "\ud83c\udf3e", // Ear of Rice
        "\ud83c\udf3f", // Herb
        "\u2618", // Shamrock
        "\ud83c\udf40", // Four Leaf Clover
        "\ud83c\udf41", // Maple Leaf
        "\ud83c\udf42", // Fallen Leaf
        "\ud83c\udf43", // Leaf Fluttering in Wind
        "\ud83c\udf0d", // Earth Globe Europe-Africa
        "\ud83c\udf0e", // Earth Globe Americas
        "\ud83c\udf0f", // Earth Globe Asia-Australia
        "\ud83c\udf10", // Globe With Meridians
        "\ud83c\udf11", // New Moon Symbol
        "\ud83c\udf12", // Waxing Crescent Moon Symbol
        "\ud83c\udf13", // First Quarter Moon Symbol
        "\ud83c\udf14", // Waxing Gibbous Moon Symbol
        "\ud83c\udf15", // Full Moon Symbol
        "\ud83c\udf16", // Waning Gibbous Moon Symbol
        "\ud83c\udf17", // Last Quarter Moon Symbol
        "\ud83c\udf18", // Waning Crescent Moon Symbol
        "\ud83c\udf19", // Crescent Moon
        "\ud83c\udf1a", // New Moon With Face
        "\ud83c\udf1b", // First Quarter Moon With Face
        "\ud83c\udf1c", // Last Quarter Moon With Face
        "\u2600", // Black Sun With Rays
        "\ud83c\udf1d", // Full Moon With Face
        "\ud83c\udf1e", // Sun With Face
        "\u2b50", // White Medium Star
        "\ud83c\udf1f", // Glowing Star
        "\ud83c\udf20", // Shooting Star
        "\u2601", // Cloud
        "\u26c5", // Sun Behind Cloud
        "\u26c8", // Thunder Cloud and Rain
        "\ud83c\udf24", // White Sun With Small Cloud
        "\ud83c\udf25", // White Sun Behind Cloud
        "\ud83c\udf26", // White Sun Behind Cloud With Rain
        "\ud83c\udf27", // Cloud With Rain
        "\ud83c\udf28", // Cloud With Snow
        "\ud83c\udf29", // Cloud With Lightning
        "\ud83c\udf2a", // Cloud With Tornado
        "\ud83c\udf2b", // Fog
        "\ud83c\udf2c", // Wind Blowing Face
        "\u2602", // Umbrella
        "\u2614", // Umbrella With Rain Drops
        "\u26a1", // High Voltage Sign
        "\u2744", // Snowflake
        "\u2603", // Snowman
        "\u2604", // Comet
        "\ud83d\udd25", // Fire
        "\ud83d\udca7", // Droplet
        "\ud83c\udf0a" // Water Wave
    };

    public static final String[] OBJECTS = new String[] {
        "\u2620", // Skull and Crossbones
        "\ud83d\udc8c", // Love Letter
        "\ud83d\udca3", // Bomb
        "\ud83d\udd73", // Hole
        "\ud83d\udecd", // Shopping Bags
        "\ud83d\udcff", // Prayer Beads
        "\ud83d\udc8e", // Gem Stone
        "\ud83d\udd2a", // Hocho
        "\ud83c\udffa", // Amphora
        "\ud83d\uddfa", // World Map
        "\ud83d\udc88", // Barber Pole
        "\ud83d\uddbc", // Frame With Picture
        "\ud83d\udece", // Bellhop Bell
        "\ud83d\udeaa", // Door
        "\ud83d\udecc", // Sleeping Accommodation
        "\ud83d\udecf", // Bed
        "\ud83d\udecb", // Couch and Lamp
        "\ud83d\udebd", // Toilet
        "\ud83d\udebf", // Shower
        "\ud83d\udec1", // Bathtub
        "\u231b", // Hourglass
        "\u23f3", // Hourglass With Flowing Sand
        "\u231a", // Watch
        "\u23f0", // Alarm Clock
        "\u23f1", // Stopwatch
        "\u23f2", // Timer Clock
        "\ud83d\udd70", // Mantelpiece Clock
        "\ud83c\udf21", // Thermometer
        "\u26f1", // Umbrella on Ground
        "\ud83c\udf88", // Balloon
        "\ud83c\udf89", // Party Popper
        "\ud83c\udf8a", // Confetti Ball
        "\ud83c\udf8e", // Japanese Dolls
        "\ud83c\udf8f", // Carp Streamer
        "\ud83c\udf90", // Wind Chime
        "\ud83c\udf80", // Ribbon
        "\ud83c\udf81", // Wrapped Present
        "\ud83d\udd79", // Joystick
        "\ud83d\udcef", // Postal Horn
        "\ud83c\udf99", // Studio Microphone
        "\ud83c\udf9a", // Level Slider
        "\ud83c\udf9b", // Control Knobs
        "\ud83d\udcfb", // Radio
        "\ud83d\udcf1", // Mobile Phone
        "\ud83d\udcf2", // Mobile Phone With Rightwards Arrow at Left
        "\u260e", // Black Telephone
        "\ud83d\udcde", // Telephone Receiver
        "\ud83d\udcdf", // Pager
        "\ud83d\udce0", // Fax Machine
        "\ud83d\udd0b", // Battery
        "\ud83d\udd0c", // Electric Plug
        "\ud83d\udcbb", // Personal Computer
        "\ud83d\udda5", // Desktop Computer
        "\ud83d\udda8", // Printer
        "\u2328", // Keyboard
        "\ud83d\uddb1", // Three Button Mouse
        "\ud83d\uddb2", // Trackball
        "\ud83d\udcbd", // Minidisc
        "\ud83d\udcbe", // Floppy Disk
        "\ud83d\udcbf", // Optical Disc
        "\ud83d\udcc0", // DVD
        "\ud83c\udfa5", // Movie Camera
        "\ud83c\udf9e", // Film Frames
        "\ud83d\udcfd", // Film Projector
        "\ud83d\udcfa", // Television
        "\ud83d\udcf7", // Camera
        "\ud83d\udcf8", // Camera With Flash
        "\ud83d\udcf9", // Video Camera
        "\ud83d\udcfc", // Videocassette
        "\ud83d\udd0d", // Left-Pointing Magnifying Glass
        "\ud83d\udd0e", // Right-Pointing Magnifying Glass
        "\ud83d\udd2c", // Microscope
        "\ud83d\udd2d", // Telescope
        "\ud83d\udce1", // Satellite Antenna
        "\ud83d\udd6f", // Candle
        "\ud83d\udca1", // Electric Light Bulb
        "\ud83d\udd26", // Electric Torch
        "\ud83c\udfee", // Izakaya Lantern
        "\ud83d\udcd4", // Notebook With Decorative Cover
        "\ud83d\udcd5", // Closed Book
        "\ud83d\udcd6", // Open Book
        "\ud83d\udcd7", // Green Book
        "\ud83d\udcd8", // Blue Book
        "\ud83d\udcd9", // Orange Book
        "\ud83d\udcda", // Books
        "\ud83d\udcd3", // Notebook
        "\ud83d\udcc3", // Page With Curl
        "\ud83d\udcdc", // Scroll
        "\ud83d\udcc4", // Page Facing Up
        "\ud83d\udcf0", // Newspaper
        "\ud83d\uddde", // Rolled-Up Newspaper
        "\ud83d\udcd1", // Bookmark Tabs
        "\ud83d\udd16", // Bookmark
        "\ud83c\udff7", // Label
        "\ud83d\udcb0", // Money Bag
        "\ud83d\udcb4", // Banknote With Yen Sign
        "\ud83d\udcb5", // Banknote With Dollar Sign
        "\ud83d\udcb6", // Banknote With Euro Sign
        "\ud83d\udcb7", // Banknote With Pound Sign
        "\ud83d\udcb8", // Money With Wings
        "\ud83d\udcb3", // Credit Card
        "\u2709", // Envelope
        "\ud83d\udce7", // E-Mail Symbol
        "\ud83d\udce8", // Incoming Envelope
        "\ud83d\udce9", // Envelope With Downwards Arrow Above
        "\ud83d\udce4", // Outbox Tray
        "\ud83d\udce5", // Inbox Tray
        "\ud83d\udce6", // Package
        "\ud83d\udceb", // Closed Mailbox With Raised Flag
        "\ud83d\udcea", // Closed Mailbox With Lowered Flag
        "\ud83d\udcec", // Open Mailbox With Raised Flag
        "\ud83d\udced", // Open Mailbox With Lowered Flag
        "\ud83d\udcee", // Postbox
        "\ud83d\uddf3", // Ballot Box With Ballot
        "\u270f", // Pencil
        "\u2712", // Black Nib
        "\ud83d\udd8b", // Lower Left Fountain Pen
        "\ud83d\udd8a", // Lower Left Ballpoint Pen
        "\ud83d\udd8c", // Lower Left Paintbrush
        "\ud83d\udd8d", // Lower Left Crayon
        "\ud83d\udcdd", // Memo
        "\ud83d\udcc1", // File Folder
        "\ud83d\udcc2", // Open File Folder
        "\ud83d\uddc2", // Card Index Dividers
        "\ud83d\udcc5", // Calendar
        "\ud83d\udcc6", // Tear-Off Calendar
        "\ud83d\uddd2", // Spiral Note Pad
        "\ud83d\uddd3", // Spiral Calendar Pad
        "\ud83d\udcc7", // Card Index
        "\ud83d\udcc8", // Chart With Upwards Trend
        "\ud83d\udcc9", // Chart With Downwards Trend
        "\ud83d\udcca", // Bar Chart
        "\ud83d\udccb", // Clipboard
        "\ud83d\udccc", // Pushpin
        "\ud83d\udccd", // Round Pushpin
        "\ud83d\udcce", // Paperclip
        "\ud83d\udd87", // Linked Paperclips
        "\ud83d\udccf", // Straight Ruler
        "\ud83d\udcd0", // Triangular Ruler
        "\u2702", // Black Scissors
        "\ud83d\uddc3", // Card File Box
        "\ud83d\uddc4", // File Cabinet
        "\ud83d\uddd1", // Wastebasket
        "\ud83d\udd12", // Lock
        "\ud83d\udd13", // Open Lock
        "\ud83d\udd0f", // Lock With Ink Pen
        "\ud83d\udd10", // Closed Lock With Key
        "\ud83d\udd11", // Key
        "\ud83d\udddd", // Old Key
        "\ud83d\udd28", // Hammer
        "\u26cf", // Pick
        "\u2692", // Hammer and Pick
        "\ud83d\udee0", // Hammer and Wrench
        "\ud83d\udde1", // Dagger Knife
        "\u2694", // Crossed Swords
        "\ud83d\udd2b", // Pistol
        "\ud83d\udee1", // Shield
        "\ud83d\udd27", // Wrench
        "\ud83d\udd29", // Nut and Bolt
        "\u2699", // Gear
        "\ud83d\udddc", // Compression
        "\u2697", // Alembic
        "\u2696", // Scales
        "\ud83d\udd17", // Link Symbol
        "\u26d3", // Chains
        "\ud83d\udc89", // Syringe
        "\ud83d\udc8a", // Pill
        "\ud83d\udeac", // Smoking Symbol
        "\u26b0", // Coffin
        "\u26b1", // Funeral Urn
        "\ud83d\uddff", // Moyai
        "\ud83d\udee2", // Oil Drum
        "\ud83d\udd2e", // Crystal Ball
        "\ud83d\udea9", // Triangular Flag on Post
        "\ud83c\udf8c", // Crossed Flags
        "\ud83c\udff4", // Waving Black Flag
        "\ud83c\udff3", // Waving White Flag
        "\ud83c\udff3\ufe0f\u200d\ud83c\udf08" // Rainbow Flag
    };

    public static final String[] PEOPLE = new String[] {
        "\ud83d\ude00", // Grinning Face
        "\ud83d\ude01", // Grinning Face With Smiling Eyes
        "\ud83d\ude02", // Face With Tears of Joy
        "\ud83d\ude03", // Smiling Face With Open Mouth
        "\ud83d\ude04", // Smiling Face With Open Mouth and Smiling Eyes
        "\ud83d\ude05", // Smiling Face With Open Mouth and Cold Sweat
        "\ud83d\ude06", // Smiling Face With Open Mouth and Tightly-Closed Eyes
        "\ud83d\ude09", // Winking Face
        "\ud83d\ude0a", // Smiling Face With Smiling Eyes
        "\ud83d\ude0b", // Face Savouring Delicious Food
        "\ud83d\ude0e", // Smiling Face With Sunglasses
        "\ud83d\ude0d", // Smiling Face With Heart-Shaped Eyes
        "\ud83d\ude18", // Face Throwing a Kiss
        "\ud83d\ude17", // Kissing Face
        "\ud83d\ude19", // Kissing Face With Smiling Eyes
        "\ud83d\ude1a", // Kissing Face With Closed Eyes
        "\u263a", // White Smiling Face
        "\ud83d\ude42", // Slightly Smiling Face
        "\ud83e\udd17", // Hugging Face
        "\ud83d\ude07", // Smiling Face With Halo
        "\ud83e\udd13", // Nerd Face
        "\ud83e\udd14", // Thinking Face
        "\ud83d\ude10", // Neutral Face
        "\ud83d\ude11", // Expressionless Face
        "\ud83d\ude36", // Face Without Mouth
        "\ud83d\ude44", // Face With Rolling Eyes
        "\ud83d\ude0f", // Smirking Face
        "\ud83d\ude23", // Persevering Face
        "\ud83d\ude25", // Disappointed but Relieved Face
        "\ud83d\ude2e", // Face With Open Mouth
        "\ud83e\udd10", // Zipper-Mouth Face
        "\ud83d\ude2f", // Hushed Face
        "\ud83d\ude2a", // Sleepy Face
        "\ud83d\ude2b", // Tired Face
        "\ud83d\ude34", // Sleeping Face
        "\ud83d\ude0c", // Relieved Face
        "\ud83d\ude1b", // Face With Stuck-Out Tongue
        "\ud83d\ude1c", // Face With Stuck-Out Tongue and Winking Eye
        "\ud83d\ude1d", // Face With Stuck-Out Tongue and Tightly-Closed Eyes
        "\ud83d\ude12", // Unamused Face
        "\ud83d\ude13", // Face With Cold Sweat
        "\ud83d\ude14", // Pensive Face
        "\ud83d\ude15", // Confused Face
        "\ud83d\ude43", // Upside-Down Face
        "\ud83e\udd11", // Money-Mouth Face
        "\ud83d\ude32", // Astonished Face
        "\ud83d\ude37", // Face With Medical Mask
        "\ud83e\udd12", // Face With Thermometer
        "\ud83e\udd15", // Face With Head-Bandage
        "\u2639", // White Frowning Face
        "\ud83d\ude41", // Slightly Frowning Face
        "\ud83d\ude16", // Confounded Face
        "\ud83d\ude1e", // Disappointed Face
        "\ud83d\ude1f", // Worried Face
        "\ud83d\ude24", // Face With Look of Triumph
        "\ud83d\ude22", // Crying Face
        "\ud83d\ude2d", // Loudly Crying Face
        "\ud83d\ude26", // Frowning Face With Open Mouth
        "\ud83d\ude27", // Anguished Face
        "\ud83d\ude28", // Fearful Face
        "\ud83d\ude29", // Weary Face
        "\ud83d\ude2c", // Grimacing Face
        "\ud83d\ude30", // Face With Open Mouth and Cold Sweat
        "\ud83d\ude31", // Face Screaming in Fear
        "\ud83d\ude33", // Flushed Face
        "\ud83d\ude35", // Dizzy Face
        "\ud83d\ude21", // Pouting Face
        "\ud83d\ude20", // Angry Face
        "\ud83d\ude08", // Smiling Face With Horns
        "\ud83d\udc7f", // Imp
        "\ud83d\udc79", // Japanese Ogre
        "\ud83d\udc7a", // Japanese Goblin
        "\ud83d\udc80", // Skull
        "\ud83d\udc7b", // Ghost
        "\ud83d\udc7d", // Extraterrestrial Alien
        "\ud83e\udd16", // Robot Face
        "\ud83d\udca9", // Pile of Poo
        "\ud83d\ude3a", // Smiling Cat Face With Open Mouth
        "\ud83d\ude38", // Grinning Cat Face With Smiling Eyes
        "\ud83d\ude39", // Cat Face With Tears of Joy
        "\ud83d\ude3b", // Smiling Cat Face With Heart-Shaped Eyes
        "\ud83d\ude3c", // Cat Face With Wry Smile
        "\ud83d\ude3d", // Kissing Cat Face With Closed Eyes
        "\ud83d\ude40", // Weary Cat Face
        "\ud83d\ude3f", // Crying Cat Face
        "\ud83d\ude3e", // Pouting Cat Face
        "\ud83d\udc66", // Boy
        "\ud83d\udc67", // Girl
        "\ud83d\udc68", // Man
        "\ud83d\udc69", // Woman
        "\ud83d\udc74", // Older Man
        "\ud83d\udc75", // Older Woman
        "\ud83d\udc76", // Baby
        "\ud83d\udc71", // Person With Blond Hair
        "\ud83d\udc6e", // Police Officer
        "\ud83d\udc72", // Man With Gua Pi Mao
        "\ud83d\udc73", // Man With Turban
        "\ud83d\udc77", // Construction Worker
        "\u26d1", // Helmet With White Cross
        "\ud83d\udc78", // Princess
        "\ud83d\udc82", // Guardsman
        "\ud83d\udd75", // Sleuth or Spy
        "\ud83c\udf85", // Father Christmas
        "\ud83d\udc70", // Bride With Veil
        "\ud83d\udc7c", // Baby Angel
        "\ud83d\udc86", // Face Massage
        "\ud83d\udc87", // Haircut
        "\ud83d\ude4d", // Person Frowning
        "\ud83d\ude4e", // Person With Pouting Face
        "\ud83d\ude45", // Face With No Good Gesture
        "\ud83d\ude46", // Face With OK Gesture
        "\ud83d\udc81", // Information Desk Person
        "\ud83d\ude4b", // Happy Person Raising One Hand
        "\ud83d\ude47", // Person Bowing Deeply
        "\ud83d\ude4c", // Person Raising Both Hands in Celebration
        "\ud83d\ude4f", // Person With Folded Hands
        "\ud83d\udde3", // Speaking Head in Silhouette
        "\ud83d\udc64", // Bust in Silhouette
        "\ud83d\udc65", // Busts in Silhouette
        "\ud83d\udeb6", // Pedestrian
        "\ud83c\udfc3", // Runner
        "\ud83d\udc6f", // Woman With Bunny Ears
        "\ud83d\udc83", // Dancer
        "\ud83d\udd74", // Man in Business Suit Levitating
        "\ud83d\udc6b", // Man and Woman Holding Hands
        "\ud83d\udc6c", // Two Men Holding Hands
        "\ud83d\udc6d", // Two Women Holding Hands
        "\ud83d\udc8f", // Kiss
        "\ud83d\udc68\u200d\u2764\ufe0f\u200d\ud83d\udc8b\u200d\ud83d\udc68", // Kiss (Man, Man)
        "\ud83d\udc69\u200d\u2764\ufe0f\u200d\ud83d\udc8b\u200d\ud83d\udc69", // Kiss (Woman, Woman)
        "\ud83d\udc91", // Couple With Heart
        "\ud83d\udc68\u200d\u2764\ufe0f\u200d\ud83d\udc68", // Couple With Heart (Man, Man)
        "\ud83d\udc69\u200d\u2764\ufe0f\u200d\ud83d\udc69", // Couple With Heart (Woman, Woman)
        "\ud83d\udc6a", // Family
        "\ud83d\udc68\u200d\ud83d\udc69\u200d\ud83d\udc67", // Family
        "\ud83d\udc68\u200d\ud83d\udc69\u200d\ud83d\udc67\u200d\ud83d\udc66", // Family
        "\ud83d\udc68\u200d\ud83d\udc69\u200d\ud83d\udc66\u200d\ud83d\udc66", // Family
        "\ud83d\udc68\u200d\ud83d\udc69\u200d\ud83d\udc67\u200d\ud83d\udc67", // Family
        "\ud83d\udc68\u200d\ud83d\udc68\u200d\ud83d\udc66", // Family
        "\ud83d\udc68\u200d\ud83d\udc68\u200d\ud83d\udc67", // Family
        "\ud83d\udc68\u200d\ud83d\udc68\u200d\ud83d\udc67\u200d\ud83d\udc66", // Family
        "\ud83d\udc68\u200d\ud83d\udc68\u200d\ud83d\udc66\u200d\ud83d\udc66", // Family
        "\ud83d\udc68\u200d\ud83d\udc68\u200d\ud83d\udc67\u200d\ud83d\udc67", // Family
        "\ud83d\udc69\u200d\ud83d\udc69\u200d\ud83d\udc66", // Family
        "\ud83d\udc69\u200d\ud83d\udc69\u200d\ud83d\udc67", // Family
        "\ud83d\udc69\u200d\ud83d\udc69\u200d\ud83d\udc67\u200d\ud83d\udc66", // Family
        "\ud83d\udc69\u200d\ud83d\udc69\u200d\ud83d\udc66\u200d\ud83d\udc66", // Family
        "\ud83d\udc69\u200d\ud83d\udc69\u200d\ud83d\udc67\u200d\ud83d\udc67", // Family
        "\ud83d\udcaa", // Flexed Biceps
        "\ud83d\udc48", // White Left Pointing Backhand Index
        "\ud83d\udc49", // White Right Pointing Backhand Index
        "\u261d", // White Up Pointing Index
        "\ud83d\udc46", // White Up Pointing Backhand Index
        "\ud83d\udd95", // Reversed Hand With Middle Finger Extended
        "\ud83d\udc47", // White Down Pointing Backhand Index
        "\u270c", // Victory Hand
        "\ud83d\udd96", // Raised Hand With Part Between Middle and Ring Fingers
        "\ud83e\udd18", // Sign of the Horns
        "\ud83d\udd90", // Raised Hand With Fingers Splayed
        "\u270b", // Raised Hand
        "\ud83d\udc4c", // OK Hand Sign
        "\ud83d\udc4d", // Thumbs Up Sign
        "\ud83d\udc4e", // Thumbs Down Sign
        "\u270a", // Raised Fist
        "\ud83d\udc4a", // Fisted Hand Sign
        "\ud83d\udc4b", // Waving Hand Sign
        "\ud83d\udc4f", // Clapping Hands Sign
        "\ud83d\udc50", // Open Hands Sign
        "\u270d", // Writing Hand
        "\ud83d\udc85", // Nail Polish
        "\ud83d\udc42", // Ear
        "\ud83d\udc43", // Nose
        "\ud83d\udc63", // Footprints
        "\ud83d\udc40", // Eyes
        "\ud83d\udc41", // Eye
        "\ud83d\udc45", // Tongue
        "\ud83d\udc44", // Mouth
        "\ud83d\udc8b", // Kiss Mark
        "\ud83d\udc53", // Eyeglasses
        "\ud83d\udd76", // Dark Sunglasses
        "\ud83d\udc54", // Necktie
        "\ud83d\udc55", // T-Shirt
        "\ud83d\udc56", // Jeans
        "\ud83d\udc57", // Dress
        "\ud83d\udc58", // Kimono
        "\ud83d\udc59", // Bikini
        "\ud83d\udc5a", // Womans Clothes
        "\ud83d\udc5b", // Purse
        "\ud83d\udc5c", // Handbag
        "\ud83d\udc5d", // Pouch
        "\ud83c\udf92", // School Satchel
        "\ud83d\udc5e", // Mans Shoe
        "\ud83d\udc5f", // Athletic Shoe
        "\ud83d\udc60", // High-Heeled Shoe
        "\ud83d\udc61", // Womans Sandal
        "\ud83d\udc62", // Womans Boots
        "\ud83d\udc51", // Crown
        "\ud83d\udc52", // Womans Hat
        "\ud83c\udfa9", // Top Hat
        "\ud83c\udf93", // Graduation Cap
        "\ud83d\udc84", // Lipstick
        "\ud83d\udc8d", // Ring
        "\ud83c\udf02", // Closed Umbrella
        "\ud83d\udcbc" // Briefcase
    };

    public static final String[] SYMBOLS = new String[] {
        "\ud83d\udc41\u200d\ud83d\udde8", // Eye in Speech Bubble
        "\ud83d\udc98", // Heart With Arrow
        "\u2764", // Heavy Black Heart
        "\ud83d\udc93", // Beating Heart
        "\ud83d\udc94", // Broken Heart
        "\ud83d\udc95", // Two Hearts
        "\ud83d\udc96", // Sparkling Heart
        "\ud83d\udc97", // Growing Heart
        "\ud83d\udc99", // Blue Heart
        "\ud83d\udc9a", // Green Heart
        "\ud83d\udc9b", // Yellow Heart
        "\ud83d\udc9c", // Purple Heart
        "\ud83d\udc9d", // Heart With Ribbon
        "\ud83d\udc9e", // Revolving Hearts
        "\ud83d\udc9f", // Heart Decoration
        "\u2763", // Heavy Heart Exclamation Mark Ornament
        "\ud83d\udca4", // Sleeping Symbol
        "\ud83d\udca2", // Anger Symbol
        "\ud83d\udcac", // Speech Balloon
        "\ud83d\uddef", // Right Anger Bubble
        "\ud83d\udcad", // Thought Balloon
        "\ud83d\udcae", // White Flower
        "\u2668", // Hot Springs
        "\ud83d\udc88", // Barber Pole
        "\ud83d\udd5b", // Clock Face Twelve O'Clock
        "\ud83d\udd67", // Clock Face Twelve-Thirty
        "\ud83d\udd50", // Clock Face One O'Clock
        "\ud83d\udd5c", // Clock Face One-Thirty
        "\ud83d\udd51", // Clock Face Two O'Clock
        "\ud83d\udd5d", // Clock Face Two-Thirty
        "\ud83d\udd52", // Clock Face Three O'Clock
        "\ud83d\udd5e", // Clock Face Three-Thirty
        "\ud83d\udd53", // Clock Face Four O'Clock
        "\ud83d\udd5f", // Clock Face Four-Thirty
        "\ud83d\udd54", // Clock Face Five O'Clock
        "\ud83d\udd60", // Clock Face Five-Thirty
        "\ud83d\udd55", // Clock Face Six O'Clock
        "\ud83d\udd61", // Clock Face Six-Thirty
        "\ud83d\udd56", // Clock Face Seven O'Clock
        "\ud83d\udd62", // Clock Face Seven-Thirty
        "\ud83d\udd57", // Clock Face Eight O'Clock
        "\ud83d\udd63", // Clock Face Eight-Thirty
        "\ud83d\udd58", // Clock Face Nine O'Clock
        "\ud83d\udd64", // Clock Face Nine-Thirty
        "\ud83d\udd59", // Clock Face Ten O'Clock
        "\ud83d\udd65", // Clock Face Ten-Thirty
        "\ud83d\udd5a", // Clock Face Eleven O'Clock
        "\ud83d\udd66", // Clock Face Eleven-Thirty
        "\ud83c\udf00", // Cyclone
        "\u2660", // Black Spade Suit
        "\u2665", // Black Heart Suit
        "\u2666", // Black Diamond Suit
        "\u2663", // Black Club Suit
        "\ud83c\udc04", // Mahjong Tile Red Dragon
        "\ud83c\udfb4", // Flower Playing Cards
        "\ud83d\udd07", // Speaker With Cancellation Stroke
        "\ud83d\udd08", // Speaker
        "\ud83d\udd09", // Speaker With One Sound Wave
        "\ud83d\udd0a", // Speaker With Three Sound Waves
        "\ud83d\udce2", // Public Address Loudspeaker
        "\ud83d\udce3", // Cheering Megaphone
        "\ud83d\udcef", // Postal Horn
        "\ud83d\udd14", // Bell
        "\ud83d\udd15", // Bell With Cancellation Stroke
        "\ud83c\udfe7", // Automated Teller Machine
        "\ud83d\udeae", // Put Litter in Its Place Symbol
        "\ud83d\udeb0", // Potable Water Symbol
        "\u267f", // Wheelchair Symbol
        "\ud83d\udeb9", // Mens Symbol
        "\ud83d\udeba", // Womens Symbol
        "\ud83d\udebb", // Restroom
        "\ud83d\udebc", // Baby Symbol
        "\ud83d\udebe", // Water Closet
        "\u26a0", // Warning Sign
        "\ud83d\udeb8", // Children Crossing
        "\u26d4", // No Entry
        "\ud83d\udeab", // No Entry Sign
        "\ud83d\udeb3", // No Bicycles
        "\ud83d\udead", // No Smoking Symbol
        "\ud83d\udeaf", // Do Not Litter Symbol
        "\ud83d\udeb1", // Non-Potable Water Symbol
        "\ud83d\udeb7", // No Pedestrians
        "\ud83d\udd1e", // No One Under Eighteen Symbol
        "\u2622", // Radioactive Sign
        "\u2623", // Biohazard Sign
        "\u2b06", // Upwards Black Arrow
        "\u2197", // North East Arrow
        "\u27a1", // Black Rightwards Arrow
        "\u2198", // South East Arrow
        "\u2b07", // Downwards Black Arrow
        "\u2199", // South West Arrow
        "\u2b05", // Leftwards Black Arrow
        "\u2196", // North West Arrow
        "\u2195", // Up Down Arrow
        "\u2194", // Left Right Arrow
        "\u21a9", // Leftwards Arrow With Hook
        "\u21aa", // Rightwards Arrow With Hook
        "\u2934", // Arrow Pointing Rightwards Then Curving Upwards
        "\u2935", // Arrow Pointing Rightwards Then Curving Downwards
        "\ud83d\udd03", // Clockwise Downwards and Upwards Open Circle Arrows
        "\ud83d\udd04", // Anticlockwise Downwards and Upwards Open Circle Arrows
        "\ud83d\udd19", // Back With Leftwards Arrow Above
        "\ud83d\udd1a", // End With Leftwards Arrow Above
        "\ud83d\udd1b", // On With Exclamation Mark With Left Right Arrow Above
        "\ud83d\udd1c", // Soon With Rightwards Arrow Above
        "\ud83d\udd1d", // Top With Upwards Arrow Above
        "\ud83d\uded0", // Place of Worship
        "\u269b", // Atom Symbol
        "\ud83d\udd49", // Om Symbol
        "\u2721", // Star of David
        "\u2638", // Wheel of Dharma
        "\u262f", // Yin Yang
        "\u271d", // Latin Cross
        "\u2626", // Orthodox Cross
        "\u262a", // Star and Crescent
        "\u262e", // Peace Symbol
        "\ud83d\udd4e", // Menorah With Nine Branches
        "\ud83d\udd2f", // Six Pointed Star With Middle Dot
        "\u267b", // Black Universal Recycling Symbol
        "\ud83d\udcdb", // Name Badge
        "\ud83d\udd30", // Japanese Symbol for Beginner
        "\ud83d\udd31", // Trident Emblem
        "\u2b55", // Heavy Large Circle
        "\u2705", // White Heavy Check Mark
        "\u2611", // Ballot Box With Check
        "\u2714", // Heavy Check Mark
        "\u2716", // Heavy Multiplication X
        "\u274c", // Cross Mark
        "\u274e", // Negative Squared Cross Mark
        "\u2795", // Heavy Plus Sign
        "\u2796", // Heavy Minus Sign
        "\u2797", // Heavy Division Sign
        "\u27b0", // Curly Loop
        "\u27bf", // Double Curly Loop
        "\u303d", // Part Alternation Mark
        "\u2733", // Eight Spoked Asterisk
        "\u2734", // Eight Pointed Black Star
        "\u2747", // Sparkle
        "\u203c", // Double Exclamation Mark
        "\u2049", // Exclamation Question Mark
        "\u2753", // Black Question Mark Ornament
        "\u2754", // White Question Mark Ornament
        "\u2755", // White Exclamation Mark Ornament
        "\u2757", // Heavy Exclamation Mark Symbol
        "\u00a9", // Copyright Sign
        "\u00ae", // Registered Sign
        "\u2122", // Trade Mark Sign
        "\u2648", // Aries
        "\u2649", // Taurus
        "\u264a", // Gemini
        "\u264b", // Cancer
        "\u264c", // Leo
        "\u264d", // Virgo
        "\u264e", // Libra
        "\u264f", // Scorpius
        "\u2650", // Sagittarius
        "\u2651", // Capricorn
        "\u2652", // Aquarius
        "\u2653", // Pisces
        "\u26ce", // Ophiuchus
        "\ud83d\udd00", // Twisted Rightwards Arrows
        "\ud83d\udd01", // Clockwise Rightwards and Leftwards Open Circle Arrows
        "\ud83d\udd02", // Clockwise Rightwards and Leftwards Open Circle Arrows With Circled One Overlay
        "\u25b6", // Black Right-Pointing Triangle
        "\u23e9", // Black Right-Pointing Double Triangle
        "\u25c0", // Black Left-Pointing Triangle
        "\u23ea", // Black Left-Pointing Double Triangle
        "\ud83d\udd3c", // Up-Pointing Small Red Triangle
        "\u23eb", // Black Up-Pointing Double Triangle
        "\ud83d\udd3d", // Down-Pointing Small Red Triangle
        "\u23ec", // Black Down-Pointing Double Triangle
        "\u23f9", // Black Square for Stop
        "\ud83c\udfa6", // Cinema
        "\ud83d\udd05", // Low Brightness Symbol
        "\ud83d\udd06", // High Brightness Symbol
        "\ud83d\udcf6", // Antenna With Bars
        "\ud83d\udcf3", // Vibration Mode
        "\ud83d\udcf4", // Mobile Phone Off
        "#\ufe0f\u20e3", // Keycap Number Sign
        "0\ufe0f\u20e3", // Keycap Digit Zero
        "1\ufe0f\u20e3", // Keycap Digit One
        "2\ufe0f\u20e3", // Keycap Digit Two
        "3\ufe0f\u20e3", // Keycap Digit Three
        "4\ufe0f\u20e3", // Keycap Digit Four
        "5\ufe0f\u20e3", // Keycap Digit Five
        "6\ufe0f\u20e3", // Keycap Digit Six
        "7\ufe0f\u20e3", // Keycap Digit Seven
        "8\ufe0f\u20e3", // Keycap Digit Eight
        "9\ufe0f\u20e3", // Keycap Digit Nine
        "\ud83d\udd1f", // Keycap Ten
        "\ud83d\udcaf", // Hundred Points Symbol
        "\ud83d\udd20", // Input Symbol for Latin Capital Letters
        "\ud83d\udd21", // Input Symbol for Latin Small Letters
        "\ud83d\udd22", // Input Symbol for Numbers
        "\ud83d\udd23", // Input Symbol for Symbols
        "\ud83d\udd24", // Input Symbol for Latin Letters
        "\ud83c\udd70", // Negative Squared Latin Capital Letter A
        "\ud83c\udd8e", // Negative Squared AB
        "\ud83c\udd71", // Negative Squared Latin Capital Letter B
        "\ud83c\udd91", // Squared CL
        "\ud83c\udd92", // Squared Cool
        "\ud83c\udd93", // Squared Free
        "\u2139", // Information Source
        "\ud83c\udd94", // Squared ID
        "\u24c2", // Circled Latin Capital Letter M
        "\ud83c\udd95", // Squared New
        "\ud83c\udd96", // Squared NG
        "\ud83c\udd7e", // Negative Squared Latin Capital Letter O
        "\ud83c\udd97", // Squared OK
        "\ud83c\udd7f", // Negative Squared Latin Capital Letter P
        "\ud83c\udd98", // Squared SOS
        "\ud83c\udd99", // Squared Up With Exclamation Mark
        "\ud83c\udd9a", // Squared Vs
        "\ud83c\ude01", // Squared Katakana Koko
        "\ud83c\ude02", // Squared Katakana Sa
        "\ud83c\ude37", // Squared CJK Unified Ideograph-6708
        "\ud83c\ude36", // Squared CJK Unified Ideograph-6709
        "\ud83c\ude2f", // Squared CJK Unified Ideograph-6307
        "\ud83c\ude50", // Circled Ideograph Advantage
        "\ud83c\ude39", // Squared CJK Unified Ideograph-5272
        "\ud83c\ude1a", // Squared CJK Unified Ideograph-7121
        "\ud83c\ude32", // Squared CJK Unified Ideograph-7981
        "\ud83c\ude51", // Circled Ideograph Accept
        "\ud83c\ude38", // Squared CJK Unified Ideograph-7533
        "\ud83c\ude34", // Squared CJK Unified Ideograph-5408
        "\ud83c\ude33", // Squared CJK Unified Ideograph-7a7a
        "\u3297", // Circled Ideograph Congratulation
        "\u3299", // Circled Ideograph Secret
        "\ud83c\ude3a", // Squared CJK Unified Ideograph-55b6
        "\ud83c\ude35", // Squared CJK Unified Ideograph-6e80
        "\u25aa", // Black Small Square
        "\u25ab", // White Small Square
        "\u25fb", // White Medium Square
        "\u25fc", // Black Medium Square
        "\u25fd", // White Medium Small Square
        "\u25fe", // Black Medium Small Square
        "\u2b1b", // Black Large Square
        "\u2b1c", // White Large Square
        "\ud83d\udd36", // Large Orange Diamond
        "\ud83d\udd37", // Large Blue Diamond
        "\ud83d\udd38", // Small Orange Diamond
        "\ud83d\udd39", // Small Blue Diamond
        "\ud83d\udd3a", // Up-Pointing Red Triangle
        "\ud83d\udd3b", // Down-Pointing Red Triangle
        "\ud83d\udca0", // Diamond Shape With a Dot Inside
        "\ud83d\udd32", // Black Square Button
        "\ud83d\udd33", // White Square Button
        "\u26aa", // Medium White Circle
        "\u26ab", // Medium Black Circle
        "\ud83d\udd34", // Large Red Circle
        "\ud83d\udd35" // Large Blue Circle
    };

    public static final String[] TRAVEL_AND_PLACES = new String[] {
        "\ud83c\udfd4", // Snow Capped Mountain
        "\u26f0", // Mountain
        "\ud83c\udf0b", // Volcano
        "\ud83d\uddfb", // Mount Fuji
        "\ud83c\udfd5", // Camping
        "\ud83c\udfd6", // Beach With Umbrella
        "\ud83c\udfdc", // Desert
        "\ud83c\udfdd", // Desert Island
        "\ud83c\udfde", // National Park
        "\ud83c\udfdf", // Stadium
        "\ud83c\udfdb", // Classical Building
        "\ud83c\udfd7", // Building Construction
        "\ud83c\udfd8", // House Buildings
        "\ud83c\udfd9", // Cityscape
        "\ud83c\udfda", // Derelict House Building
        "\ud83c\udfe0", // House Building
        "\ud83c\udfe1", // House With Garden
        "\ud83c\udfe2", // Office Building
        "\ud83c\udfe3", // Japanese Post Office
        "\ud83c\udfe4", // European Post Office
        "\ud83c\udfe5", // Hospital
        "\ud83c\udfe6", // Bank
        "\ud83c\udfe8", // Hotel
        "\ud83c\udfe9", // Love Hotel
        "\ud83c\udfea", // Convenience Store
        "\ud83c\udfeb", // School
        "\ud83c\udfec", // Department Store
        "\ud83c\udfed", // Factory
        "\ud83c\udfef", // Japanese Castle
        "\ud83c\udff0", // European Castle
        "\ud83d\udc92", // Wedding
        "\ud83d\uddfc", // Tokyo Tower
        "\ud83d\uddfd", // Statue of Liberty
        "\u26ea", // Church
        "\ud83d\udd4c", // Mosque
        "\ud83d\udd4d", // Synagogue
        "\u26e9", // Shinto Shrine
        "\ud83d\udd4b", // Kaaba
        "\u26f2", // Fountain
        "\ud83c\udf01", // Foggy
        "\ud83c\udf03", // Night With Stars
        "\ud83c\udf06", // Cityscape at Dusk
        "\ud83c\udf07", // Sunset Over Buildings
        "\ud83c\udf09", // Bridge at Night
        "\ud83c\udf0c", // Milky Way
        "\ud83c\udfa0", // Carousel Horse
        "\ud83c\udfa1", // Ferris Wheel
        "\ud83c\udfa2", // Roller Coaster
        "\ud83d\ude82", // Steam Locomotive
        "\ud83d\ude83", // Railway Car
        "\ud83d\ude84", // High-Speed Train
        "\ud83d\ude85", // High-Speed Train With Bullet Nose
        "\ud83d\ude86", // Train
        "\ud83d\ude87", // Metro
        "\ud83d\ude88", // Light Rail
        "\ud83d\ude89", // Station
        "\ud83d\ude8a", // Tram
        "\ud83d\ude9d", // Monorail
        "\ud83d\ude9e", // Mountain Railway
        "\ud83d\ude8b", // Tram Car
        "\ud83d\ude8c", // Bus
        "\ud83d\ude8d", // Oncoming Bus
        "\ud83d\ude8e", // Trolleybus
        "\ud83d\ude8f", // Bus Stop
        "\ud83d\ude90", // Minibus
        "\ud83d\ude91", // Ambulance
        "\ud83d\ude92", // Fire Engine
        "\ud83d\ude93", // Police Car
        "\ud83d\ude94", // Oncoming Police Car
        "\ud83d\ude95", // Taxi
        "\ud83d\ude96", // Oncoming Taxi
        "\ud83d\ude97", // Automobile
        "\ud83d\ude98", // Oncoming Automobile
        "\ud83d\ude9a", // Delivery Truck
        "\ud83d\ude9b", // Articulated Lorry
        "\ud83d\ude9c", // Tractor
        "\ud83d\udeb2", // Bicycle
        "\u26fd", // Fuel Pump
        "\ud83d\udee4", // Railway Track
        "\ud83d\udea8", // Police Cars Revolving Light
        "\ud83d\udea5", // Horizontal Traffic Light
        "\ud83d\udea6", // Vertical Traffic Light
        "\ud83d\udea7", // Construction Sign
        "\u2693", // Anchor
        "\u26f5", // Sailboat
        "\ud83d\udea3", // Rowboat
        "\ud83d\udea4", // Speedboat
        "\ud83d\udef3", // Passenger Ship
        "\u26f4", // Ferry
        "\ud83d\udee5", // Motor Boat
        "\ud83d\udea2", // Ship
        "\u2708", // Airplane
        "\ud83d\udee9", // Small Airplane
        "\ud83d\udeeb", // Airplane Departure
        "\ud83d\udeec", // Airplane Arriving
        "\ud83d\udcba", // Seat
        "\ud83d\ude81", // Helicopter
        "\ud83d\ude9f", // Suspension Railway
        "\ud83d\udea0", // Mountain Cableway
        "\ud83d\udea1", // Aerial Tramway
        "\ud83d\ude80", // Rocket
        "\ud83d\udef0", // Satellite
        "\ud83c\udf91", // Moon Viewing Ceremony
        "\ud83c\udfce", // Racing Car
        "\ud83c\udfcd", // Racing Motorcycle
        "\ud83d\udcb4", // Banknote With Yen Sign
        "\ud83d\udcb5", // Banknote With Dollar Sign
        "\ud83d\udcb6", // Banknote With Euro Sign
        "\ud83d\udcb7", // Banknote With Pound Sign
        "\ud83d\uddff", // Moyai
        "\ud83d\udec2", // Passport Control
        "\ud83d\udec3", // Customs
        "\ud83d\udec4", // Baggage Claim
        "\ud83d\udec5" // Left Luggage
    };

}
