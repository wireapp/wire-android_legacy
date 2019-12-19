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
import androidx.annotation.Nullable
import androidx.fragment.app.FragmentManager
import android.view._
import android.widget.ImageView
import com.bumptech.glide.request.RequestOptions
import com.waz.api.impl.ErrorResponse
import com.waz.model
import com.waz.model._
import com.waz.service.IntegrationsService
import com.waz.service.tracking.{IntegrationAdded, TrackingService}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.glide.transformations.IntegrationBackgroundCrop
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.paintcode.ServicePlaceholderDrawable
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.usersearch.SearchUIFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R, SpinnerController}

import scala.concurrent.Future

class IntegrationDetailsFragment extends FragmentHelper {

  import IntegrationDetailsFragment._
  import com.waz.threading.Threading.Implicits.Ui
  implicit def ctx: Context = getActivity

  private lazy val integrationsService    = inject[Signal[IntegrationsService]]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val themeController        = inject[ThemeController]
  private lazy val tracking               = inject[TrackingService]
  private lazy val convController         = inject[ConversationController]
  private lazy val spinner                = inject[SpinnerController]

  private lazy val serviceId   = getStringArg(ServiceId).map(IntegrationId)
  private lazy val providerId  = getStringArg(ProviderId).map(model.ProviderId) //only defined when adding to a conversation
  private lazy val name        = getStringArg(Name)
  private lazy val description = getStringArg(Description)
  private lazy val summary     = getStringArg(Summary)
  private lazy val assetId     = getStringArg(Asset).map(AssetId.apply)

  //will only be defined if removing from a conversation
  private lazy val fromConv    = getStringArg(RemoveFromConv).map(ConvId)
  private lazy val serviceUser = getStringArg(ServiceUser).map(UserId)

  private lazy val isBackgroundTransparent = getBooleanArg(IsTransparent)
  private lazy val addRemoveButton = view[View](R.id.add_remove_service_button)
  private lazy val addRemoveButtonText = view[TypefaceTextView](R.id.button_text)

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    val localInflater =
      if (fromConv.isEmpty)
        inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Dark))
      else
        inflater

    localInflater.inflate(R.layout.fragment_integration_details, viewContainer, false)
  }

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    returning(findById[GlyphTextView](R.id.integration_close)) { v =>
      v.onClick(close())
    }

    returning(findById[GlyphTextView](R.id.integration_back))(_.onClick(goBack()))

    returning(findById[TypefaceTextView](R.id.integration_title))(v => name.foreach(v.setText(_)))
    returning(findById[TypefaceTextView](R.id.integration_name))(v => name.foreach(v.setText(_)))
    returning(findById[ImageView](R.id.integration_picture)) { iv =>
      val placeholder = ServicePlaceholderDrawable(getDimenPx(R.dimen.wire__padding__regular))
      assetId match {
        case Some(id) =>
          WireGlide(ctx)
            .load(id)
            .apply(new RequestOptions().placeholder(placeholder).transform(new IntegrationBackgroundCrop()))
            .into(iv)
        case _ =>
          iv.setImageDrawable(placeholder)
      }
    }
    returning(findById[TypefaceTextView](R.id.integration_summary))(v => summary.foreach(v.setText(_)))
    returning(findById[TypefaceTextView](R.id.integration_description))(v => description.foreach(v.setText(_)))

    // FIXME: we use the same button for adding and removing services...
    val showAddRemoveButton = fromConv match {
      case Some(id) => convController.selfRoleInConv(id).map(_.canRemoveGroupMember).head
      case _        => userAccountsController.hasPermissionToAddService
    }

    showAddRemoveButton.foreach {
      case true =>
        addRemoveButtonText.foreach { v =>
          v.setText(if (isRemovingFromConv) R.string.remove_service_button_text else R.string.open_service_conversation_button_text)
        }
        addRemoveButton.foreach { v =>
          v.setBackground(getDrawable(if (isRemovingFromConv) R.drawable.red_button else R.drawable.blue_button))
          v.onClick {
            setLoading(true)
            fromConv.fold {
              for { sId <- serviceId; pId <- providerId } {
                integrationsService.head.flatMap(_.getOrCreateConvWithService(pId, sId)).foreach { res =>
                  setLoading(false)
                  res match {
                    case Left(err) => showToast(errorMessage(err))
                    case Right(convId) =>
                      close()
                      tracking.integrationAdded(sId, convId, IntegrationAdded.StartUi)
                      convController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST)
                  }
                }
              }
            } { convId =>
              for { uId <- serviceUser; sId <- serviceId } {
                integrationsService.head.flatMap(_.removeBotFromConversation(convId, uId)).foreach { res =>
                  setLoading(false)
                  res match {
                    case Right(_) =>
                      getParentFragment.getFragmentManager.popBackStack()
                      tracking.integrationRemoved(sId)
                    case Left(e) => Future.successful(showToast(errorMessage(e)))
                  }
                }
              }
            }
          }
        }

      case false =>
        addRemoveButton.foreach(_.setVisibility(View.GONE))
        addRemoveButtonText.foreach(_.setVisibility(View.GONE))
    }

    // TODO: AN-5980
    if (!isBackgroundTransparent)
      v.setBackgroundColor(
        if (themeController.isDarkTheme) themeController.getThemeDependentOptionsTheme.getOverlayColor
        else Color.WHITE
      )
  }

  private def setLoading(loading: Boolean): Unit = {
    addRemoveButton.foreach { v =>
      v.setEnabled(!loading)
      if (loading) spinner.showSpinner(forcedIsDarkTheme = Option(true)) else spinner.hideSpinner()
    }
  }

  private def errorMessage(e: ErrorResponse): String =
    getString((e.code, e.label) match {
      case (403, "too-many-members") => R.string.conversation_errors_full
      //      case (419, "too-many-bots")    => R.string.integrations_errors_add_service //TODO ???
      case (_, _)                    => R.string.integrations_errors_service_unavailable
    })

  override def onBackPressed(): Boolean = {
    super.onBackPressed()
    goBack()
  }

  def goBack(): Boolean = {
    getFragmentManager.popBackStack()
    true
  }

  //TODO - move navigation logic out of this fragment
  def close(): Boolean =
  if (isRemovingFromConv) {
    inject[ParticipantsController].onLeaveParticipants ! true
    true
  } else {
    Option(getFragmentManager).foreach { fm =>
      fm.popBackStack(SearchUIFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      inject[IPickUserController].hidePickUser()
      inject[INavigationController].setLeftPage(Page.CONVERSATION_LIST, IntegrationDetailsFragment.Tag)
    }
    true
  }

  private def isRemovingFromConv: Boolean =
    fromConv.isDefined
}

object IntegrationDetailsFragment {

  val Tag = classOf[IntegrationDetailsFragment].getName

  val Name            = "ARG_SERVICE_NAME"
  val Description     = "ARG_SERVICE_DESCRIPTION"
  val Summary         = "ARG_SERVICE_SUMMARY"
  val Asset           = "ARG_SERVICE_ASSET"
  val RemoveFromConv  = "ARG_REMOVE_FROM_CONV"
  val ServiceUser     = "ARG_SERVICE_USER"
  val ServiceId       = "ARG_SERVICE_ID"
  val ProviderId      = "ARG_PROVIDER_ID"
  val IsTransparent   = "ARG_IS_TRANSPARENT"

  def newAddingInstance(service: IntegrationData): IntegrationDetailsFragment =
    returning(new IntegrationDetailsFragment) {
      _.setArguments(returning(new Bundle) { b =>
        b.putString(Name, service.name)
        b.putString(Description, service.description)
        b.putString(Summary, service.summary)
        b.putString(ServiceId, service.id.str)
        b.putString(ProviderId, service.provider.str)
        service.asset.map(_.str).foreach(b.putString(Asset, _))
        b.putBoolean(IsTransparent, true)
      })
    }

  def newRemovingInstance(service: IntegrationData, convId: ConvId, userId: UserId) =
    returning(new IntegrationDetailsFragment) {
      _.setArguments(returning(new Bundle) { b =>
        b.putString(Name, service.name)
        b.putString(Description, service.description)
        b.putString(Summary, service.summary)
        b.putString(ServiceId, service.id.str)
        service.asset.map(_.str).foreach(b.putString(Asset, _))
        b.putString(RemoveFromConv, convId.str)
        b.putString(ServiceUser, userId.str)
        b.putBoolean(IsTransparent, false)
      })
    }
}
