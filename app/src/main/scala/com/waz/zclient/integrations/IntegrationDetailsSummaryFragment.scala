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
import android.view.animation.{AlphaAnimation, Animation}
import android.view.{ContextThemeWrapper, LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, TextView}
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.IntegrationsController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getDrawable, showToast}
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future
import com.waz.ZLog.ImplicitTag._

class IntegrationDetailsSummaryFragment extends Fragment with FragmentHelper {

  implicit private def ctx = getContext

  private lazy val integrationDetailsViewController = inject[IntegrationDetailsController]
  private lazy val integrationsController = inject[IntegrationsController]
  private lazy val tracking = inject[TrackingService]

  private lazy val descriptionText = returning(view[TypefaceTextView](R.id.integration_description)) { summaryText =>
    integrationDetailsViewController.currentIntegration.map(_.description).onUi(d => summaryText.foreach(_.setText(d)))
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    val parent = getParentFragment
    if (!enter && parent != null && parent.isRemoving) {
      returning(new AlphaAnimation(1, 1)) {
        _.setDuration(ViewUtils.getNextAnimationDuration(parent))
      }
    } else super.onCreateAnimation(transit, enter, nextAnim)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val localInflater =
      if (integrationDetailsViewController.addingToConversation.isEmpty && integrationDetailsViewController.removingFromConversation.isEmpty)
        inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Dark))
      else
        inflater

    localInflater.inflate(R.layout.fragment_integration_details_summary, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    implicit val ec = Threading.Implicits.Ui

    val button     = findById[FrameLayout](R.id.add_remove_service_button)
    val buttonText = findById[TextView](R.id.button_text)

    val removing = integrationDetailsViewController.removingFromConversation
    button.setBackground(getDrawable(if (removing.nonEmpty) R.drawable.red_button else R.drawable.blue_button))
    buttonText.setText(if (removing.nonEmpty) R.string.remove_service_button_text else R.string.add_service_button_text)
    button.onClick {
      removing match {
        case Some((cId, uId)) =>
          integrationsController.removeBot(cId, uId).flatMap {
            case Left(e) =>
              Future.successful(showToast(integrationsController.errorMessage(e)))
            case Right(_) =>
              getParentFragment.getFragmentManager.popBackStack()
              integrationDetailsViewController.currentIntegrationId.head.map {
                case (_, iId) => tracking.integrationRemoved(iId)
              }
          }
        case _ =>
          integrationDetailsViewController.onAddServiceClick ! {}
      }
    }

    descriptionText
  }
}
