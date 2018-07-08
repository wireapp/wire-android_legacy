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
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.RelativeLayout
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.model.ConversationData
import com.waz.service.tracking.{IntegrationAdded, TrackingService}
import com.waz.service.{SearchKey, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.IntegrationsController
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.views.NormalConversationListRow
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.integrations.IntegrationConversationsAdapter._
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.zclient.utils.{RichView, UiStorage}
import com.waz.zclient.{FragmentHelper, R, ViewHelper}

import scala.concurrent.Future

class IntegrationConversationSearchFragment extends Fragment with FragmentHelper {
  import Threading.Implicits.Ui

  implicit private def ctx = getContext

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val integrationsController = inject[IntegrationsController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val integrationDetailsController = inject[IntegrationDetailsController]
  private lazy val tracking = inject[TrackingService]
  implicit private lazy val uiStorage = inject[UiStorage]

  private lazy val searchBox = view[SearchEditText](R.id.search_box)
  private lazy val listView = view[RecyclerView](R.id.conv_recycler_view)
  private lazy val convsAdapter = returning(new IntegrationConversationsAdapter(getContext)) { adapter =>
    adapter.conversationClicked.onUi { conv =>
      integrationDetailsController.currentIntegrationId.head.flatMap {
        case (providerId, integrationId) =>
          integrationsController.addBot(conv.id, providerId, integrationId).flatMap {
            case Left(e) =>
              showToast(integrationsController.errorMessage(e))
              Future.successful(())
            case Right(_) =>
              close()
              tracking.integrationAdded(integrationId, conv.id, IntegrationAdded.StartUi)
              conversationController.selectConv(conv.id, ConversationChangeRequester.CONVERSATION_LIST)
          }
      }
    }
    adapter.createConvClicked.onUi { _ =>
      integrationDetailsController.currentIntegrationId.head.flatMap {
        case (providerId, integrationId) =>
          integrationsController.createConvWithBot(providerId, integrationId).flatMap { convId =>
            close()
            tracking.integrationAdded(integrationId, convId, IntegrationAdded.StartUi)
            conversationController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST)
          }
      }
    }
  }

  private lazy val convsData = for {
    zms    <- zms
    filter <- integrationDetailsController.searchFilter
    convs  <- Signal.future(zms.convsUi.findGroupConversations(SearchKey(filter), Int.MaxValue, handleOnly = false))
    groups <- Signal.future(Future.sequence(convs.distinct.map(conv => conversationController.isGroup(conv.id).map(if (_) Some(conv) else None))))
  } yield groups.flatten.filter(_.team == zms.teamId)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_integrations_pick_conv, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    listView.foreach { lv =>
      lv.setLayoutManager(new LinearLayoutManager(getContext))
      lv.setAdapter(convsAdapter)
    }

    searchBox.foreach(_.setCallback(new PickerSpannableEditText.Callback {
      override def afterTextChanged(s: String): Unit = integrationDetailsController.searchFilter ! s
      override def onRemovedTokenSpan(element: PickableElement): Unit = {}
    }))
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    integrationDetailsController.searchFilter ! ""
    convsData.onUi(convsAdapter.setData)
  }

  def close(): Unit = inject[IPickUserController].hidePickUser()
}

object IntegrationConversationSearchFragment {
  val Tag: String = ZLog.ImplicitTag.implicitLogTag
}

case class IntegrationConversationsAdapter(context: Context) extends RecyclerView.Adapter[IntegrationViewHolder] {

  val conversationClicked: SourceStream[ConversationData] = EventStream[ConversationData]()
  val createConvClicked: SourceStream[Unit] = EventStream[Unit]()

  private var data = Seq.empty[ConversationData]
  setHasStableIds(true)

  def setData(data: Seq[ConversationData]): Unit = {
    this.data = data
    notifyDataSetChanged()
  }

  private def getItem(position: Int) = data.lift(position - 1)

  override def getItemCount: Int = data.size + 1

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): IntegrationViewHolder = {
    viewType match {
      case CreateButtonViewType =>
        val view = ViewHelper.inflate[RelativeLayout](R.layout.integration_create_conv, parent, addToParent = false)
        view.onClick(createConvClicked ! (()))
        IntegrationCreateConvViewHolder(view)
      case ConversationViewType =>
        val view = ViewHelper.inflate[NormalConversationListRow](R.layout.normal_conv_list_item, parent, addToParent = false)
        IntegrationConversationViewHolder(view, conversationClicked ! _ )
    }
  }

  override def onBindViewHolder(holder: IntegrationViewHolder, position: Int): Unit = holder.bind(getItem(position))

  override def getItemViewType(position: Int): Int =
    if (position == 0) CreateButtonViewType else ConversationViewType

  override def getItemId(position: Int): Long = getItem(position).map(_.id.str.hashCode.toLong).getOrElse(0L)
}

object IntegrationConversationsAdapter {

  val CreateButtonViewType = 0
  val ConversationViewType = 1

  trait IntegrationViewHolder extends RecyclerView.ViewHolder {
    def bind(conversationData: Option[ConversationData]): Unit = {}
  }

  case class IntegrationCreateConvViewHolder(v: View) extends RecyclerView.ViewHolder(v) with IntegrationViewHolder

  case class IntegrationConversationViewHolder(v: NormalConversationListRow, onClick: ConversationData => Unit) extends RecyclerView.ViewHolder(v) with IntegrationViewHolder {
    override def bind(conversationData: Option[ConversationData]): Unit =
      conversationData.foreach { conv =>
        v.onClick(onClick(conv))
        v.setConversation(conv)
      }
  }
}
