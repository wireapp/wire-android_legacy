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

import android.content.{Context, DialogInterface, Intent}
import android.graphics.Color
import android.os.Bundle
import android.view._
import android.widget.{Button, LinearLayout}
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment
import com.waz.service.call.Avs.VideoState
import com.waz.threading.Threading._
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.{CallingHeader, CallingMiddleLayout, ControlsView}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, MainActivity, R}
import com.wire.signals.Subscription

class ControlsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity

  private lazy val controller = inject[CallController]

  private lazy val callingHeader = view[CallingHeader](R.id.calling_header)

  private lazy val callingMiddle = view[CallingMiddleLayout](R.id.calling_middle)
  private lazy val callingControls = view[ControlsView](R.id.controls_grid)
  private lazy val speakersLayoutContainer = view[LinearLayout](R.id.all_speakers_layout)
  private lazy val speakersButton = view[Button](R.id.speakers_button)
  private lazy val allButton = view[Button](R.id.all_button)

  private var subs = Set[Subscription]()

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    controller.allVideoReceiveStates.map(_.values.exists(Set(VideoState.Started, VideoState.ScreenShare).contains)).onUi {
      case true => getView.setBackgroundColor(getColor(R.color.calling_video_overlay))
      case false => getView.setBackgroundColor(Color.TRANSPARENT)
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_calling_controls, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    speakersButton.foreach { button =>
      button.onClick {
        updateToggleSelection(true)
      }
    }
    allButton.foreach { button =>
      button.setSelected(true)
      button.onClick {
        updateToggleSelection(false)
      }
    }

    controller.showTopSpeakers.onUi { shouldShowActiveSpeakers =>
      updateToggleSelection(shouldShowActiveSpeakers)
    }

    callingControls
    callingMiddle // initializing it later than the header and controls to reduce the number of height recalculations

    controller.onCallDegraded.onUi { _ =>
      showConversationDegragatedDialog()
    }

    callingHeader.foreach {
      _.closeButton.onClick {
        controller.callControlsVisible ! false
        getContext.startActivity(new Intent(getContext, classOf[MainActivity]))
      }
    }


    //TODO : The calling squad decided to disable all/speaker toggle to perform some optimizations
    // in terms of user experience before releasing it to public

     /*if (BuildConfig.ACTIVE_SPEAKERS) {
       Signal.zip(
         controller.isCallEstablished,
         controller.isGroupCall,
         controller.isVideoCall,
         controller.isFullScreenEnabled
       ).onUi {
         case (true, true, true, false) => speakersLayoutContainer.foreach(_.setVisibility(View.VISIBLE))
         case _                         => speakersLayoutContainer.foreach(_.setVisibility(View.INVISIBLE))
       }
     }
     else*/
    speakersLayoutContainer.foreach(_.setVisibility(View.INVISIBLE))
  }

  override def onStart(): Unit = {
    super.onStart()

    controller.controlsClick(true) //reset timer after coming back from participants

    subs += controller.controlsVisible.onUi {
      case true => getView.fadeIn()
      case false => getView.fadeOut()
    }

    callingControls.foreach(controls =>
      subs += controls.onButtonClick.onUi { _ =>
        verbose(l"button clicked")
        controller.controlsClick(true)
      }
    )

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
    controller.showTopSpeakers ! false
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onStop()
  }

  private def showConversationDegragatedDialog(): Unit = ViewUtils.showAlertDialog(
    getContext,
    R.string.call_degraded_title,
    R.string.conversation_degraded_message,
    android.R.string.ok,
    new DialogInterface.OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = getActivity.finish()
    },
    false)

  private def updateToggleSelection(shouldShowActiveSpeakers: Boolean): Unit = {
    speakersButton.foreach(_.setSelected(shouldShowActiveSpeakers))
    allButton.foreach(_.setSelected(!shouldShowActiveSpeakers))
    controller.showTopSpeakers ! shouldShowActiveSpeakers
  }
}

object ControlsFragment {
  val VideoViewTag = "VIDEO_VIEW_TAG"
  val Tag = classOf[ControlsFragment].getName

  def newInstance: Fragment = new ControlsFragment
}
