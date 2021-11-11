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
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View.OnClickListener
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView.OnEditorActionListener
import android.widget._
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.waz.api.impl.ContentUriAssetForUpload
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.{AreSelfDeletingMessagesEnabled, SelfDeletingMessagesEnforcedTimeout}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{MessageContent => _, _}
import com.waz.service.assets.{FileRestrictionList, UriHelper}
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.wire.signals._
import com.waz.utils.wrappers.URI
import com.waz.utils.{RichWireInstant, returning}
import com.waz.zclient._
import com.waz.zclient.R
import com.waz.zclient.common.controllers.SharingController
import com.waz.zclient.common.controllers.SharingController.{FileContent, ImageContent, NewContent, TextContent}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.cursor.{EphemeralLayout, EphemeralTimerButton}
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.{ColorUtils, KeyboardUtils}
import com.waz.zclient.ui.views.CursorIconButton
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}
import com.waz.zclient.utils.ContextUtils.{getDimenPx, showErrorDialog, showToast}
import com.waz.zclient.utils.{RichView, ViewUtils}

import scala.util.Success
import com.waz.threading.Threading._
import com.waz.zclient.legalhold.LegalHoldController

import scala.concurrent.duration.DurationInt

class ConversationSelectorFragment extends FragmentHelper with OnBackPressedListener {
  import Threading.Implicits.Ui
  implicit def cxt = getContext

  import ConversationSelectorFragment._

  private lazy val accounts          = inject[AccountsService]
  private lazy val convController    = inject[ConversationController]
  private lazy val sharingController = inject[SharingController]
  private lazy val accentColor       = inject[AccentColorController].accentColor.map(_.color)

  private lazy val filterText = Signal[String]("")

  private lazy val onClickEvent = EventStream[Unit]()

  private lazy val multiPicker = getBooleanArg(MultiPickerArgumentKey)

  private lazy val adapter = returning(new ConversationSelectorAdapter(getContext, filterText, multiPicker)) { a =>
    onClickEvent.onUi { _ =>
      a.selectedConversations.head.map { convs =>
        sharingController.onContentShared(getActivity, convs)
        if (multiPicker) showToast(R.string.multi_share_toast_sending, long = false)
        getActivity.finish()
      } (Threading.Ui)
    }
  }

  private lazy val convList = view[RecyclerView](R.id.lv__conversation_list)
  private lazy val accountTabs = view[AccountTabsView](R.id.account_tabs)
  private lazy val bottomContainer = view[AnimatedBottomContainer](R.id.ephemeral_container)
  private lazy val ephemeralIcon = view[EphemeralTimerButton](R.id.ephemeral_toggle)

  private lazy val userPrefs = inject[Signal[UserPreferences]]
  private lazy val areSelfDeletingMessagesEnabled = userPrefs.flatMap { prefs => prefs(AreSelfDeletingMessagesEnabled).signal }
  private lazy val isEphemeralButtonVisible = areSelfDeletingMessagesEnabled

  private lazy val enforcedSelfDeletingMessagesTimeout = userPrefs.flatMap { prefs => prefs(SelfDeletingMessagesEnforcedTimeout).signal }

  private lazy val sendButton = returning(view[CursorIconButton](R.id.cib__send_button)) { vh =>
    (for {
      convs <- adapter.selectedConversations
      color <- accentColor
    } yield if (convs.nonEmpty) color else ColorUtils.injectAlpha(0.4f, color)).onUi(c => vh.foreach(_.setSolidBackgroundColor(c)))
  }

  private lazy val searchBox = returning(view[SearchEditText](R.id.multi_share_search_box)) { vh =>
    accentColor.onUi(c => vh.foreach(_.setCursorColor(c)))

    if (!multiPicker) vh.foreach(_.findById[TypefaceTextView](R.id.hint).setText(getString(R.string.single_selector_search_hint)))

    ZMessaging.currentAccounts.activeAccount.onChanged.onUi(_ => vh.foreach(v => v.getElements.foreach(v.removeElement)))

    (for {
      selected <- Signal.from(adapter.conversationSelectEvent)
      name     <- convController.conversationName(selected._1)
    } yield (PickableConversation(selected._1.str, name.str), selected._2)).onUi {
      case (convData, true) if multiPicker  => vh.foreach(_.addElement(convData))
      case (convData, false) if multiPicker => vh.foreach(_.removeElement(convData))
      case _ =>
    }
  }

  private def checkFileRestrictions(uris: Seq[URI]): Seq[String] =
    uris.map(URI.toJava)
      .map(inject[UriHelper].extractFileName)
      .collect { case Success(name) if !inject[FileRestrictionList].isAllowed(name) => name }
      .map { _.split('.').last }
      .distinct
      .sorted

  lazy val contentLayout = returning(view[RelativeLayout](R.id.content_container)) { vh =>
    //It's possible for an app to share multiple uris at once but we're only showing the preview for one

    sharingController.sharableContent.onUi {
      case Some(content) => vh.foreach { layout =>
        layout.removeAllViews()

        val contentHeight = getDimenPx(content match {
          case NewContent  => R.dimen.zero
          case TextContent(_)  => R.dimen.collections__multi_share__text_preview__height
          case ImageContent(_) => R.dimen.collections__multi_share__image_preview__height
          case FileContent(_)  => R.dimen.collections__multi_share__file_preview__height
        })

        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, contentHeight))

        val inflater = getLayoutInflater
        content match {
          case NewContent =>

          case TextContent(text) =>
            inflater.inflate(R.layout.share_preview_text, layout).findViewById[TypefaceTextView](R.id.text_content).setText(text)

          case ImageContent(uris) =>
            returning(inflater.inflate(R.layout.share_preview_image, layout).findViewById[ImageView](R.id.image_content)) { imagePreview =>
              WireGlide(cxt).load(Uri.parse(uris.head.toString))
                .apply(new RequestOptions().centerCrop().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(imagePreview)
            }

          case FileContent(uris) =>
            val restricted = checkFileRestrictions(uris)
            if (restricted.nonEmpty)
              showErrorDialog("", getString(R.string.file_restrictions__sender_error, restricted.mkString(", ")))
                .foreach(_ => getActivity.finish())(Threading.Ui)
            else
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

    accountTabs.map(_.onTabClick.map(a => Some(a.id)).foreach(accounts.setAccount)).foreach(subs += _)

    sendButton.foreach(_.onClick {
      if (!adapter.selectedConversations.currentValue.forall(_.isEmpty)) {
        onClickEvent ! {}
      }
    })

    ephemeralIcon.foreach { icon =>
        enforcedSelfDeletingMessagesTimeout.map(_ > 0)
          .pipeTo(icon.hasEnforcedTimeout)

        enforcedSelfDeletingMessagesTimeout.filter(_ > 0)
          .map { timeoutInSeconds => Some(ConvExpiry(timeoutInSeconds.seconds)).asInstanceOf[Option[EphemeralDuration]] }
          .pipeTo(icon.ephemeralExpiration)
    }
    ephemeralIcon.foreach(icon =>
      isEphemeralButtonVisible.onUi(icon.setVisible)
    )
    ephemeralIcon.foreach(icon =>
      icon.onClick {
        for {
          enforcedExpiration  <- enforcedSelfDeletingMessagesTimeout.head
        } yield {
          if(enforcedExpiration == 0){
            openEphemeralSettings(icon)
          }
        }
      }
    )

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


  private def openEphemeralSettings(ephemeralIcon: EphemeralTimerButton) = {
    bottomContainer.foreach { bc =>
      bc.isExpanded.currentValue match {
        case Some(true) =>
          bc.closedAnimated()
        case Some(false) =>
          returning(getLayoutInflater.inflate(R.layout.ephemeral_keyboard_layout, null, false).asInstanceOf[EphemeralLayout]) { l =>
            sharingController.ephemeralExpiration.foreach(l.setSelectedExpiration)
            l.expirationSelected.onUi { case (exp, close) =>
              ephemeralIcon.ephemeralExpiration ! exp.map(MessageExpiry)
              sharingController.ephemeralExpiration ! exp
              if (close) bc.closedAnimated()
            }
            bc.addView(l)
          }
          bc.openAnimated()
        case _ =>
      }
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

object ConversationSelectorFragment {
  val MultiPickerArgumentKey = "multiPickerArgumentKey"
  val TAG = ConversationSelectorFragment.getClass.getSimpleName

  def newInstance(multiPicker: Boolean): ConversationSelectorFragment = {
    val bundle = new Bundle()
    bundle.putBoolean(MultiPickerArgumentKey, multiPicker)
    returning(new ConversationSelectorFragment) {
      _.setArguments(bundle)
    }
  }
}

case class PickableConversation(override val id: String, override val name: String) extends PickableElement

class ConversationSelectorAdapter(context: Context, filter: Signal[String], multiPicker: Boolean)(implicit injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[RecyclerView.ViewHolder]
    with Injectable
    with DerivedLogTag {

  setHasStableIds(true)

  private lazy val conversations = for {
    z             <-  inject[Signal[ZMessaging]]
    convs         <- z.convsStorage.contents
    visible       =  convs.filter { case (_, c) => (c.convType == ConversationType.Group || c.convType == ConversationType.OneToOne) && !c.hidden }
    names         <- Signal.sequence(visible.keys.map(id => z.conversations.conversationName(id).map(name => id -> name.toLowerCase)).toArray: _*)
    f             <- filter
    filterName    =  f.toLowerCase
    filteredIds   =  names.filter { case (_, name) => name.contains(filterName) }.map(_._1).toSet
    conversations =  visible.filterKeys(filteredIds.contains).values.toSeq
  } yield
    conversations.sortWith((a, b) => a.lastEventTime.isAfter(b.lastEventTime))

  conversations.onUi(_ => notifyDataSetChanged())

  val selectedConversations: SourceSignal[Seq[ConvId]] = Signal(Seq.empty)

  val conversationSelectEvent = EventStream[(ConvId, Boolean)]()

  conversationSelectEvent.onUi {
    case (conv, add) =>
      if (multiPicker)  { selectedConversations.mutate(convs => if (add) convs :+ conv else convs.filterNot(_ == conv)) } else {
        selectedConversations ! (if (add) Seq(conv) else Seq.empty)
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

case class SelectableConversationRowViewHolder(view: SelectableConversationRow)(implicit eventContext: EventContext, injector: Injector)
  extends RecyclerView.ViewHolder(view)
    with Injectable
    with DerivedLogTag {

  import SelectableConversationRowViewHolder._

  private val conversationId = Signal[ConvId]()
  private lazy val convController = inject[ConversationController]
  private lazy val legalHoldController = inject[LegalHoldController]

  (for {
    cid      <- conversationId
    convName <- convController.conversationName(cid)
  } yield convName).onUi { convName =>
    view.nameView.setText(convName.str)
  }

  (for {
    cid      <- conversationId
    lhActive <- legalHoldController.isLegalHoldActive(cid)
  } yield lhActive).map {
    case true  => (Some(R.drawable.ic_legal_hold_active), LegalHoldLocator)
    case false => (None, "")
  }.onUi { case (iconRes, contentDesc) =>
    view.nameView.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes.getOrElse(0), 0)
    view.nameView.setContentDescription(contentDesc)
  }

  def setConversation(convId: ConvId, checked: Boolean): Unit = {
    view.checkBox.setTag(null)
    view.checkBox.setChecked(checked)
    view.checkBox.setTag(convId)
    conversationId ! convId
  }
}

object SelectableConversationRowViewHolder {
  val LegalHoldLocator = "Legal hold active"
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
