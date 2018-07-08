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
package com.waz.zclient.integrations

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.{ContextThemeWrapper, LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.events.Subscription
import com.waz.utils.returning
import com.waz.zclient.common.controllers.IntegrationsController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getDrawable, showToast}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future

class IntegrationDetailsSummaryFragment extends Fragment with FragmentHelper {

  implicit private def ctx = getContext
  import Threading.Implicits.Ui

  private lazy val integrationDetailsViewController = inject[IntegrationDetailsController]
  private lazy val integrationsController           = inject[IntegrationsController]
  private lazy val participantsController           = inject[ParticipantsController]
  private lazy val tracking                         = inject[TrackingService]

  private var subs = Set.empty[Subscription]

  private lazy val descriptionText = returning(view[TypefaceTextView](R.id.integration_description)) { summaryText =>
    integrationDetailsViewController.currentIntegration.map(_.description).onUi(d => summaryText.foreach(_.setText(d)))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View ={
    val localInflater =
      if (integrationDetailsViewController.addingToConversation.isEmpty && integrationDetailsViewController.removingFromConversation.isEmpty)
        inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Dark))
      else
        inflater

    localInflater.inflate(R.layout.fragment_integration_details_summary, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val button     = findById[FrameLayout](R.id.add_remove_service_button)
    val buttonText = findById[TextView](R.id.button_text)

    integrationDetailsViewController.removingFromConversation match {
      case None =>
        button.setBackground(getDrawable(R.drawable.blue_button))
        buttonText.setText(R.string.add_service_button_text)
        button.onClick {
          integrationDetailsViewController.onAddServiceClick ! {}
        }
      case Some((cId, uId)) =>
        subs += participantsController.isCurrentUserGuest {
          case true =>
            button.setVisible(false)
          case false =>
            button.setBackground(getDrawable(R.drawable.red_button))
            buttonText.setText(R.string.remove_service_button_text)
            button.onClick {
              integrationsController.removeBot(cId, uId).flatMap {
                case Left(e) =>
                  Future.successful(showToast(integrationsController.errorMessage(e)))
                case Right(_) =>
                  getParentFragment.getFragmentManager.popBackStack()
                  integrationDetailsViewController.currentIntegrationId.head.map {
                    case (_, iId) => tracking.integrationRemoved(iId)
                  }
              }
            }
        }
    }

    descriptionText
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty

    super.onDestroyView()
  }
}
