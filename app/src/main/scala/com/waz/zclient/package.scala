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
package com.waz

import android.app.{NotificationManager, Activity, ActivityManager, Application}
import android.content.{ContentResolver, Context, ContextWrapper}
import android.media.AudioManager
import android.os.{Vibrator, PowerManager}
import android.support.v4.app.{FragmentActivity, FragmentManager}
import com.waz.utils.events.EventContext

package object zclient {

  def AppModule = new Module {
    val ctx = WireApplication.APP_INSTANCE
    bind [Context]          to ctx
    bind [WireContext]      to ctx
    bind [Application]      to ctx
    bind [EventContext]     to ctx.eventContext
    bind [ContentResolver]  to ctx.getContentResolver

    //Android services
    bind [ActivityManager]      to ctx.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    bind [PowerManager]         to ctx.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    bind [Vibrator]             to ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
    bind [AudioManager]         to ctx.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    bind [NotificationManager]  to ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
  }

  def ContextModule(ctx: WireContext) = new Module {
    bind [Context]      to ctx
    bind [WireContext]  to ctx
    bind [EventContext] to ctx.eventContext
    bind [Activity]     to {
      def getActivity(ctx: Context): Activity = ctx match {
        case a: Activity => a
        case w: ContextWrapper => getActivity(w.getBaseContext)
      }
      getActivity(ctx)
    }
    bind [FragmentManager] to inject[Activity].asInstanceOf[FragmentActivity].getSupportFragmentManager
  }
}
