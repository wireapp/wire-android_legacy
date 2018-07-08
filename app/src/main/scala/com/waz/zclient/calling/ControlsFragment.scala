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
package com.waz.zclient.calling

import android.app.AlertDialog
import android.content.{Context, DialogInterface, Intent}
import android.graphics.Color
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.Fragment
import android.view._
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.CallState
import com.waz.utils.events.Subscription
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.{CallingHeader, CallingMiddleLayout, ControlsView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, MainActivity, R}

class ControlsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity

  private lazy val controller = inject[CallController]

  private lazy val callingHeader = view[CallingHeader](R.id.calling_header)

  private lazy val callingMiddle   = view[CallingMiddleLayout](R.id.calling_middle)
  private lazy val callingControls = view[ControlsView](R.id.controls_grid)

  private var subs = Set[Subscription]()

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    controller.allVideoReceiveStates.map(_.values.exists(_ == VideoState.Started)).onUi {
      case true => getView.setBackgroundColor(getColor(R.color.calling_video_overlay))
      case false => getView.setBackgroundColor(Color.TRANSPARENT)
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_calling_controls, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    callingControls
    callingMiddle // initializing it later than the header and controls to reduce the number of height recalculations

    controller.isCallActive.onUi {
      case false =>
        verbose("call no longer exists, finishing activity")
        getActivity.finish()
      case _ =>
    }

    (for {
      state                 <- controller.callState
      incoming              =  state == CallState.SelfJoining || state == CallState.OtherCalling
      Some(degradationText) <- controller.degradationWarningText
    } yield (incoming, degradationText)).onUi { case (incoming, degradationText) =>
      new AlertDialog.Builder(getActivity)
        .setTitle(R.string.calling_degraded_title)
        .setMessage(degradationText)
        .setCancelable(false)
        .setPositiveButton(
          if (incoming) android.R.string.ok else R.string.calling_ongoing_call_start_anyway,
          new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = controller.continueDegradedCall()
          }
        )
        .setNegativeButton(
          android.R.string.cancel,
          new DialogInterface.OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = controller.leaveCall()
          }
        )
        .create()
        .show()
    }

    callingHeader.foreach {
      _.closeButton.onClick {
        controller.callControlsVisible ! false
        getContext.startActivity(new Intent(getContext, classOf[MainActivity]))
      }
    }
  }

  override def onStart(): Unit = {
    super.onStart()

    controller.controlsClick(true) //reset timer after coming back from participants

    subs += controller.controlsVisible.onUi {
      case true  => getView.fadeIn()
      case false => getView.fadeOut()
    }

    callingControls.foreach(controls =>
      subs += controls.onButtonClick.onUi { _ =>
        verbose("button clicked")
        controller.controlsClick(true)
      }
    )

    //we need to listen to clicks on the outer layout, so that we can set this.getView to gone.
    getView.getParent.asInstanceOf[View].onClick {
      Option(getView).map(!_.isVisible).foreach(controller.controlsClick)
    }

    callingMiddle.foreach(vh => subs += vh.onShowAllClicked.onUi { _ =>
      controller.callControlsVisible ! false
      getFragmentManager.beginTransaction
        .setCustomAnimations(
          R.anim.fragment_animation_second_page_slide_in_from_right_no_alpha,
          R.anim.fragment_animation_second_page_slide_out_to_left_no_alpha,
          R.anim.fragment_animation_second_page_slide_in_from_left_no_alpha,
          R.anim.fragment_animation_second_page_slide_out_to_right_no_alpha)
        .replace(R.id.controls_layout, CallParticipantsFragment(), CallParticipantsFragment.Tag)
        .addToBackStack(CallParticipantsFragment.Tag)
        .commit
    })
  }


  override def onResume() = {
    super.onResume()
    //WARNING! Samsung devices call onPause/onStop on the activity (and thus fragment) when the proximity sensor kicks in.
    //We then can't call callControlsVisible ! false in either of those methods, or else the proximity sensor is disabled again.
    //For this reason, we have to set it to false at all possible exists out of the fragment
    controller.callControlsVisible ! true
  }

  override def onBackPressed() = {
    controller.callControlsVisible ! false
    super.onBackPressed()
  }

  override def onStop(): Unit = {
    getView.getParent.asInstanceOf[View].setOnClickListener(null)
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onStop()
  }

}

object ControlsFragment {
  val VideoViewTag = "VIDEO_VIEW_TAG"
  val Tag = classOf[ControlsFragment].getName

  def newInstance: Fragment = new ControlsFragment
}
