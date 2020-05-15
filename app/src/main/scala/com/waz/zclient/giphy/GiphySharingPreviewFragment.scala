/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.giphy

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{EditText, ImageView, TextView}
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{RecyclerView, StaggeredGridLayoutManager}
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.waz.model.{AssetData, Mime}
import com.waz.service.assets.{Content, ContentForUpload}
import com.waz.service.media.GiphyService.GifObject
import com.waz.service.tracking.ContributionEvent
import com.waz.service.{NetworkModeService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{ScreenController, ThemeController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.giphy.GiphyGridViewAdapter.ScrollGifCallback
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.views.{ConfirmationMenu, ConfirmationMenuListener}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils.getColorWithTheme
import com.waz.zclient.utils.{RichEditText, RichView}
import com.waz.zclient.views.LoadingIndicatorView

import scala.concurrent.Future

//TODO Should be splitted in two fragments
class GiphySharingPreviewFragment extends BaseFragment[GiphySharingPreviewFragment.Container]
  with FragmentHelper
  with OnBackPressedListener {

  import GiphySharingPreviewFragment._
  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val themeController = inject[ThemeController]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val keyboardController = inject[KeyboardController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val networkService = inject[NetworkModeService]
  private lazy val screenController = inject[ScreenController]
  private lazy val giphyService = zms.map(_.giphy)
  private lazy val spinnerController = inject[SpinnerController]

  private lazy val searchTerm = Signal[String]("")
  private lazy val isPreviewShown = Signal[Boolean](false)
  private lazy val isGifShowing = Signal[Boolean](false)
  private lazy val selectedGif = Signal[Option[GifObject]]()

  private lazy val giphySearchResults = for {
    giphyService <- giphyService
    term <- searchTerm
    searchResults <- Signal.future(
      if (TextUtils.isEmpty(term)) giphyService.trending()
      else giphyService.search(term)
    )
  } yield searchResults

  private lazy val previewImage = returning(view[ImageView](R.id.giphy_preview)) { vh =>
    networkService.isOnline.onUi(isOnline => vh.foreach(_.setClickable(isOnline)))
    isPreviewShown.onUi(isPreview => vh.foreach(_.fade(isPreview)))
    selectedGif.onUi {
      case Some(gif) => vh.foreach { v =>
        WireGlide(getContext)
          .load(gif.original.source.toString)
          .addListener(new RequestListener[Drawable]() {
            override def onLoadFailed(e: GlideException, model: scala.Any, target: Target[Drawable], isFirstResource: Boolean): Boolean = {
              isGifShowing ! false
              false
            }

            override def onResourceReady(resource: Drawable, model: scala.Any, target: Target[Drawable], dataSource: DataSource, isFirstResource: Boolean): Boolean = {
              isGifShowing ! true
              false
            }
          })
          .into(v)
      }
      case _ => vh.foreach(WireGlide(getContext).clear(_))
    }
  }

  private lazy val giphyTitle = returning(view[TextView](R.id.ttv__giphy_preview__title)) { vh =>
    conversationController.currentConvName.onUi(text => vh.foreach(_.setText(text)))
    isPreviewShown.onUi(isPreview => vh.foreach(_.fade(isPreview)))
  }

  private lazy val confirmationMenu = returning(view[ConfirmationMenu](R.id.cm__giphy_preview__confirmation_menu)) { vh =>
    accentColorController.accentColor.map(_.color).onUi { color =>
      vh.foreach { v =>
        v.setAccentColor(color)
        if (!themeController.isDarkTheme) {
          v.setCancelColor(color, color)
          v.setConfirmColor(getColorWithTheme(R.color.white), color)
        }
      }
    }
    isPreviewShown.onUi(isPreview => vh.foreach(_.fade(isPreview)))
  }

  private lazy val toolbar = returning(view[Toolbar](R.id.t__giphy__toolbar)) { vh =>
    isPreviewShown.map {
      case true =>
        if (themeController.isDarkTheme) R.drawable.action_back_light
        else R.drawable.action_back_dark
      case false =>
        if (themeController.isDarkTheme) R.drawable.ic_action_search_light
        else R.drawable.ic_action_search_dark
    }.onUi(icon => vh.foreach(_.setNavigationIcon(icon)))
  }
  private lazy val giphySearchEditText = returning(view[EditText](R.id.cet__giphy_preview__search)) { vh =>
    isPreviewShown.map(!_).onUi(isPreview => vh.foreach(_.fade(isPreview)))
  }

  private lazy val errorView = returning(view[TextView](R.id.ttv__giphy_preview__error)) { vh =>
    val isResultsEmpty = giphySearchResults.map(_.isEmpty)
    isResultsEmpty.map(isEmpty => if (isEmpty) View.VISIBLE else View.GONE)
      .onUi(visibility => vh.foreach(_.setVisibility(visibility)))
    isResultsEmpty.map(isEmpty => if (isEmpty) getString(R.string.giphy_preview__error) else "")
      .onUi(msg => vh.foreach { v =>
        v.setText(msg)
        TextViewUtils.mediumText(v)
      })
  }

  private lazy val recyclerView: ViewHolder[RecyclerView] = returning(view[RecyclerView](R.id.rv__giphy_image_preview)) { vh =>
    isPreviewShown.map(!_).onUi(isPreview => vh.foreach(_.fade(isPreview)))
    giphySearchResults.map(_.nonEmpty).onUi(isResult => vh.foreach(_.fade(isResult)))
  }

  private lazy val closeButton = returning(view[View](R.id.gtv__giphy_preview__close_button)) { vh =>
    vh.onClick { _ => screenController.hideGiphy ! false }
  }

  private lazy val giphyGridViewAdapter = returning(new GiphyGridViewAdapter(
    scrollGifCallback = new ScrollGifCallback {
      override def setSelectedGifFromGridView(gif: GifObject): Unit = {
        isPreviewShown ! true
        selectedGif ! Some(gif)
        keyboardController.hideKeyboardIfVisible()
      }
    }
  )) { adapter => giphySearchResults.onUi(adapter.setGiphyResults) }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_giphy_preview, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    closeButton
    previewImage

    Option(getArguments).orElse(Option(savedInstanceState)).foreach { bundle =>
      giphySearchEditText.foreach(_.setText(bundle.getString(ArgSearchTerm)))
    }

    giphySearchEditText.foreach { _.afterTextChangedSignal() pipeTo searchTerm }
    errorView.foreach(_.setVisibility(View.GONE))

    recyclerView.foreach { v =>
      v.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL))
      v.setAdapter(giphyGridViewAdapter)
    }

    confirmationMenu.foreach { v =>
      v.setConfirmationMenuListener(new ConfirmationMenuListener() {
        override def confirm(): Unit = networkService.isOnline.head.filter(_ == true).foreach(_ => sendGif())
        override def cancel(): Unit = {
          isPreviewShown ! false
          selectedGif ! None
        }
      })
      v.setConfirm(getString(R.string.sharing__image_preview__confirm_action))
      v.setCancel(getString(R.string.confirmation_menu__cancel))
      v.setWireTheme(themeController.getThemeDependentOptionsTheme)
      v.setVisibility(View.GONE)
    }

    toolbar.foreach { v =>
      v.setNavigationOnClickListener(new View.OnClickListener() {
        override def onClick(view: View): Unit = {
          isPreviewShown.currentValue.foreach { _ =>
            isPreviewShown ! false
            selectedGif ! None
          }
        }
      })
    }

    EventStream.union(
      searchTerm.onChanged.map(_ => true),
      selectedGif.onChanged.filter(_.isDefined).map(_ => true),
      giphySearchResults.onChanged.map(_ => false),
      isPreviewShown.onChanged.filter(_ == false),
      isGifShowing.onChanged.map(!_)
    ) onUi {
      case true  => spinnerController.showSpinner(LoadingIndicatorView.InfiniteLoadingBar)
      case false => spinnerController.hideSpinner()
    }

    giphyTitle.foreach(_.setVisibility(View.GONE))
  }

  override def onStart(): Unit = {
    super.onStart()
    keyboardController.hideKeyboardIfVisible()
  }

  override def onStop(): Unit = {
    keyboardController.hideKeyboardIfVisible()
    super.onStop()
  }

  override def onBackPressed(): Boolean =
    returning(isPreviewShown.currentValue.getOrElse(false)) { isVisible =>
      if (isVisible) {
        isPreviewShown ! false
        selectedGif ! None
      }
    }

  //TODO Should be moved to 'Presenter'
  private def sendGif(): Unit = {
    ZMessaging.currentGlobal.trackingService.contribution(new ContributionEvent.Action("text"))
    for {
      term <- searchTerm.head
      gif  <- selectedGif.head
      msg =
        if (TextUtils.isEmpty(term)) getString(R.string.giphy_preview__message_via_random_trending)
        else getString(R.string.giphy_preview__message_via_search, term)
      gifContent <- Future { WireGlide(getContext).as(classOf[Array[Byte]]).load(gif.get.original.source.toString).submit().get() }(Threading.Background)
      contentForUpload = ContentForUpload(s"${gif.get.id}.${Mime.extensionsMap(Mime.Image.Gif)}", Content.Bytes(Mime.Image.Gif, gifContent))
      _  <- conversationController.sendMessage(msg)
      _  <- conversationController.sendAssetMessage(contentForUpload, getActivity, None)
    } yield ()
    screenController.hideGiphy ! true
  }

}

object GiphySharingPreviewFragment {

  val Tag: String = classOf[GiphySharingPreviewFragment].getSimpleName
  val ArgSearchTerm = "SEARCH_TERM"
  val GiphySearchDelayMinSec = 800

  def newInstance(searchTerm: Option[String]): GiphySharingPreviewFragment =
    returning(new GiphySharingPreviewFragment) { fragment =>
      searchTerm.foreach(term =>
        fragment.setArguments(returning(new Bundle)(_.putString(ArgSearchTerm, term)))
      )
    }


  trait Container {}

  case class GifData(preview: Option[AssetData], gif: AssetData)

}
