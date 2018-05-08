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
package com.waz.zclient.views

import android.Manifest.permission.{CAMERA, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}
import android.content.{Context, DialogInterface, Intent}
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.Nullable
import android.support.v7.widget.{ActionMenuView, Toolbar}
import android.text.TextUtils
import android.text.format.Formatter
import android.view._
import android.view.animation.Animation
import android.widget.{AbsListView, FrameLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api._
import com.waz.api.impl.ContentUriAssetForUpload
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{MessageContent => _, _}
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventStreamWithAuxSignal, Signal}
import com.waz.utils.returningF
import com.waz.utils.wrappers.URI
import com.waz.zclient.Intents.ShowDevicesIntent
import com.waz.zclient.calling.controllers.{CallController, CallStartController}
import com.waz.zclient.camera.controllers.GlobalCameraController
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.controllers.accentcolor.AccentColorObserver
import com.waz.zclient.controllers.confirmation.{ConfirmationCallback, ConfirmationRequest, IConfirmationController}
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.giphy.GiphyObserver
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page, PagerControllerObserver}
import com.waz.zclient.controllers.orientation.OrientationControllerObserver
import com.waz.zclient.controllers.singleimage.SingleImageObserver
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.conversation.toolbar.AudioMessageRecordingView
import com.waz.zclient.cursor.{CursorCallback, CursorController, CursorView}
import com.waz.zclient.messages.{MessagesController, MessagesListView}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.pages.extendedcursor.emoji.EmojiKeyboardLayout
import com.waz.zclient.pages.extendedcursor.ephemeral.EphemeralLayout
import com.waz.zclient.pages.extendedcursor.image.{CursorImagesLayout, ImagePreviewLayout}
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterLayout
import com.waz.zclient.pages.main.conversation.{AssetIntentsManager, MessageStreamAnimation}
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
import com.waz.zclient.pages.main.conversationpager.controller.SlidingPaneObserver
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.fragments.SingleParticipantFragment
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.cursor.CursorMenuItem
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, SquareOrientation, ViewUtils}
import com.waz.zclient.views.e2ee.ShieldView
import com.waz.zclient.{ErrorsController, FragmentHelper, R}

import scala.concurrent.Future
import scala.concurrent.duration._

class ConversationFragment extends BaseFragment[ConversationFragment.Container] with FragmentHelper {
  import ConversationFragment._
  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]

  private lazy val convController         = inject[ConversationController]
  private lazy val messagesController     = inject[MessagesController]
  private lazy val collectionController   = inject[CollectionController]
  private lazy val permissions            = inject[PermissionsService]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val keyboardController     = inject[KeyboardController]
  private lazy val errorsController       = inject[ErrorsController]
  private lazy val callController         = inject[CallController]
  private lazy val callStartController    = inject[CallStartController]

  private val previewShown = Signal(false)
  private lazy val convChange = convController.convChanged.filter { _.to.isDefined }
  private lazy val cancelPreviewOnChange = new EventStreamWithAuxSignal(convChange, previewShown)

  private lazy val draftMap = inject[DraftMap]

  private var assetIntentsManager: Option[AssetIntentsManager] = None

  private var loadingIndicatorView: LoadingIndicatorView = _
  private var containerPreview: ViewGroup = _
  private var cursorView: CursorView = _
  private var audioMessageRecordingView: AudioMessageRecordingView = _
  private var extendedCursorContainer: ExtendedCursorContainer = _
  private var toolbarTitle: TextView = _
  private var listView: MessagesListView = _

  private var leftMenu: ActionMenuView = _
  private var toolbar: Toolbar = _

  private lazy val guestsBanner = view[FrameLayout](R.id.guests_banner)
  private lazy val guestsBannerText = view[TypefaceTextView](R.id.guests_banner_text)

  private var openBanner = false

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0 || Option(getContainer).isEmpty || getControllerFactory.isTornDown) super.onCreateAnimation(transit, enter, nextAnim)
    else if (nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_in ||
             nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_out) new MessageStreamAnimation(
      enter,
      getInt(R.integer.wire__animation__duration__medium),
      0,
      getOrientationDependentDisplayWidth - getResources.getDimensionPixelSize(R.dimen.framework__sidebar_width)
    )
    else if (enter) new ConversationListAnimation(
      0,
      getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_long),
      getInt(R.integer.framework_animation_duration_medium),
      false,
      1f
    )
    else new ConversationListAnimation(
      0,
      getResources.getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_medium),
      0,
      false,
      1f
    )
  }

  override def onCreate(@Nullable savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    assetIntentsManager = Option(new AssetIntentsManager(getActivity, assetIntentsManagerCallback, savedInstanceState))

    participantsController.containsGuest.onUi {
      case true => openGuestsBanner()
      case false =>  hideGuestsBanner()
    }

    keyboardController.isKeyboardVisible.onUi(visible => if(visible) collapseGuestsBanner())
  }

  private def openGuestsBanner(): Unit = {
    if (!openBanner) {
      openBanner = true
      guestsBanner.foreach { banner =>
        banner.setVisibility(View.VISIBLE)
        banner.setPivotY(0.0f)
        banner.setScaleY(1.0f)
      }
      guestsBannerText.foreach(_.setAlpha(1.0f))
    }
  }

  private def collapseGuestsBanner(): Unit = {
    if (openBanner) {
      openBanner = false
      guestsBanner.foreach { banner =>
        banner.setPivotY(0.0f)
        banner.animate().scaleY(0.1f).start()
      }
      guestsBannerText.foreach(_.animate().alpha(0.0f).start())
    }
  }

  private def hideGuestsBanner(): Unit = {
    openBanner = false
    guestsBanner.foreach(_.setVisibility(View.GONE))
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_conversation, viewGroup, false)
  }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    if (savedInstanceState != null) previewShown ! savedInstanceState.getBoolean(SAVED_STATE_PREVIEW, false)
    zms.flatMap(_.errors.getErrors).onUi { _.foreach(handleSyncError) }

    implicit val ctx: Context = getActivity

    findById(R.id.tiv_typing_indicator_view)

    loadingIndicatorView = findById(R.id.lbv__conversation__loading_indicator)
    containerPreview = findById(R.id.fl__conversation_overlay)
    cursorView = findById(R.id.cv__cursor)

    extendedCursorContainer = findById(R.id.ecc__conversation)
    listView = findById(R.id.messages_list_view)

    returningF( findById(R.id.sv__conversation_toolbar__verified_shield) ){ view: ShieldView =>
      view.setVisible(false)
    }

    // Recording audio messages
    audioMessageRecordingView = findById[AudioMessageRecordingView](R.id.amrv_audio_message_recording)

    // invisible footer to scroll over inputfield
    returningF( new FrameLayout(getActivity) ){ footer: FrameLayout =>
      footer.setLayoutParams(
        new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources.getDimensionPixelSize(R.dimen.cursor__list_view_footer__height))
      )
    }

    leftMenu = findById(R.id.conversation_left_menu)
    toolbar = findById(R.id.t_conversation_toolbar)
    toolbarTitle = ViewUtils.getView(toolbar, R.id.tv__conversation_toolbar__title).asInstanceOf[TextView]
    convController.currentConvName.onUi { updateTitle }


    cancelPreviewOnChange.onUi {
      case (change, Some(true)) if !change.noChange => imagePreviewCallback.onCancelPreview()
      case _ =>
    }

    convController.currentConv.onUi {
      case conv if conv.isActive =>
        inflateCollectionIcon()
        callController.currentCallOpt.head.flatMap {
          case Some(call) if call.convId == conv.id => Future.successful(None)
          case _ => convController.hasOtherParticipants(conv.id).flatMap {
            case true => convController.isGroup(conv.id).map(isGroup => Some(if (isGroup) R.menu.conversation_header_menu_audio else R.menu.conversation_header_menu_video))
            case false => Future.successful(None)
          }
        }.foreach { id =>
          toolbar.getMenu.clear()
          id.foreach(toolbar.inflateMenu)
        }(Threading.Ui)
      case _ => toolbar.getMenu.clear()
    }

    convChange.onUi {
      case ConversationChange(from, Some(to), requester) =>
        CancellableFuture.delay(getResources.getInteger(R.integer.framework_animation_duration_short).millis).map { _ =>
          convController.loadConv(to).map {
            case Some(toConv) =>
              from.foreach{ id => draftMap.set(id, cursorView.getText.trim) }
              if (toConv.convType != ConversationType.WaitForConnection) updateConv(from, toConv)
            case None =>
          }
        }

      case _ =>
    }
  }

  override def onStart(): Unit = {
    super.onStart()

    toolbar.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        participantsController.onShowParticipants ! None
      }
    })

    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      override def onMenuItemClick(item: MenuItem): Boolean =
        item.getItemId match {
          case R.id.action_audio_call | R.id.action_video_call =>
            callStartController.startCallInCurrentConv(withVideo = item.getItemId == R.id.action_video_call)
            cursorView.closeEditMessage(false)
            true
          case _ => false
      }
    })

    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        cursorView.closeEditMessage(false)
        getActivity.onBackPressed()
        KeyboardUtils.closeKeyboardIfShown(getActivity)
      }
    })

    leftMenu.setOnMenuItemClickListener(new ActionMenuView.OnMenuItemClickListener() {
      override def onMenuItemClick(item: MenuItem): Boolean = item.getItemId match {
        case R.id.action_collection =>
          collectionController.openCollection()
          true
        case _ => false
      }
    })

    getControllerFactory.getGlobalLayoutController.addKeyboardHeightObserver(extendedCursorContainer)
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(extendedCursorContainer)
    extendedCursorContainer.setCallback(extendedCursorContainerCallback)

    getControllerFactory.getOrientationController.addOrientationControllerObserver(orientationControllerObserver)
    cursorView.setCallback(cursorCallback)

    draftMap.withCurrentDraft { draftText => if (!TextUtils.isEmpty(draftText)) cursorView.setText(draftText) }

    getControllerFactory.getNavigationController.addNavigationControllerObserver(navigationControllerObserver)
    getControllerFactory.getNavigationController.addPagerControllerObserver(pagerControllerObserver)
    getControllerFactory.getGiphyController.addObserver(giphyObserver)
    getControllerFactory.getSingleImageController.addSingleImageObserver(singleImageObserver)
    getControllerFactory.getAccentColorController.addAccentColorObserver(accentColorObserver)
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(keyboardVisibilityObserver)
    getControllerFactory.getSlidingPaneController.addObserver(slidingPaneObserver)
  }

  private def updateTitle(text: String): Unit = if (toolbarTitle != null) toolbarTitle.setText(text)

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    assetIntentsManager.foreach { _.onSaveInstanceState(outState) }
    previewShown.head.foreach { isShown => outState.putBoolean(SAVED_STATE_PREVIEW, isShown) }
  }

  override def onPause(): Unit = {
    super.onPause()
    KeyboardUtils.hideKeyboard(getActivity)
    audioMessageRecordingView.hide()
  }

  override def onStop(): Unit = {
    extendedCursorContainer.close(true)
    extendedCursorContainer.setCallback(null)
    cursorView.setCallback(null)

    toolbar.setOnClickListener(null)
    toolbar.setOnMenuItemClickListener(null)
    toolbar.setNavigationOnClickListener(null)
    leftMenu.setOnMenuItemClickListener(null)

    getControllerFactory.getGlobalLayoutController.removeKeyboardHeightObserver(extendedCursorContainer)
    getControllerFactory.getGlobalLayoutController.removeKeyboardVisibilityObserver(extendedCursorContainer)

    getControllerFactory.getOrientationController.removeOrientationControllerObserver(orientationControllerObserver)
    getControllerFactory.getGiphyController.removeObserver(giphyObserver)
    getControllerFactory.getSingleImageController.removeSingleImageObserver(singleImageObserver)

    if (!cursorView.isEditingMessage) draftMap.setCurrent(cursorView.getText.trim)

    getControllerFactory.getGlobalLayoutController.removeKeyboardVisibilityObserver(keyboardVisibilityObserver)
    getControllerFactory.getAccentColorController.removeAccentColorObserver(accentColorObserver)

    getControllerFactory.getSlidingPaneController.removeObserver(slidingPaneObserver)

    getControllerFactory.getNavigationController.removePagerControllerObserver(pagerControllerObserver)
    getControllerFactory.getNavigationController.removeNavigationControllerObserver(navigationControllerObserver)

    super.onStop()
  }

  private def updateConv(fromId: Option[ConvId], toConv: ConversationData): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    loadingIndicatorView.hide()
    cursorView.enableMessageWriting()

    fromId.filter(_ != toConv.id).foreach { id =>

      cursorView.setVisible(toConv.isActive)
      draftMap.get(toConv.id).map { draftText =>
        cursorView.setText(draftText)
        cursorView.setConversation()
      }
      audioMessageRecordingView.hide()
    }
    // TODO: ConversationScreenController should listen to this signal and do it itself
    extendedCursorContainer.close(true)
  }

  private def inflateCollectionIcon(): Unit = {
    leftMenu.getMenu.clear()

    val searchInProgress = collectionController.contentSearchQuery.currentValue("").get.originalString.nonEmpty

    getActivity.getMenuInflater.inflate(
      if (searchInProgress) R.menu.conversation_header_menu_collection_searching
      else R.menu.conversation_header_menu_collection,
      leftMenu.getMenu
    )
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit =
    assetIntentsManager.foreach { _.onActivityResult(requestCode, resultCode, data) }

  private lazy val imagePreviewCallback = new ImagePreviewLayout.Callback {
    override def onCancelPreview(): Unit = {
      previewShown ! false
      getControllerFactory.getNavigationController.setPagerEnabled(true)
      containerPreview
        .animate
        .translationY(getView.getMeasuredHeight)
        .setDuration(getResources.getInteger(R.integer.animation_duration_medium))
        .setInterpolator(new Expo.EaseIn)
        .withEndAction(new Runnable() {
          override def run(): Unit = if (containerPreview != null) containerPreview.removeAllViews()
        })
    }

    override def onSketchOnPreviewPicture(imageAsset: ImageAsset, source: ImagePreviewLayout.Source, method: IDrawingController.DrawingMethod): Unit = {
      getControllerFactory.getDrawingController.showDrawing(imageAsset, IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW, method)
      extendedCursorContainer.close(true)
    }

    override def onSendPictureFromPreview(imageAsset: ImageAsset, source: ImagePreviewLayout.Source): Unit = imageAsset match {
      case a: com.waz.api.impl.ImageAsset => convController.currentConv.head.map { conv =>
        convController.sendMessage(conv.id, a)
        extendedCursorContainer.close(true)
        onCancelPreview()
      }
      case _ =>
    }

  }

  private def errorHandler = new MessageContent.Asset.ErrorHandler() {
    override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = answer.ok()
  }

  private def sendVideo(uri: URI): Unit = {
    convController.sendMessage(ContentUriAssetForUpload(AssetId(), uri), assetErrorHandlerVideo)
    getControllerFactory.getNavigationController.setRightPage(Page.MESSAGE_STREAM, TAG)
    extendedCursorContainer.close(true)
  }

  private def sendImage(uri: URI): Unit = ImageAssetFactory.getImageAsset(uri) match {
    case a: com.waz.api.impl.ImageAsset => convController.sendMessage(a)
    case _ =>
  }

  private val assetErrorHandler = new MessageContent.Asset.ErrorHandler() {
    override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = Option(getActivity) match {
      case None => answer.ok()
      case Some(activity) =>
        val dialog = ViewUtils.showAlertDialog(
          activity,
          R.string.asset_upload_warning__large_file__title,
          R.string.asset_upload_warning__large_file__message_default,
          R.string.asset_upload_warning__large_file__button_accept,
          R.string.asset_upload_warning__large_file__button_cancel,
          new DialogInterface.OnClickListener() { override def onClick(dialog: DialogInterface, which: Int): Unit = answer.ok() },
          new DialogInterface.OnClickListener() { override def onClick(dialog: DialogInterface, which: Int): Unit = answer.cancel() }
        )
        dialog.setCancelable(false)
        if (sizeInBytes > 0)
          dialog.setMessage(getString(R.string.asset_upload_warning__large_file__message, Formatter.formatFileSize(getContext, sizeInBytes)))
    }
  }

  private val assetErrorHandlerVideo = new MessageContent.Asset.ErrorHandler() {
    override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = Option(getActivity) match {
      case None => answer.ok()
      case Some(activity) =>
        val dialog = ViewUtils.showAlertDialog(
          activity,
          R.string.asset_upload_warning__large_file__title,
          R.string.asset_upload_warning__large_file__message_default,
          R.string.asset_upload_warning__large_file__button_accept,
          R.string.asset_upload_warning__large_file__button_cancel,
          new DialogInterface.OnClickListener() { override def onClick(dialog: DialogInterface, which: Int): Unit = answer.ok() },
          new DialogInterface.OnClickListener() { override def onClick(dialog: DialogInterface, which: Int): Unit = answer.cancel() }
      )
      dialog.setCancelable(false)
      if (sizeInBytes > 0) dialog.setMessage(getString(R.string.asset_upload_warning__large_file__message__video))
    }
  }

  private val assetIntentsManagerCallback = new AssetIntentsManager.Callback {
    override def onDataReceived(intentType: AssetIntentsManager.IntentType, uri: URI): Unit = intentType match {
      case AssetIntentsManager.IntentType.FILE_SHARING =>
        permissions.requestAllPermissions(Set(READ_EXTERNAL_STORAGE)).map {
          case true =>
            convController.sendMessage(ContentUriAssetForUpload(AssetId(), uri), assetErrorHandler)
          case _ =>
            ViewUtils.showAlertDialog(
              getActivity,
              R.string.asset_upload_error__not_found__title,
              R.string.asset_upload_error__not_found__message,
              R.string.asset_upload_error__not_found__button,
              null,
              true
            )
        }
      case AssetIntentsManager.IntentType.GALLERY =>
        showImagePreview(ImageAssetFactory.getImageAsset(uri), ImagePreviewLayout.Source.DEVICE_GALLERY)
      case AssetIntentsManager.IntentType.VIDEO =>
        sendVideo(uri)
      case AssetIntentsManager.IntentType.CAMERA =>
        sendImage(uri)
        extendedCursorContainer.close(true)
    }

    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = {
      if (MediaStore.ACTION_VIDEO_CAPTURE.equals(intent.getAction) &&
        extendedCursorContainer.getType == ExtendedCursorContainer.Type.IMAGES &&
        extendedCursorContainer.isExpanded) {
        // Close keyboard camera before requesting external camera for recording video
        extendedCursorContainer.close(true)
      }
      startActivityForResult(intent, intentType.requestCode)
      getActivity.overridePendingTransition(R.anim.camera_in, R.anim.camera_out)
    }

    override def onFailed(tpe: AssetIntentsManager.IntentType): Unit = {}

    override def onCanceled(tpe: AssetIntentsManager.IntentType): Unit = {}
  }

  private val extendedCursorContainerCallback = new ExtendedCursorContainer.Callback {
    override def onExtendedCursorClosed(lastType: ExtendedCursorContainer.Type): Unit = {
      cursorView.onExtendedCursorClosed()

      if (lastType == ExtendedCursorContainer.Type.EPHEMERAL)
        convController.currentConv.head.map { conv =>
          if (conv.ephemeral != EphemeralExpiration.NONE)
            getControllerFactory.getUserPreferencesController.setLastEphemeralValue(conv.ephemeral.milliseconds)
        }

      getControllerFactory.getGlobalLayoutController.resetScreenAwakeState()
    }
  }

  private def openExtendedCursor(cursorType: ExtendedCursorContainer.Type): Unit = cursorType match {
      case ExtendedCursorContainer.Type.NONE =>
      case ExtendedCursorContainer.Type.EMOJIS =>
        extendedCursorContainer.openEmojis(getControllerFactory.getUserPreferencesController.getRecentEmojis, getControllerFactory.getUserPreferencesController.getUnsupportedEmojis, emojiKeyboardLayoutCallback)
      case ExtendedCursorContainer.Type.EPHEMERAL => convController.currentConv.head.map { conv =>
        extendedCursorContainer.openEphemeral(ephemeralLayoutCallback, conv.ephemeral)
      }
      case ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING =>
        extendedCursorContainer.openVoiceFilter(voiceFilterLayoutCallback)
      case ExtendedCursorContainer.Type.IMAGES =>
        extendedCursorContainer.openCursorImages(cursorImageLayoutCallback)
      case _ =>
        verbose(s"openExtendedCursor(unknown)")
    }


  private val emojiKeyboardLayoutCallback = new EmojiKeyboardLayout.Callback {
    override def onEmojiSelected(emoji: LogTag) = {
      cursorView.insertText(emoji)
      getControllerFactory.getUserPreferencesController.addRecentEmoji(emoji)
    }
  }

  private val ephemeralLayoutCallback = new EphemeralLayout.Callback {
    override def onEphemeralExpirationSelected(expiration: EphemeralExpiration, close: Boolean) = {
      if (close) extendedCursorContainer.close(false)
      convController.setEphemeralExpiration(expiration)
    }
  }

  private val voiceFilterLayoutCallback = new VoiceFilterLayout.Callback {
    override def onAudioMessageRecordingStarted(): Unit = {
      getControllerFactory.getGlobalLayoutController.keepScreenAwake()
    }

    override def onCancel(): Unit = extendedCursorContainer.close(false)

    override def sendRecording(audioAssetForUpload: AudioAssetForUpload, appliedAudioEffect: AudioEffect): Unit = {
      audioAssetForUpload match {
        case a: com.waz.api.impl.AudioAssetForUpload => convController.sendMessage(a, errorHandler)
        case _ =>
      }

      extendedCursorContainer.close(true)
    }
  }

  private val cursorImageLayoutCallback = new CursorImagesLayout.Callback {
    override def openCamera(): Unit = getControllerFactory.getCameraController.openCamera(CameraContext.MESSAGE)

    override def openVideo(): Unit = captureVideoAskPermissions()

    override def onGalleryPictureSelected(asset: ImageAsset): Unit = {
      previewShown ! true
      showImagePreview(asset, ImagePreviewLayout.Source.IN_APP_GALLERY)
    }

    override def openGallery(): Unit = assetIntentsManager.foreach { _.openGallery() }

    override def onPictureTaken(imageAsset: ImageAsset): Unit = showImagePreview(imageAsset, ImagePreviewLayout.Source.CAMERA)
  }

  private def captureVideoAskPermissions() = for {
    _ <- inject[GlobalCameraController].releaseCamera() //release camera so the camera app can use it
    _ <- permissions.requestAllPermissions(Set(CAMERA, WRITE_EXTERNAL_STORAGE)).map {
      case true => assetIntentsManager.foreach(_.captureVideo(getContext.getApplicationContext))
      case false => //
    }(Threading.Ui)
  } yield {}

  private lazy val inLandscape = Signal(Option.empty[Boolean])

  private val orientationControllerObserver = new  OrientationControllerObserver {
    override def onOrientationHasChanged(squareOrientation: SquareOrientation): Unit = inLandscape.head.foreach { oldInLandscape =>
      val newInLandscape = isInLandscape
      oldInLandscape match {
        case Some(landscape) if landscape != newInLandscape =>
          val conversationListVisible = getControllerFactory.getNavigationController.getCurrentPage == Page.CONVERSATION_LIST
          if (newInLandscape && !conversationListVisible)
            CancellableFuture.delayed(getInt(R.integer.framework_animation_duration_short).millis){
              Option(getActivity).foreach(_.onBackPressed())
            }
        case _ =>
      }
      inLandscape ! Some(newInLandscape)
    }
  }

  private val cursorCallback = new CursorCallback {
    override def onMotionEventFromCursorButton(cursorMenuItem: CursorMenuItem, motionEvent: MotionEvent): Unit =
      if (cursorMenuItem == CursorMenuItem.AUDIO_MESSAGE && audioMessageRecordingView.isVisible)
        audioMessageRecordingView.onMotionEventFromAudioMessageButton(motionEvent)

    override def captureVideo(): Unit = captureVideoAskPermissions()

    override def hideExtendedCursor(): Unit = if (extendedCursorContainer.isExpanded) extendedCursorContainer.close(false)

    override def onMessageSent(msg: MessageData): Unit = listView.scrollToBottom()

    override def openExtendedCursor(tpe: ExtendedCursorContainer.Type): Unit = ConversationFragment.this.openExtendedCursor(tpe)

    override def onCursorClicked(): Unit = if (!cursorView.isEditingMessage && listView.scrollController.targetPosition.isEmpty) listView.scrollToBottom()

    override def openFileSharing(): Unit = assetIntentsManager.foreach { _.openFileSharing() }

    override def onCursorButtonLongPressed(cursorMenuItem: CursorMenuItem): Unit =
      cursorMenuItem match {
        case CursorMenuItem.AUDIO_MESSAGE =>
          callController.isCallActive.head.foreach {
            case true  => showErrorDialog(R.string.calling_ongoing_call_title, R.string.calling_ongoing_call_audio_message)
            case false =>
              extendedCursorContainer.close(true)
              audioMessageRecordingView.show()
          }
        case _ => //
      }
  }

  private val navigationControllerObserver = new NavigationControllerObserver {
    override def onPageVisible(page: Page): Unit = if (page == Page.MESSAGE_STREAM) {
      participantsController.containsGuest.currentValue.foreach {
        case true => openGuestsBanner()
        case false =>  hideGuestsBanner()
      }
      inflateCollectionIcon()
      cursorView.enableMessageWriting()
    }
  }

  private val pagerControllerObserver = new PagerControllerObserver {
    override def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int): Unit =
      if (positionOffset > 0) extendedCursorContainer.close(true)

    override def onPagerEnabledStateHasChanged(enabled: Boolean): Unit = {}
    override def onPageSelected(position: Int): Unit = {}
    override def onPageScrollStateChanged(state: Int): Unit = {}
  }

  private val giphyObserver = new GiphyObserver {
    override def onSearch(keyword: LogTag): Unit = {}
    override def onRandomSearch(): Unit = {}
    override def onTrendingSearch(): Unit = {}
    override def onCloseGiphy(): Unit = cursorView.setText("")
    override def onCancelGiphy(): Unit = {}
  }

  private val singleImageObserver = new SingleImageObserver {
    override def onShowSingleImage(messageId: String): Unit = {}
    override def onHideSingleImage(): Unit = getControllerFactory.getNavigationController.setRightPage(Page.MESSAGE_STREAM, TAG)
  }

  private val accentColorObserver = new AccentColorObserver {
    override def onAccentColorHasChanged(color: Int): Unit = {
      loadingIndicatorView.setColor(color)
      extendedCursorContainer.setAccentColor(color)
    }
  }

  private val keyboardVisibilityObserver = new KeyboardVisibilityObserver {
    override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit =
      inject[CursorController].notifyKeyboardVisibilityChanged(keyboardIsVisible)
  }

  private def handleSyncError(err: ErrorData): Unit = err.errType match {
    case ErrorType.CANNOT_SEND_ASSET_FILE_NOT_FOUND =>
      ViewUtils.showAlertDialog(
        getActivity,
        R.string.asset_upload_error__not_found__title,
        R.string.asset_upload_error__not_found__message,
        R.string.asset_upload_error__not_found__button,
        null,
        true
      )
      errorsController.dismissSyncError(err.id)
    case ErrorType.CANNOT_SEND_ASSET_TOO_LARGE =>
      val maxAllowedSizeInBytes = AssetFactory.getMaxAllowedAssetSizeInBytes
      if (maxAllowedSizeInBytes > 0) {
        val dialog = ViewUtils.showAlertDialog(
          getActivity,
          R.string.asset_upload_error__file_too_large__title,
          R.string.asset_upload_error__file_too_large__message_default,
          R.string.asset_upload_error__file_too_large__button,
          null,
          true
        )
        dialog.setMessage(getString(R.string.asset_upload_error__file_too_large__message, Formatter.formatShortFileSize(getContext, maxAllowedSizeInBytes)))
      }
      errorsController.dismissSyncError(err.id)
    case ErrorType.RECORDING_FAILURE =>
      ViewUtils.showAlertDialog(
        getActivity,
        R.string.audio_message__recording__failure__title,
        R.string.audio_message__recording__failure__message,
        R.string.alert_dialog__confirmation,
        null,
        true
      )
      errorsController.dismissSyncError(err.id)
    case ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION =>
      err.convId.foreach(onErrorCanNotSentMessageToUnverifiedConversation(err, _))
    case errType =>
      error(s"Unhandled onSyncError: $errType")
  }

  private def onErrorCanNotSentMessageToUnverifiedConversation(err: ErrorData, convId: ConvId) =
    if (getControllerFactory.getNavigationController.getCurrentPage == Page.MESSAGE_STREAM) {
      KeyboardUtils.hideKeyboard(getActivity)

      (for {
        self <- inject[UserAccountsController].currentUser.head
        members <- convController.loadMembers(convId)
        unverifiedUsers = (members ++ self.map(Seq(_)).getOrElse(Nil)).filter { !_.isVerified }
        unverifiedDevices <-
          if (unverifiedUsers.size == 1) Future.sequence(unverifiedUsers.map(u => convController.loadClients(u.id).map(_.filter(!_.isVerified)))).map(_.flatten.size)
          else Future.successful(0) // in other cases we don't need this number
      } yield (self, unverifiedUsers, unverifiedDevices)).map { case (self, unverifiedUsers, unverifiedDevices) =>

        val unverifiedNames = unverifiedUsers.map { u =>
          if (self.map(_.id).contains(u.id)) getString(R.string.conversation_degraded_confirmation__header__you)
          else u.displayName
        }

        val header =
          if (unverifiedUsers.isEmpty) getResources.getString(R.string.conversation__degraded_confirmation__header__someone)
          else if (unverifiedUsers.size == 1)
            getResources.getQuantityString(R.plurals.conversation__degraded_confirmation__header__single_user, unverifiedDevices, unverifiedNames.head)
          else getString(R.string.conversation__degraded_confirmation__header__multiple_user, unverifiedNames.mkString(","))

        val onlySelfChanged = unverifiedUsers.size == 1 && self.map(_.id).contains(unverifiedUsers.head.id)

        val callback = new ConfirmationCallback {
          override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = {
            messagesController.retryMessageSending(err.messages)
            errorsController.dismissSyncError(err.id)
          }

          override def onHideAnimationEnd(confirmed: Boolean, cancelled: Boolean, checkboxIsSelected: Boolean): Unit =
            if (!confirmed && !cancelled) {
              if (onlySelfChanged) getContext.startActivity(ShowDevicesIntent(getActivity))
              else participantsController.onShowParticipants ! Some(SingleParticipantFragment.TagDevices)
            }

          override def negativeButtonClicked(): Unit = {}

          override def canceled(): Unit = {}
        }

        val positiveButton = getString(R.string.conversation__degraded_confirmation__positive_action)
        val negativeButton =
          if (onlySelfChanged) getString(R.string.conversation__degraded_confirmation__negative_action_self)
          else getResources.getQuantityString(R.plurals.conversation__degraded_confirmation__negative_action, unverifiedUsers.size)

        val messageCount = Math.max(1, err.messages.size)
        val message = getResources.getQuantityString(R.plurals.conversation__degraded_confirmation__message, messageCount)

        val request =
          new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(message)
            .withPositiveButton(positiveButton)
            .withNegativeButton(negativeButton)
            .withConfirmationCallback(callback)
            .withCancelButton()
            .withBackgroundImage(R.drawable.degradation_overlay)
            .withWireTheme(inject[ThemeController].getThemeDependentOptionsTheme)
            .build

        getControllerFactory.getConfirmationController.requestConfirmation(request, IConfirmationController.CONVERSATION)
      }

    }

  private val slidingPaneObserver = new SlidingPaneObserver {
    override def onPanelSlide(panel: View, slideOffset: Float): Unit = {}
    override def onPanelOpened(panel: View): Unit = KeyboardUtils.closeKeyboardIfShown(getActivity)
    override def onPanelClosed(panel: View): Unit = {}
  }

  private def showImagePreview(asset: ImageAsset, source: ImagePreviewLayout.Source): Unit = {
    val imagePreviewLayout = LayoutInflater.from(getContext).inflate(R.layout.fragment_cursor_images_preview, containerPreview, false).asInstanceOf[ImagePreviewLayout]
    imagePreviewLayout.setImageAsset(asset, source, imagePreviewCallback)
    imagePreviewLayout.setAccentColor(getControllerFactory.getAccentColorController.getAccentColor.getColor)
    convController.currentConv.head.map { conv => imagePreviewLayout.setTitle(conv.displayName) }
    containerPreview.addView(imagePreviewLayout)
    openPreview(containerPreview)
  }

  private def openPreview(containerPreview: View): Unit = {
    previewShown ! true
    getControllerFactory.getNavigationController.setPagerEnabled(false)
    containerPreview.setTranslationY(getView.getMeasuredHeight)
    containerPreview.animate.translationY(0).setDuration(getResources.getInteger(R.integer.animation_duration_medium)).setInterpolator(new Expo.EaseOut)
  }
}

object ConversationFragment {
  val TAG = ConversationFragment.getClass.getName
  val SAVED_STATE_PREVIEW = "SAVED_STATE_PREVIEW"
  val REQUEST_VIDEO_CAPTURE = 911

  def newInstance() = new ConversationFragment

  trait Container {

  }
}
