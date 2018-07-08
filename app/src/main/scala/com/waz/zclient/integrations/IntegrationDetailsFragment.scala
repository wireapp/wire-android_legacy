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

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.{Fragment, FragmentManager, FragmentPagerAdapter}
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.view._
import android.widget.ImageView
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.warn
import com.waz.model.{IntegrationId, ProviderId}
import com.waz.service.tracking.{IntegrationAdded, TrackingService}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{IntegrationsController, ThemeController}
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, NoImage, WireImage}
import com.waz.zclient.common.views.IntegrationAssetDrawable
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.integrations.IntegrationDetailsViewPager._
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.paintcode.ServicePlaceholderDrawable
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.usersearch.SearchUIFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R, ViewHelper}

class IntegrationDetailsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity

  private lazy val integrationDetailsController = inject[IntegrationDetailsController]
  private lazy val integrationsController       = inject[IntegrationsController]
  private lazy val themeController              = inject[ThemeController]
  private lazy val tracking                     = inject[TrackingService]
  private lazy val createConvControlelr         = inject[CreateConversationController]

  private lazy val providerId              = ProviderId(getArguments.getString(IntegrationDetailsFragment.ProviderId))
  private lazy val integrationId           = IntegrationId(getArguments.getString(IntegrationDetailsFragment.IntegrationId))
  private lazy val isBackgroundTransparent = getArguments.getBoolean(IntegrationDetailsFragment.IsTransparent)

  private lazy val pictureAssetId               = integrationDetailsController.currentIntegration.map(_.asset)
  private lazy val picture: Signal[ImageSource] = pictureAssetId.map(_.map(WireImage).getOrElse(NoImage()))
  private lazy val name                         = integrationDetailsController.currentIntegration.map(_.name)

  private lazy val drawable = new IntegrationAssetDrawable(
    src          = picture,
    scaleType    = ScaleType.CenterInside,
    request      = RequestBuilder.Regular,
    background   = Some(ServicePlaceholderDrawable(getDimenPx(R.dimen.wire__padding__regular))),
    animate      = true
  )

  private lazy val viewPager = view[IntegrationDetailsViewPager](R.id.view_pager)

  private lazy val title = returning(view[TypefaceTextView](R.id.integration_title)) { title =>
    name.map(_.toUpperCase).onUi { name => title.foreach(_.setText(name)) }
  }

  private lazy val nameView = returning(view[TypefaceTextView](R.id.integration_name)){ nv =>
    name.onUi { name => nv.foreach(_.setText(name)) }
  }

  private lazy val summaryView = returning(view[TypefaceTextView](R.id.integration_summary)){ sv =>
    integrationDetailsController.currentIntegration.map(_.summary).onUi { summary => sv.foreach(_.setText(summary)) }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    integrationDetailsController.currentIntegrationId ! (providerId, integrationId)
    integrationDetailsController.onAddServiceClick { _ =>

      integrationDetailsController.addingToConversation.fold {
        viewPager.foreach(_.goToConversations)
      } { convId =>
        integrationsController.addBot(convId, providerId, integrationId).map {
          case Left(e) =>
            warn(s"Failed to add bot to conversation: $e")
            showToast(integrationsController.errorMessage(e))
            close()
          case Right(_) =>
            tracking.integrationAdded(integrationId, convId, IntegrationAdded.ConversationDetails)
            close()
        } (Threading.Ui)
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    val localInflater =
      if (integrationDetailsController.addingToConversation.isEmpty && integrationDetailsController.removingFromConversation.isEmpty)
        inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Dark))
      else
        inflater

    localInflater.inflate(R.layout.fragment_integration_details, viewContainer, false)
  }

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    returning(findById[GlyphTextView](R.id.integration_close)) { closeButton =>
      if (integrationDetailsController.removingFromConversation.isDefined || integrationDetailsController.addingToConversation.isDefined)
        closeButton.setVisibility(View.GONE)
      else
        closeButton.onClick(close())
    }

    returning(findById[GlyphTextView](R.id.integration_back))(_.onClick(goBack()))
    returning(findById[ImageView](R.id.integration_picture))(_.setImageDrawable(drawable))

    // TODO: AN-5980
    if (!isBackgroundTransparent)
      v.setBackgroundColor(
        if (themeController.isDarkTheme) themeController.getThemeDependentOptionsTheme.getOverlayColor
        else Color.WHITE
      )

    title
    summaryView
    nameView

    viewPager.foreach(_.setAdapter(IntegrationDetailsAdapter(getChildFragmentManager, providerId, integrationId)))
  }

  override def onBackPressed(): Boolean = {
    super.onBackPressed()
    goBack()
  }

  def goBack(): Boolean = {
    viewPager.foreach(_.getCurrentItem match {
      case IntegrationDetailsViewPager.ConvListPage =>
        viewPager.foreach(_.goToDetails)
      case _ =>
        getFragmentManager.popBackStack()
        if (integrationDetailsController.addingToConversation.nonEmpty) {
          inject[INavigationController].setRightPage(Page.PICK_USER, IntegrationDetailsFragment.Tag)
        } else if (integrationDetailsController.removingFromConversation.isEmpty) {
          inject[INavigationController].setLeftPage(Page.PICK_USER, IntegrationDetailsFragment.Tag)
        }
    })
    true
  }

  def close(): Boolean = {
    getFragmentManager.popBackStack(SearchUIFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    if (integrationDetailsController.addingToConversation.nonEmpty) {
      createConvControlelr.onShowCreateConversation ! false
      inject[INavigationController].setRightPage(Page.PARTICIPANT, IntegrationDetailsFragment.Tag)
    } else {
      inject[IPickUserController].hidePickUser()
      inject[INavigationController].setLeftPage(Page.CONVERSATION_LIST, IntegrationDetailsFragment.Tag)
    }
    true
  }
}

object IntegrationDetailsFragment {

  val Tag = classOf[IntegrationDetailsFragment].getName

  val IntegrationId = "ARG_INTEGRATION_ID"
  val ProviderId    = "ARG_PROVIDER_ID"
  val IsTransparent = "ARG_IS_TRANSPARENT"

  def newInstance(providerId: ProviderId, integrationId: IntegrationId, isTransparent: Boolean = true) = returning(new IntegrationDetailsFragment) {
    _.setArguments(returning(new Bundle){ b =>
      b.putString(ProviderId, providerId.str)
      b.putString(IntegrationId, integrationId.str)
      b.putBoolean(IsTransparent, isTransparent)
    })
  }
}

object IntegrationDetailsViewPager {
  val DetailsPage = 0
  val ConvListPage = 1
}

case class IntegrationDetailsViewPager (context: Context, attrs: AttributeSet) extends ViewPager(context, attrs) with ViewHelper {
  def this(context: Context) = this(context, null)

  def goToDetails       = setCurrentItem(DetailsPage)
  def goToConversations = setCurrentItem(ConvListPage)

  override def onInterceptTouchEvent(ev: MotionEvent): Boolean = false
  override def onTouchEvent(ev: MotionEvent): Boolean = false
}

case class IntegrationDetailsAdapter(fm: FragmentManager, providerId: ProviderId, integrationId: IntegrationId) extends FragmentPagerAdapter(fm) {

  override def getItem(position: Int): Fragment = {
    position match {
      case DetailsPage =>
        new IntegrationDetailsSummaryFragment()
      case ConvListPage =>
        new IntegrationConversationSearchFragment()
    }
  }

  override def getCount: Int = 2
}
