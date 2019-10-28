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
package com.waz.service.call

import com.sun.jna.{Callback, Native, Pointer}
import com.waz.CLibrary.Members
import com.waz.utils.jna.{Size_t, Uint32_t}

import scala.concurrent.Promise

object Calling {

  // The wrapped `wcall` instance.
  type Handle = Uint32_t

  // A magic number used to initialize AVS (required for all mobile platforms).
  val WCALL_ENV_DEFAULT: Int = 0

  private val available = Promise[Unit]()
  val avsAvailable = available.future

  try {
    Native.register(Calling.getClass, "avs")
    available.success({})
  }
  catch {
    case e: Throwable => available.failure(e)
  }

  @native def wcall_library_version(): String

  @native def wcall_init(env: Int): Int

  @native def wcall_close(): Unit

  @native def wcall_create(userid:    String,
                           clientid:  String,
                           readyh:    ReadyHandler,
                           sendh:     SendHandler,
                           incomingh: IncomingCallHandler,
                           missedh:   MissedCallHandler,
                           answeredh: AnsweredCallHandler,
                           estabh:    EstablishedCallHandler,
                           closeh:    CloseCallHandler,
                           metricsh:  MetricsHandler,
                           confreqh:  CallConfigRequestHandler,
                           acbrh:     CbrStateChangeHandler,
                           vstateh:   VideoReceiveStateHandler,
                           arg:       Pointer): Handle

  @native def wcall_destroy(arg: Handle): Unit

  @native def wcall_start(inst: Handle, convid: String, call_type: Int, conv_type: Int, audio_cbr: Int): Int

  @native def wcall_answer(inst: Handle, convid: String, call_type: Int, audio_cbr: Int): Unit

  @native def wcall_resp(inst: Handle, status: Int, reason: String, arg: Pointer): Int

  @native def wcall_config_update(inst: Handle, err: Int, json_str: String): Unit

  @native def wcall_recv_msg(inst: Handle, msg: Array[Byte], len: Int, curr_time: Uint32_t, msg_time: Uint32_t, convId: String, userId: String, clientId: String): Int

  @native def wcall_end(inst: Handle, convId: String): Unit

  @native def wcall_reject(inst: Handle, convId: String): Unit

  @native def wcall_set_video_send_state(inst: Handle, convid: String, state: Int): Unit

  @native def wcall_network_changed(inst: Handle): Unit

  @native def wcall_set_participant_changed_handler(inst: Handle, wcall_participant_changed_h: ParticipantChangedHandler, arg: Pointer): Unit

  @native def wcall_get_members(inst: Handle, convid: String): Members

  @native def wcall_free_members(pointer: Pointer): Unit

  @native def wcall_set_state_handler(inst: Handle, wcall_state_change_h: CallStateChangeHandler): Unit

  @native def wcall_set_log_handler(wcall_log_h: LogHandler, arg: Pointer): Unit

  @native def wcall_get_mute(inst: Handle): Int

  @native def wcall_set_mute(inst: Handle, muted: Int): Unit

  @native def wcall_set_proxy(host: String, port: Int): Int

  /* This will be called when the calling system is ready for calling.
     * The version parameter specifies the config obtained version to use
     * for calling.
     */
  trait ReadyHandler extends Callback {
    def onReady(version: Int, arg: Pointer): Unit
  }

  /* Send calling message otr data */
  trait SendHandler extends Callback {
    def onSend(ctx: Pointer, convId: String, userid_self: String, clientid_self: String, userid_dest: String, clientid_dest: String, data: Pointer, len: Size_t, transient: Boolean, arg: Pointer): Int
  }

  /* Incoming call */
  trait IncomingCallHandler extends Callback {
    def onIncomingCall(convid: String, msg_time: Uint32_t, userid: String, video_call: Boolean, should_ring: Boolean, arg: Pointer): Unit
  }

  /* Missed call */
  trait MissedCallHandler extends Callback {
    def onMissedCall(convId: String, msg_time: Uint32_t, userId: String, video_call: Boolean, arg: Pointer): Unit
  }

  trait AnsweredCallHandler extends Callback {
    /**
      * Note, only relevant for one-to-one calls
      */
    def onAnsweredCall(convId: String, arg: Pointer): Unit
  }

  /* Call established (with media) */
  trait EstablishedCallHandler extends Callback {
    def onEstablishedCall(convId: String, userId: String, arg: Pointer): Unit
  }

  trait CloseCallHandler extends Callback {
    def onClosedCall(reason: Int, convid: String, msg_time: Uint32_t, userid: String, arg: Pointer): Unit
  }

  trait CbrStateChangeHandler extends Callback {
    def onBitRateStateChanged(userId: String, enabled: Boolean, arg: Pointer): Unit
  }

  trait CallStateChangeHandler extends Callback {
    def onCallStateChanged(convId: String, state: Int, arg: Pointer): Unit
  }

  trait VideoReceiveStateHandler extends Callback {
    def onVideoReceiveStateChanged(convId: String, userId: String, clientId: String, state: Int, arg: Pointer): Unit
  }

  trait ParticipantChangedHandler extends Callback {

    // Example of `data`
    //  {
    //      "convid": "df371578-65cf-4f07-9f49-c72a49877ae7",
    //      "members": [
    //          {
    //              "userid": "3f49da1d-0d52-4696-9ef3-0dd181383e8a",
    //              "clientid": "24cc758f602fb1f4",
    //              "aestab": 1,
    //              "vrecv": 0
    //          }
    //      ]
    //}
    def onParticipantChanged(convId: String, data: String, arg: Pointer): Unit
  }

  trait MetricsHandler extends Callback {
    def onMetricsReady(convId: String, metricsJson: String, arg: Pointer): Unit
  }

  trait LogHandler extends Callback {
    def onLog(level: Int, msg: String, arg: Pointer): Unit
  }

  trait CallConfigRequestHandler extends Callback {
    def onConfigRequest(inst: Handle, arg: Pointer): Int
  }

}
