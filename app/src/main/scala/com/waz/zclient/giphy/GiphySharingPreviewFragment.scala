package com.waz.zclient.giphy

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

import android.os.Bundle
import android.support.v7.widget.{RecyclerView, StaggeredGridLayoutManager, Toolbar}
import android.text.{Editable, TextUtils, TextWatcher}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{EditText, ImageView, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.ImageAssetFactory
import com.waz.model.AssetData
import com.waz.service.images.BitmapSignal
import com.waz.service.tracking.ContributionEvent
import com.waz.service.{NetworkModeService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{ScaleType, State}
import com.waz.zclient.common.views.ImageController.{DataImage, ImageSource, NoImage}
import com.waz.zclient.controllers.giphy.IGiphyController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.giphy.GiphyGridViewAdapter.ScrollGifCallback
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.views.{ConfirmationMenu, ConfirmationMenuListener}
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.{ContextUtils, ViewUtils}
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R, ViewHolder}

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
  private lazy val giphyController = inject[IGiphyController]
  private lazy val giphyService = zms.map(_.giphy)

  private lazy val searchTerm = Signal[String]()
  private lazy val isPreviewShown = Signal[Boolean]()
  private lazy val selectedGif = Signal[Option[AssetData]]()
  private lazy val gifImage: Signal[ImageSource] = selectedGif.map {
    case Some(asset) => DataImage(asset)
    case _ => NoImage()
  }

  private val showView: View => Unit = ViewUtils.fadeInView
  private val hideView: View => Unit = ViewUtils.fadeOutView

  private lazy val giphySearchResults = for {
    giphyService <- giphyService
    term <- searchTerm
    searchResults <- Signal.future(
      if (TextUtils.isEmpty(term)) giphyService.trending()
      else giphyService.searchGiphyImage(term)
    )
  } yield searchResults.map(GifData.tupled)

  //TODO Move this signal to NetworkModeService
  private lazy val isOnline = networkService.networkMode.map(_ => networkService.isOnlineMode)

  private lazy val previewImage = returning(view[ImageView](R.id.giphy_preview)) { vh =>
    isOnline.onUi(isOnline => vh.foreach(_.setClickable(isOnline)))
    isPreviewShown.map(isPreview => if (isPreview) showView else hideView)
      .onUi(animate => vh.foreach(animate))
  }

  private lazy val giphyTitle = returning(view[TextView](R.id.ttv__giphy_preview__title)) { vh =>
    conversationController.currentConvName.onUi(text => vh.foreach(_.setText(text)))
    isPreviewShown.map(isPreview => if (isPreview) showView else hideView)
      .onUi(animate => vh.foreach(animate))
  }

  private lazy val confirmationMenu = returning(view[ConfirmationMenu](R.id.cm__giphy_preview__confirmation_menu)) { vh =>
    accentColorController.accentColor.map(_.getColor).onUi { color =>
      vh.foreach { v =>
        v.setAccentColor(color)
        if (!themeController.isDarkTheme) {
          v.setCancelColor(color, color)
          v.setConfirmColor(ContextUtils.getColorWithTheme(R.color.white, getContext), color)
        }
      }
    }
    isPreviewShown.map(isPreview => if (isPreview) showView else hideView)
      .onUi(animate => vh.foreach(animate))
  }

  private lazy val toolbar = view[Toolbar](R.id.t__giphy__toolbar)
  private lazy val giphySearchEditText = returning(view[EditText](R.id.cet__giphy_preview__search)) { vh =>
    vh.foreach { _.afterTextChangedSignal() pipeTo searchTerm }
    isPreviewShown.map(isPreview => if (isPreview) hideView else showView)
      .onUi(animate => vh.foreach(animate))
  }

  private lazy val loadingIndicator = returning(view[LoadingIndicatorView](R.id.liv__giphy_preview__loading)) { vh =>
    accentColorController.accentColor.onUi(color => vh.foreach(_.setColor(color.getColor)))
    searchTerm.onUi(_ => vh.foreach(_.show(LoadingIndicatorView.InfiniteLoadingBar)))
    giphySearchResults.onUi(_ => vh.foreach(_.hide()))
    selectedGif.filter(_.isDefined).onUi(_ => vh.foreach(_.show(LoadingIndicatorView.InfiniteLoadingBar)))
  }

  private lazy val errorView = returning(view[TextView](R.id.ttv__giphy_preview__error)) { vh =>
    isPreviewShown.onUi(_ => vh.foreach(ViewUtils.fadeOutView))
  }

  private lazy val recyclerView: ViewHolder[RecyclerView] = returning(view[RecyclerView](R.id.rv__giphy_image_preview)) { vh =>
    vh.foreach { v =>
      v.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL))
      v.setAdapter(giphyGridViewAdapter)
    }
    isPreviewShown.map(isPreview => if (isPreview) hideView else showView)
      .onUi(animate => vh.foreach(animate))
  }

  private lazy val closeButton = returning(view[View](R.id.gtv__giphy_preview__close_button)) { vh =>
    vh.onClick { _ => getControllerFactory.getGiphyController.cancel() }
  }

  private lazy val giphyGridViewAdapter = new GiphyGridViewAdapter(
    scrollGifCallback = new ScrollGifCallback {
      override def setSelectedGifFromGridView(gifAsset: AssetData): Unit = {
        isPreviewShown ! true
        selectedGif ! Some(gifAsset)
        keyboardController.hideKeyboardIfVisible()

        if (themeController.isDarkTheme) toolbar.setNavigationIcon(R.drawable.action_back_light)
        else toolbar.setNavigationIcon(R.drawable.action_back_dark)
      }
    },
    assetLoader = BitmapSignal.apply(zms.currentValue.get, _, _)
  )

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_giphy_preview, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    closeButton
    loadingIndicator
    giphySearchEditText

    Option(getArguments).orElse(Option(savedInstanceState)).foreach { bundle =>
      giphySearchEditText.foreach(_.setText(bundle.getString(ArgSearchTerm)))
    }

    val gifDrawable = new ImageAssetDrawable(gifImage, scaleType = ScaleType.CenterInside)
    previewImage.setImageDrawable(gifDrawable)
    gifDrawable.state
      .collect { case _ : State.Loaded | _ : State.Failed => () }
      .onUi(_ => loadingIndicator.foreach(_.hide()))


    giphySearchResults.onUi { results =>
      if (results.isEmpty) {
        errorView.setText(R.string.giphy_preview__error)
        TextViewUtils.mediumText(errorView)
        errorView.setVisibility(View.VISIBLE)
      } else {
        errorView.setVisibility(View.GONE)
        giphyGridViewAdapter.setGiphyResults(results)
      }
    }

    confirmationMenu.foreach { v =>
      v.setConfirmationMenuListener(new ConfirmationMenuListener() {
        override def confirm(): Unit = isOnline.head.filter(_ == true).foreach(_ => sendGif())
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

    errorView.setVisibility(View.GONE)
    previewImage.setVisibility(View.GONE)
    recyclerView.setVisibility(View.VISIBLE)
    giphyTitle.setVisibility(View.GONE)

    toolbar.foreach { v =>
      v.setNavigationIcon(
        if (ThemeUtils.isDarkTheme(getContext)) R.drawable.ic_action_search_light
        else R.drawable.ic_action_search_dark
      )

      v.setNavigationOnClickListener(new View.OnClickListener() {
        override def onClick(view: View): Unit = {
          isPreviewShown
          isPreviewShown ! false
          selectedGif ! None
        }
      })

    }
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

  private def sendGif() = {
    ZMessaging.getCurrentGlobal.trackingService.contribution(new ContributionEvent.Action("text")) //TODO use lazy val when in scala
    for {
      term <- searchTerm.head
      gif <- selectedGif.head
      msg =
        if (TextUtils.isEmpty(term)) getString(R.string.giphy_preview__message_via_random_trending)
        else getString(R.string.giphy_preview__message_via_search, term)
      _ <- conversationController.sendMessage(msg)
      _ <- conversationController.sendMessage(ImageAssetFactory.getImageAsset(gif.flatMap(_.source).get))
    } yield giphyController.close()
  }

}

object GiphySharingPreviewFragment {

  //TODO Move this to Signal class
  implicit class RichSignal[T](signal: Signal[T]) {
    def either[B](right: Signal[B]): Signal[Either[T, B]] =
      signal.map(Left(_): Either[T, B]).orElse(right.map(Right.apply))

    def pipeTo(sourceSignal: SourceSignal[T])(implicit ec: EventContext): Unit =
      signal.foreach(sourceSignal ! _)
  }

  implicit class RichEditText(et: EditText) {
    def afterTextChangedSignal(withInitialValue: Boolean = true): Signal[String] = new Signal[String]() {
      if (withInitialValue) publish(et.getText.toString)
      private val textWatcher = new TextWatcher {
        override def onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = ()
        override def afterTextChanged(editable: Editable): Unit = publish(editable.toString)
        override def beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = ()
      }

      override protected def onWire(): Unit = et.addTextChangedListener(textWatcher)
      override protected def onUnwire(): Unit = et.removeTextChangedListener(textWatcher)
    }
  }

  val Tag: String = classOf[GiphySharingPreviewFragment].getSimpleName
  val ArgSearchTerm = "SEARCH_TERM"
  val GiphySearchDelayMinSec = 800

  def newInstance: GiphySharingPreviewFragment = new GiphySharingPreviewFragment

  def newInstance(searchTerm: String): GiphySharingPreviewFragment =
    returning(new GiphySharingPreviewFragment) { fragment =>
      fragment.setArguments(returning(new Bundle)(_.putString(ArgSearchTerm, searchTerm)))
    }


  trait Container {}

  case class GifData(preview: Option[AssetData], gif: AssetData)

}
