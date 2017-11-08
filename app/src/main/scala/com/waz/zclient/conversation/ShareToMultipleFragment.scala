/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.conversation

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
import com.waz.api
import com.waz.api.{AssetFactory, EphemeralExpiration}
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{MessageContent => _, _}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceSignal}
import com.waz.zclient._
import com.waz.zclient.controllers.SharingController.{FileContent, ImageContent, SharableContent, TextContent}
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.controllers.{AssetsController, SharingController}
import com.waz.zclient.messages.{MessagesController, UsersController}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.extendedcursor.ephemeral.EphemeralLayout
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.{ColorUtils, KeyboardUtils}
import com.waz.zclient.ui.views.CursorIconButton
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.DataImage
import com.waz.zclient.views._

import scala.util.Success


class ShareToMultipleFragment extends BaseFragment[ShareToMultipleFragment.Container] with FragmentHelper with OnBackPressedListener {

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val assetsController = inject[AssetsController]
  lazy val messagesController = inject[MessagesController]
  lazy val accentColorController = inject[AccentColorController]
  lazy val sharingController = inject[SharingController]
  lazy val usersController = inject[UsersController]

  val updatePreviewEvent = EventStream[Unit]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_collection_share, container, false)
    val listView = ViewUtils.getView(view, R.id.lv__conversation_list).asInstanceOf[RecyclerView]
    val sendButton = ViewUtils.getView(view, R.id.cib__send_button).asInstanceOf[CursorIconButton]
    val searchBox = ViewUtils.getView(view, R.id.multi_share_search_box).asInstanceOf[SearchEditText]
    val contentLayout = ViewUtils.getView(view, R.id.content_container).asInstanceOf[RelativeLayout]
    val bottomContainer = ViewUtils.getView(view, R.id.ephemeral_container).asInstanceOf[AnimatedBottomContainer]
    val ephemeralToggle = ViewUtils.getView(view, R.id.ephemeral_toggle).asInstanceOf[EphemeralCursorButton]
    val accountTabs = findById[AccountTabsView](view, R.id.account_tabs)

    val onClickEvent = EventStream[Unit]()
    val filterText = Signal[String]("")
    val adapter = new ShareToMultipleAdapter(getContext, filterText)

    accountTabs.onTabClick { account =>
      ZMessaging.currentAccounts.switchAccount(account.id)
    }

    Signal(accentColorController.accentColorNoEmpty, adapter.selectedConversations).on(Threading.Ui){
      case (color, convs) if convs.nonEmpty =>
        sendButton.setSolidBackgroundColor(color.getColor)
        searchBox.setCursorColor(color.getColor)
      case (color, _) =>
        sendButton.setSolidBackgroundColor(ColorUtils.injectAlpha(0.4f, color.getColor))
        searchBox.setCursorColor(color.getColor)
      case _ =>
    }

    val convSignal = for {
      z <- zms
      convs <- z.convsContent.conversationsSignal
      selected <- Signal.wrap(adapter.conversationSelectEvent)
     } yield (convs.conversations.find(c => c.id == selected._1), selected._2)

    convSignal.on(Threading.Ui){
      case (Some(convData), true) =>
        searchBox.addElement(PickableConversation(convData))
      case (Some(convData), false) =>
        searchBox.removeElement(PickableConversation(convData))
      case _ =>
    }

    searchBox.setCallback(new PickerSpannableEditText.Callback {
      override def onRemovedTokenSpan(element: PickableElement) = {
        adapter.conversationSelectEvent ! (ConvId(element.id), false)
      }

      override def afterTextChanged(s: String) = {
        filterText ! searchBox.getSearchFilter
      }
    })

    listView.setLayoutManager(new LinearLayoutManager(getContext))
    listView.setAdapter(adapter)


    //TODO: It's possible for an app to share multiple uris at once but we're only showing the preview for one
    def showMessagePreview(content: Option[SharableContent]): Unit = content match{
      case Some(TextContent(text)) =>
        contentLayout.removeAllViews()

        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          getContext.getResources.getDimensionPixelSize(R.dimen.collections__multi_share__text_preview__height)))

        val contentTextView = inflater.inflate(R.layout.share_preview_text, contentLayout)
        contentTextView.findViewById(R.id.text_content).asInstanceOf[TypefaceTextView].setText(text)
      case Some(ImageContent(uris)) =>
        contentLayout.removeAllViews()

        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          getContext.getResources.getDimensionPixelSize(R.dimen.collections__multi_share__image_preview__height)))

        inflater.inflate(R.layout.share_preview_image, contentLayout)
        val contentImageView = contentLayout.findViewById(R.id.image_content).asInstanceOf[ImageView]

        val imageAsset = AssetData.newImageAssetFromUri(tag = Tag.Medium, uri = uris.head)
        val drawable = new ImageAssetDrawable(Signal(DataImage(imageAsset)), ScaleType.CenterCrop, RequestBuilder.Regular)
        contentImageView.setImageDrawable(drawable)

      case Some(FileContent(uris)) =>
        contentLayout.removeAllViews()

        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          getContext.getResources.getDimensionPixelSize(R.dimen.collections__multi_share__file_preview__height)))

        val contentView = inflater.inflate(R.layout.share_preview_file, contentLayout)
        val assetForUpload = Option(AssetFactory.fromContentUri(uris.head).asInstanceOf[api.impl.AssetForUpload])
        assetForUpload.foreach(_.name.onComplete{
          case Success(Some(name)) => contentView.findViewById(R.id.file_name).asInstanceOf[TextView].setText(name)
          case _ =>
        }(Threading.Ui))
        assetForUpload.foreach(_.sizeInBytes.onComplete{
          case Success(Some(size)) =>
            val textView = contentView.findViewById(R.id.file_info).asInstanceOf[TextView]
            textView.setVisibility(View.GONE)
            textView.setText(Formatter.formatFileSize(getContext, size))
          case _ => contentView.findViewById(R.id.file_info).asInstanceOf[TextView].setVisibility(View.GONE)
        }(Threading.Ui))
      case _ =>
    }

    sharingController.sharableContent.on(Threading.Ui) {
      showMessagePreview
    }

    updatePreviewEvent.on(Threading.Ui) { _ =>
      sharingController.sharableContent.currentValue.foreach(showMessagePreview)
    }

    onClickEvent { _ =>
      adapter.selectedConversations.head.map { convs =>
        sharingController.onContentShared(getActivity, convs)
        Toast.makeText(getContext, R.string.multi_share_toast_sending, Toast.LENGTH_SHORT).show()
        getActivity.finish()
      } (Threading.Ui)
    }

    searchBox.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
          if (adapter.selectedConversations.currentValue.forall(_.isEmpty)) {
            return false
          }
          KeyboardUtils.closeKeyboardIfShown(getActivity)
          onClickEvent ! (())
          return true
        }
        false
      }
    })

    sendButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (adapter.selectedConversations.currentValue.forall(_.isEmpty)) {
          return
        }
        onClickEvent ! (())
      }
    })

    val ephemeralCallback = new EphemeralLayout.Callback(){
      override def onEphemeralExpirationSelected(expiration: EphemeralExpiration, close: Boolean): Unit = {
        sharingController.ephemeralExpiration ! expiration
        ephemeralToggle.ephemeralExpiration ! expiration
        if (close)
          bottomContainer.closedAnimated()
      }
    }

    ephemeralToggle.setOnClickListener(new OnClickListener{
      override def onClick(v: View): Unit = {
        bottomContainer.isExpanded.currentValue match {
          case Some(true) =>
            bottomContainer.closedAnimated()
          case Some(false) =>
            val ephemeralLayout = inflater.inflate(R.layout.ephemeral_keyboard_layout, null, false).asInstanceOf[EphemeralLayout]
            sharingController.ephemeralExpiration.currentValue.foreach(ephemeralLayout.setSelectedExpiration)
            ephemeralLayout.setCallback(ephemeralCallback)
            bottomContainer.addView(ephemeralLayout)
            bottomContainer.openAnimated()
          case _ =>
        }
      }
    })

    ZMessaging.currentAccounts.activeAccount.onChanged.onUi { _ =>
      searchBox.getElements.foreach(searchBox.removeElement)
    }

    view
  }

  override def onBackPressed(): Boolean = {
    val bottomContainer = ViewUtils.getView(getView, R.id.ephemeral_container).asInstanceOf[AnimatedBottomContainer]
    if (bottomContainer.isExpanded.currentValue.exists(a => a)) {
      bottomContainer.closedAnimated()
      return true
    }
    false
  }

  def updatePreview(): Unit ={
    updatePreviewEvent ! (())
  }
}

object ShareToMultipleFragment {
  val TAG = ShareToMultipleFragment.getClass.getSimpleName

  val MSG_ID_ARG = "MSG_ID_ARG"

  def newInstance(messageId: MessageId): ShareToMultipleFragment = {
    val fragment = new ShareToMultipleFragment
    val bundle = new Bundle()
    bundle.putString(MSG_ID_ARG, messageId.str)
    fragment.setArguments(bundle)
    fragment
  }

  def newInstance(): ShareToMultipleFragment = {
    new ShareToMultipleFragment
  }

  trait Container
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
