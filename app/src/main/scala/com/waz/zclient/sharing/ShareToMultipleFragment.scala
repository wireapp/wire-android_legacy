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
package com.waz.zclient.sharing

import android.content.Context
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.text.format.Formatter
import android.view.View.OnClickListener
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.EphemeralExpiration
import com.waz.api.impl.ContentUriAssetForUpload
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{MessageContent => _, _}
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.SharingController.{FileContent, ImageContent, TextContent}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers.{AssetsController, SharingController}
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.DataImage
import com.waz.zclient.common.views._
import com.waz.zclient.messages.{MessagesController, UsersController}
import com.waz.zclient.pages.extendedcursor.ephemeral.EphemeralLayout
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.{ColorUtils, KeyboardUtils}
import com.waz.zclient.ui.views.CursorIconButton
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}
import com.waz.zclient.utils.ContextUtils.{getDimenPx, showToast}
import com.waz.zclient.utils.{RichView, ViewUtils}

import scala.util.Success


class ShareToMultipleFragment extends FragmentHelper with OnBackPressedListener {

  implicit def cxt = getContext

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val accounts = inject[AccountsService]
  lazy val assetsController = inject[AssetsController]
  lazy val messagesController = inject[MessagesController]
  lazy val sharingController = inject[SharingController]
  lazy val usersController = inject[UsersController]
  lazy val accentColor = inject[AccentColorController].accentColor.map(_.getColor)

  lazy val filterText = Signal[String]("")

  lazy val onClickEvent = EventStream[Unit]()

  lazy val adapter = returning(new ShareToMultipleAdapter(getContext, filterText)) { a =>
    onClickEvent { _ =>
      a.selectedConversations.head.map { convs =>
        sharingController.onContentShared(getActivity, convs)
        showToast(R.string.multi_share_toast_sending, long = false)
        getActivity.finish()
      } (Threading.Ui)
    }
  }

  lazy val convList = view[RecyclerView](R.id.lv__conversation_list)
  lazy val accountTabs = view[AccountTabsView](R.id.account_tabs)
  lazy val bottomContainer = view[AnimatedBottomContainer](R.id.ephemeral_container)
  lazy val ephemeralToggle = view[EphemeralCursorButton](R.id.ephemeral_toggle)

  lazy val sendButton = returning(view[CursorIconButton](R.id.cib__send_button)) { vh =>
    (for {
      convs <- adapter.selectedConversations
      color <- accentColor
    } yield if (convs.nonEmpty) color else ColorUtils.injectAlpha(0.4f, color)).onUi(c => vh.foreach(_.setSolidBackgroundColor(c)))
  }

  lazy val searchBox = returning(view[SearchEditText](R.id.multi_share_search_box)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setCursorColor(c)))

    ZMessaging.currentAccounts.activeAccount.onChanged.onUi(_ => vh.foreach(v => v.getElements.foreach(v.removeElement)))

    (for {
      z        <- zms
      convs    <- z.convsContent.conversationsSignal
      selected <- Signal.wrap(adapter.conversationSelectEvent)
    } yield (convs.conversations.find(c => c.id == selected._1).map(PickableConversation), selected._2)).onUi {
      case (Some(convData), true)  => vh.foreach(_.addElement(convData))
      case (Some(convData), false) => vh.foreach(_.removeElement(convData))
      case _ =>
    }
  }

  lazy val contentLayout = returning(view[RelativeLayout](R.id.content_container)) { vh =>
    //TODO: It's possible for an app to share multiple uris at once but we're only showing the preview for one

    sharingController.sharableContent.onUi {
      case Some(content) => vh.foreach { layout =>
        layout.removeAllViews()

        val contentHeight = getDimenPx(content match {
          case TextContent(_)  => R.dimen.collections__multi_share__text_preview__height
          case ImageContent(_) => R.dimen.collections__multi_share__image_preview__height
          case FileContent(_)  => R.dimen.collections__multi_share__file_preview__height
        })

        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, contentHeight))

        val inflater = getLayoutInflater
        content match {
          case TextContent(text) =>
            inflater.inflate(R.layout.share_preview_text, layout).findViewById[TypefaceTextView](R.id.text_content).setText(text)

          case ImageContent(uris) =>
            returning(inflater.inflate(R.layout.share_preview_image, layout).findViewById[ImageView](R.id.image_content)) { imagePreview =>
              val imageAsset = AssetData.newImageAssetFromUri(tag = Tag.Medium, uri = uris.head)
              val drawable = new ImageAssetDrawable(Signal(DataImage(imageAsset)), ScaleType.CenterCrop, RequestBuilder.Regular)
              imagePreview.setImageDrawable(drawable)
            }

          case FileContent(uris) =>
            returning(inflater.inflate(R.layout.share_preview_file, layout)) { previewLayout =>
              val assetForUpload = ContentUriAssetForUpload(AssetId(), uris.head)

              assetForUpload.name.onComplete {
                case Success(Some(name)) => previewLayout.findViewById[TextView](R.id.file_name).setText(name)
                case _ =>
              }(Threading.Ui)

              assetForUpload.sizeInBytes.onComplete {
                case Success(Some(size)) =>
                  returning(previewLayout.findViewById(R.id.file_info).asInstanceOf[TextView]) { tv =>
                    tv.setVisibility(View.GONE)
                    tv.setText(Formatter.formatFileSize(getContext, size))
                  }

                case _ => previewLayout.findViewById[TextView](R.id.file_info).setVisibility(View.GONE)
              }(Threading.Ui)
            }
        }
      }
      case _ =>
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_collection_share, container, false)


  private var subs = Set.empty[Subscription]
  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    adapter
    bottomContainer
    contentLayout

    convList.foreach { list =>
      list.setLayoutManager(new LinearLayoutManager(getContext))
      list.setAdapter(adapter)
    }

    accountTabs.map(_.onTabClick.map(a => Some(a.id))(accounts.setAccount)).foreach(subs += _)

    sendButton.foreach(_.onClick {
      if (!adapter.selectedConversations.currentValue.forall(_.isEmpty)) {
        onClickEvent ! {}
      }
    })

    ephemeralToggle.foreach(toggle => toggle.onClick {
      bottomContainer.foreach { bc =>
        bc.isExpanded.currentValue match {
          case Some(true) =>
            bc.closedAnimated()
          case Some(false) =>
            returning(getLayoutInflater.inflate(R.layout.ephemeral_keyboard_layout, null, false).asInstanceOf[EphemeralLayout]) { l =>
              sharingController.ephemeralExpiration.currentValue.foreach(l.setSelectedExpiration)
              l.setCallback(new EphemeralLayout.Callback() {
                override def onEphemeralExpirationSelected(expiration: EphemeralExpiration, close: Boolean): Unit = {
                  sharingController.ephemeralExpiration ! expiration
                  toggle.ephemeralExpiration ! expiration
                  if (close) bc.closedAnimated()
                }
              })
              bc.addView(l)
            }
            bc.openAnimated()
          case _ =>
        }
      }
    })

    searchBox.foreach { box =>
      box.setCallback(new PickerSpannableEditText.Callback {
        override def onRemovedTokenSpan(element: PickableElement) =
          adapter.conversationSelectEvent ! (ConvId(element.id), false)

        override def afterTextChanged(s: String) = filterText ! box.getSearchFilter
      })

      box.setOnEditorActionListener(new OnEditorActionListener {
        override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
          if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
            if (adapter.selectedConversations.currentValue.forall(_.isEmpty)) false
            else {
              KeyboardUtils.closeKeyboardIfShown(getActivity)
              onClickEvent ! {}
              true
            }
          } else false
        }
      })
    }
  }


  override def onDestroyView() = {
    subs.foreach(_.destroy())
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean = {
    val bottomContainer = findById[AnimatedBottomContainer](getView, R.id.ephemeral_container)
    if (bottomContainer.isExpanded.currentValue.exists(a => a)) {
      bottomContainer.closedAnimated()
      true
    } else false
  }
}

object ShareToMultipleFragment {
  val TAG = ShareToMultipleFragment.getClass.getSimpleName

  def newInstance(): ShareToMultipleFragment = {
    new ShareToMultipleFragment
  }
}

case class PickableConversation(conversationData: ConversationData) extends PickableElement{
  override def id = conversationData.id.str
  override def name = conversationData.displayName
}

class ShareToMultipleAdapter(context: Context, filter: Signal[String])(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[RecyclerView.ViewHolder] with Injectable {
  setHasStableIds(true)
  lazy val zms = inject[Signal[ZMessaging]]
  lazy val conversations = for{
    z <- zms
    conversations <- Signal.future(z.convsContent.storage.getAllConvs)
    f <- filter
  } yield
    conversations
      .filter(c => (c.convType == ConversationType.Group || c.convType == ConversationType.OneToOne) && !c.hidden && c.displayName.toLowerCase.contains(f.toLowerCase))
      .sortWith((a, b) => a.lastEventTime.isAfter(b.lastEventTime))

  conversations.on(Threading.Ui) {
    _ => notifyDataSetChanged()
  }

  val selectedConversations: SourceSignal[Set[ConvId]] = Signal(Set())

  val conversationSelectEvent = EventStream[(ConvId, Boolean)]()
  conversationSelectEvent.on(Threading.Ui){ event =>
    if (event._2) {
      selectedConversations.mutate( _ + event._1)
    } else {
      selectedConversations.mutate( _ - event._1)
    }
    notifyDataSetChanged()
  }

  private val checkBoxListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      buttonView.setContentDescription(if (isChecked) "selected" else "")
      Option(buttonView.getTag.asInstanceOf[ConvId]).foreach{ convId =>
        conversationSelectEvent ! (convId, isChecked)
      }
    }
  }

  def getItem(position: Int): Option[ConversationData] = conversations.currentValue.map(_(position))

  override def getItemCount: Int = conversations.currentValue.fold(0)(_.size)

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = {
    getItem(position) match {
      case Some(conv) =>
        holder.asInstanceOf[SelectableConversationRowViewHolder].setConversation(conv.id, selectedConversations.currentValue.exists(_.contains(conv.id)))
      case _ =>
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
    SelectableConversationRowViewHolder(new SelectableConversationRow(context, checkBoxListener))

  override def getItemId(position: Int): Long = getItem(position).fold(0)(_.id.hashCode()).toLong

  override def getItemViewType(position: Int): Int = 1
}

case class SelectableConversationRowViewHolder(view: SelectableConversationRow)(implicit eventContext: EventContext, injector: Injector) extends RecyclerView.ViewHolder(view) with Injectable{
  lazy val zms = inject[Signal[ZMessaging]]

  val conversationId = Signal[ConvId]()

  val convSignal = for {
    z <- zms
    cid <- conversationId
    conversations <- z.convsStorage.convsSignal
    conversation <- Signal(conversations.conversations.find(_.id == cid))
  } yield conversation

  convSignal.on(Threading.Ui){
    case Some(conversationData) =>
      val name = conversationData.displayName
      if (name.isEmpty) {
        import Threading.Implicits.Background
        zms.head.flatMap(_.conversations.forceNameUpdate(conversationData.id))
      }
      view.nameView.setText(conversationData.displayName)
    case _ => view.nameView.setText("")
  }

  def setConversation(convId: ConvId, checked: Boolean): Unit = {
    view.checkBox.setTag(null)
    view.checkBox.setChecked(checked)
    view.checkBox.setTag(convId)
    conversationId ! convId
  }
}

class SelectableConversationRow(context: Context, checkBoxListener: CompoundButton.OnCheckedChangeListener) extends LinearLayout(context, null, 0) {

  setPadding(
    getResources.getDimensionPixelSize(R.dimen.wire__padding__12),
    getResources.getDimensionPixelSize(R.dimen.list_tile_top_padding),
    getResources.getDimensionPixelSize(R.dimen.wire__padding__12),
    getResources.getDimensionPixelSize(R.dimen.list_tile_bottom_padding))
  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources.getDimensionPixelSize(R.dimen.list_row_height)))
  setOrientation(LinearLayout.HORIZONTAL)

  LayoutInflater.from(context).inflate(R.layout.row_selectable_conversation, this, true)
  val nameView = ViewUtils.getView(this, R.id.ttv__conversation_name).asInstanceOf[TypefaceTextView]
  val checkBox = ViewUtils.getView(this, R.id.rb__conversation_selected).asInstanceOf[CheckBox]
  val buttonDrawable = ContextCompat.getDrawable(getContext, R.drawable.checkbox)
  buttonDrawable.setLevel(1)
  checkBox.setButtonDrawable(buttonDrawable)

  checkBox.setOnCheckedChangeListener(checkBoxListener)
  nameView.setOnClickListener(new OnClickListener() {
    override def onClick(v: View): Unit = checkBox.toggle()
  })
}
