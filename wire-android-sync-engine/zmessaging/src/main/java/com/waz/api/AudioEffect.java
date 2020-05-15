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

import static com.waz.audioeffect.AudioEffect.*;

public enum AudioEffect {
    CHORUS_MIN        (AVS_AUDIO_EFFECT_CHORUS_MIN),
    CHORUS_MAX        (AVS_AUDIO_EFFECT_CHORUS_MAX),
    REVERB_MIN        (AVS_AUDIO_EFFECT_REVERB_MIN),
    REVERB_MED        (AVS_AUDIO_EFFECT_REVERB_MED),
    REVERB_MAX        (AVS_AUDIO_EFFECT_REVERB_MAX),
    PITCH_UP_MIN      (AVS_AUDIO_EFFECT_PITCH_UP_MIN),
    PITCH_UP_MED      (AVS_AUDIO_EFFECT_PITCH_UP_MED),
    PITCH_UP_MAX      (AVS_AUDIO_EFFECT_PITCH_UP_MAX),
    PITCH_UP_INSANE   (AVS_AUDIO_EFFECT_PITCH_UP_INSANE),
    PITCH_DOWN_MIN    (AVS_AUDIO_EFFECT_PITCH_DOWN_MIN),
    PITCH_DOWN_MED    (AVS_AUDIO_EFFECT_PITCH_DOWN_MED),
    PITCH_DOWN_MAX    (AVS_AUDIO_EFFECT_PITCH_DOWN_MAX),
    PITCH_DOWN_INSANE (AVS_AUDIO_EFFECT_PITCH_DOWN_INSANE),
    PITCH_UP_DOWN_MAX (AVS_AUDIO_EFFECT_PITCH_UP_DOWN_MAX),
    PACE_UP_MIN       (AVS_AUDIO_EFFECT_PACE_UP_MIN),
    PACE_UP_MED       (AVS_AUDIO_EFFECT_PACE_UP_MED),
    PACE_UP_MAX       (AVS_AUDIO_EFFECT_PACE_UP_MAX),
    PACE_DOWN_MIN     (AVS_AUDIO_EFFECT_PACE_DOWN_MIN),
    PACE_DOWN_MED     (AVS_AUDIO_EFFECT_PACE_DOWN_MED),
    PACE_DOWN_MAX     (AVS_AUDIO_EFFECT_PACE_DOWN_MAX),
    REVERSE           (AVS_AUDIO_EFFECT_REVERSE),
    VOCODER_MED       (AVS_AUDIO_EFFECT_VOCODER_MED),
    NONE              (AVS_AUDIO_EFFECT_NONE);

    public final int avsOrdinal;

    private AudioEffect(int avsOrdinal) {
        this.avsOrdinal = avsOrdinal;
    }
}
