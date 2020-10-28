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

import org.threeten.bp.Instant;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

public enum EphemeralExpiration {

    NONE(0), FIVE_SECONDS(5 * 1000), FIFTEEN_SECONDS(15 * 1000), THIRTY_SECONDS(30 * 1000), ONE_MINUTE(60 * 1000), FIVE_MINUTES(5 * 60 * 1000), ONE_DAY(24 * 60 * 60 * 1000);

    public long milliseconds;

    public FiniteDuration duration() {
        return new FiniteDuration(milliseconds, TimeUnit.MILLISECONDS);
    }

    public Option<Instant> expiryFromNow() {
        return (milliseconds == 0) ? Option.<Instant>empty() : Option.apply(Instant.now().plusMillis(milliseconds));
    }

    EphemeralExpiration(long millis) {
        this.milliseconds = millis;
    }

    /**
     * Find expiration constant with closes time value.
     * Expiration timeout is sent as a number of milliseconds,
     * it's possible that some clients will send some unexpected values.
     * We don't want to handle arbitrary timeouts (design decision),
     * so we always use a closest constant.
     *
     * Warning: don't round to zero, this would mean: `not ephemeral`.
     */
    public static EphemeralExpiration getForMillis(long millis) {
        for (EphemeralExpiration exp : values()) {
            if (exp.milliseconds >= millis) {
                return exp;
            }
        }
        return FIVE_MINUTES;
    }
}
