/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.api.scala;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.RawRes;
import android.text.TextUtils;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.R;
import com.waz.zclient.controllers.userpreferences.UserPreferencesController;
import com.waz.zclient.core.stores.media.MediaStore;
import com.waz.zclient.utils.RingtoneUtils;
import timber.log.Timber;

public class ScalaMediaStore extends MediaStore {

    private Context context;
    private ZMessagingApi zMessagingApi;

    public ScalaMediaStore(Context context, ZMessagingApi zMessagingApi) {
        this.context = context;
        this.zMessagingApi = zMessagingApi;

        final SharedPreferences preferences = context.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE);
        setCustomSoundUrisFromPreferences(preferences);
    }

    @Override
    public void tearDown() {
        super.tearDown();
        context = null;
        zMessagingApi = null;
    }

    /**
     * Plays a sound from raw resource.
     *
     * @param resourceId Should be something like R.raw.alert
     */
    @Override
    public void playSound(@RawRes int resourceId) {
        Timber.i("playSound: %s (AVS MediaManager intensity level = %s)",
                 context.getResources().getResourceEntryName(resourceId),
                 zMessagingApi.getMediaManager().getIntensity());
        zMessagingApi.getMediaManager().playMedia(context.getResources().getResourceEntryName(resourceId));
    }

    @Override
    public void stopSound(@RawRes int resourceId) {
        Timber.i("stopSound: %s", context.getResources().getResourceEntryName(resourceId));
        zMessagingApi.getMediaManager().stopMedia(context.getResources().getResourceEntryName(resourceId));
    }

    @Override
    public void setCustomSoundUri(@RawRes int resourceId, String uri) {
        try {
            if (TextUtils.isEmpty(uri)) {
                Timber.i("Set no sound for '%s'", context.getResources().getResourceEntryName(resourceId));
                zMessagingApi.getMediaManager().unregisterMedia(context.getResources().getResourceEntryName(resourceId));
            } else {
                final Uri parsedUri = Uri.parse(uri);
                Timber.i("Set '%s' for sound '%s'", uri, context.getResources().getResourceEntryName(resourceId));
                zMessagingApi.getMediaManager().registerMediaFileUrl(context.getResources().getResourceEntryName(resourceId), parsedUri);
            }
        } catch (Exception e) {
            Timber.e(e, "Could not set custom uri: %s", uri);
        }
    }

    @Override
    public void setCustomSoundUrisFromPreferences(SharedPreferences preferences) {
        //The value of the preference is the uri for the sound resource
        //If this value is an empty string, then it should be silent
        //If the preference doesn't exist yet (null) we assume the default value instead
        String customSoundUri = preferences.getString(context.getString(R.string.pref_options_ringtones_ringtone_key), null);
        if (customSoundUri != null) {
            setCustomSoundUri(R.raw.ringing_from_them, customSoundUri);
            if (RingtoneUtils.isDefaultValue(context, customSoundUri, R.raw.ringing_from_them)) {
                setCustomSoundUri(R.raw.ringing_from_me, RingtoneUtils.getUriForRawId(context, R.raw.ringing_from_me).toString());
                setCustomSoundUri(R.raw.ringing_from_me_video, RingtoneUtils.getUriForRawId(context, R.raw.ringing_from_me_video).toString());
                setCustomSoundUri(R.raw.ringing_from_them_incall, RingtoneUtils.getUriForRawId(context, R.raw.ringing_from_them_incall).toString());
            } else {
                setCustomSoundUri(R.raw.ringing_from_me, customSoundUri);
                setCustomSoundUri(R.raw.ringing_from_me_video, customSoundUri);
                setCustomSoundUri(R.raw.ringing_from_them_incall, customSoundUri);
            }
        }

        customSoundUri = preferences.getString(context.getString(R.string.pref_options_ringtones_ping_key), null);
        if (customSoundUri != null) {
            setCustomSoundUri(R.raw.ping_from_them, customSoundUri);
            if (RingtoneUtils.isDefaultValue(context, customSoundUri, R.raw.ping_from_them)) {
                setCustomSoundUri(R.raw.ping_from_me, RingtoneUtils.getUriForRawId(context, R.raw.ping_from_me).toString());
            } else {
                setCustomSoundUri(R.raw.ping_from_me, customSoundUri);
            }
        }

        customSoundUri = preferences.getString(context.getString(R.string.pref_options_ringtones_text_key), null);
        if (customSoundUri != null) {
            setCustomSoundUri(R.raw.new_message, customSoundUri);
            if (RingtoneUtils.isDefaultValue(context, customSoundUri, R.raw.new_message)) {
                setCustomSoundUri(R.raw.first_message, RingtoneUtils.getUriForRawId(context, R.raw.first_message).toString());
                setCustomSoundUri(R.raw.new_message_gcm, RingtoneUtils.getUriForRawId(context, R.raw.new_message_gcm).toString());
            } else {
                setCustomSoundUri(R.raw.first_message, customSoundUri);
                setCustomSoundUri(R.raw.new_message_gcm, customSoundUri);
            }
        }
    }
}
