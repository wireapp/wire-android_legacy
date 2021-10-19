/**
 * Wire
 * Copyright (C) 2021 Wire Swiss GmbH
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

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout, Toast}
import androidx.cardview.widget.CardView
import com.waz.avs.VideoPreview
import com.waz.service.call.Avs.VideoState
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.{SelfVideoView, UserVideoView}
import com.waz.zclient.common.controllers.ThemeControllingFrameLayout
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityPolicyChecker
import com.waz.zclient.utils.RichView
import com.wire.signals.Signal
import Threading.Implicits.Ui
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.{TabLayout, TabLayoutMediator}


class NewCallingFragment extends FragmentHelper {

  implicit val fragment = this
  private lazy val controlsFragment          = ControlsFragment.newInstance
  private lazy val callController            = inject[CallController]
  private lazy val previewCardView           = view[CardView](R.id.preview_card_view)
  private lazy val noActiveSpeakersLayout    = view[LinearLayout](R.id.no_active_speakers_layout)
  private lazy val parentLayout              = view[FrameLayout](R.id.parent_layout)
  private lazy val viewPager                 = view[ViewPager2](R.id.view_pager)
  private lazy val tabLayout                 = view[TabLayout](R.id.tab_layout)
  private lazy val allParticipantsAdapter    = new AllParticipantsAdapter()
  private lazy val activeParticipantsAdapter = new ActiveParticipantsAdapter()
  private lazy val videoPreview              = new VideoPreview(getContext) { preview =>
    preview.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    preview.setElevation(0)
  }

  private lazy val tabLayoutMediator: TabLayoutMediator =
    new TabLayoutMediator(tabLayout.get, viewPager.get, new TabLayoutMediator.TabConfigurationStrategy() {
      override def onConfigureTab(tab: TabLayout.Tab, position: Int): Unit = tab.setId(position)
    })

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_new_calling, container, false)) { v =>
      callController.theme.foreach(t => v.asInstanceOf[ThemeControllingFrameLayout].theme ! Some(t))
    }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    runSecurityPolicyChecker()
    initCallingOverlay()
    initNoActiveSpeakersLayout()
    displayFullScreenModeIndication()
    manageVideoPreview()
    initClickForRootView()
    manageFloatingSelfPreview()
    initCallingGridViewPager()

  }

  override def onBackPressed(): Boolean =
    withChildFragmentOpt(R.id.controls_layout) {
      case Some(f: FragmentHelper) if f.onBackPressed()               => true
      case Some(_) if getChildFragmentManager.popBackStackImmediate() => true
      case _ => super.onBackPressed()
    }

  private def runSecurityPolicyChecker(): Unit =
    callController.isCallIncoming.head.foreach {
      if (_) inject[SecurityPolicyChecker].run(getActivity)
    }

  private def initCallingOverlay(): Unit = getChildFragmentManager
    .beginTransaction
    .replace(R.id.controls_layout, controlsFragment, ControlsFragment.Tag)
    .commit

  private def initNoActiveSpeakersLayout(): Unit = {

    Signal.zip(
      callController.showTopSpeakers,
      callController.longTermActiveParticipants().map(_.size > 0),
      callController.controlsVisible,
      callController.isGroupCall
    ).onUi {
      case (true, false, false, true) => noActiveSpeakersLayout.foreach(_.setVisibility(View.VISIBLE))
      case _ => noActiveSpeakersLayout.foreach(_.setVisibility(View.GONE))
    }
  }

  private def displayFullScreenModeIndication(): Unit =
    callController.isGroupCall.onChanged.foreach {
      case true =>
        Toast.makeText(getContext, R.string.calling_double_tap_enter_fullscreen_message, Toast.LENGTH_LONG).show()
      case _ =>
    }

  private def manageVideoPreview(): Unit = {
    Signal.zip(callController.isSelfViewVisible, callController.videoSendState).onUi {
      case (false, VideoState.Started | VideoState.ScreenShare | VideoState.BadConnection) =>
        stopVideoPreview()
        startVideoPreview()
        attachVideoPreviewToParentLayout()
      case (false, _) =>
        stopVideoPreview()
        detachVideoPreviewFromParentLayout()
      case (true, _) =>
        detachVideoPreviewFromParentLayout()
    }
  }

  private def stopVideoPreview(): Unit = callController.setVideoPreview(getContext, null)

  private def startVideoPreview(): Unit =
    callController.setVideoPreview(getContext, Some(videoPreview))

  private def attachVideoPreviewToParentLayout(): Unit =
    parentLayout.foreach(_.addView(videoPreview))

  private def detachVideoPreviewFromParentLayout(): Unit =
    parentLayout.foreach(_.removeView(videoPreview))

  private def initClickForRootView(): Unit = getView.onClick {
    callController.controlsClick(true)
  }

  private def manageFloatingSelfPreview(): Unit = {

    Signal.zip(
      callController.selfParticipant,
      callController.showTopSpeakers,
      callController.allParticipants.map(_.size),
      callController.isCallEstablished
    ).foreach {
      case (selfParticipant, showTopSpeakers, participantsCount, true) =>

        val selfVideoView = new SelfVideoView(getContext, selfParticipant)

        previewCardView.foreach { cardView =>
          if (!showTopSpeakers && participantsCount == 2) {
            callController.isSelfViewVisible ! true
            showFloatingSelfPreview(selfVideoView, cardView)
          } else {
            callController.isSelfViewVisible ! false
            hideFloatingSelfPreview(cardView)
          }
        }
      case _ =>
    }
  }


  private def showFloatingSelfPreview(selfVideoView: UserVideoView, cardView: CardView): Unit = {
    verbose(l"Showing card preview")
    selfVideoView.setLayoutParams(
      new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
    cardView.addView(selfVideoView)
    cardView.setVisibility(View.VISIBLE)
  }

  private def hideFloatingSelfPreview(cardView: CardView): Unit = {
    verbose(l"Hiding card preview")
    cardView.removeAllViews()
    cardView.setVisibility(View.GONE)
  }

  private def initCallingGridViewPager(): Unit =
    Signal.zip(callController.showTopSpeakers, callController.allParticipants.map(_.size)
    ).onUi {
      case (false, size) =>
        viewPager.foreach(_.setAdapter(allParticipantsAdapter))
        if (size > AllParticipantsAdapter.MAX_PARTICIPANTS_PER_PAGE) {
          attachTabLayoutToViewPager()
          showPaginationDots()
        } else {
          detachTabLayoutFromViewPager()
          hidePaginationDots()
        }
      case (true, _) =>
        viewPager.foreach(_.setAdapter(activeParticipantsAdapter))
        detachTabLayoutFromViewPager()
        hidePaginationDots()
    }

  private def attachTabLayoutToViewPager(): Unit = tabLayoutMediator.attach()

  private def detachTabLayoutFromViewPager(): Unit = tabLayoutMediator.detach()

  private def showPaginationDots(): Unit = tabLayout.foreach(_.setVisible(true))

  private def hidePaginationDots(): Unit = tabLayout.foreach(_.setVisible(false))
}

object NewCallingFragment {
  val Tag: String = getClass.getSimpleName
  val MaxAllVideoPreviews = 8
  val MaxTopSpeakerVideoPreviews = 4
  val NbParticipantsOneOneCall: Int = 2
  def apply(): NewCallingFragment = new NewCallingFragment()
}



