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
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.Nullable
import android.support.v7.widget.{ActionMenuView, LinearLayoutManager, RecyclerView, Toolbar}
import android.text.TextUtils
import android.view._
import android.view.animation.Animation
import android.widget.{AbsListView, FrameLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.{AudioAssetForUpload, AudioEffect, ErrorType}
import com.waz.content.GlobalPreferences
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{AccentColor, MessageContent => _, _}
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.call.CallingService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventStreamWithAuxSignal, Signal}
import com.waz.utils.wrappers.URI
import com.waz.utils.{returning, returningF}
import com.waz.zclient.Intents.ShowDevicesIntent
import com.waz.zclient.calling.controllers.{CallController, CallStartController}
import com.waz.zclient.camera.controllers.GlobalCameraController
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.controllers.{ScreenController, ThemeController, UserAccountsController}
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.{ConfirmationCallback, ConfirmationRequest, IConfirmationController}
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.globallayout.{IGlobalLayoutController, KeyboardVisibilityObserver}
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationControllerObserver, Page, PagerControllerObserver}
import com.waz.zclient.controllers.singleimage.{ISingleImageController, SingleImageObserver}
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.{ConversationController, ReplyContent, ReplyController, ReplyView}
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.conversation.toolbar.AudioMessageRecordingView
import com.waz.zclient.cursor._
import com.waz.zclient.drawing.DrawingFragment.Sketch
import com.waz.zclient.messages.{MessagesController, MessagesListView}
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.pages.extendedcursor.emoji.EmojiKeyboardLayout
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterLayout
import com.waz.zclient.pages.main.conversation.{AssetIntentsManager, MessageStreamAnimation}
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
import com.waz.zclient.pages.main.conversationpager.controller.{ISlidingPaneController, SlidingPaneObserver}
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.pages.main.{ImagePreviewCallback, ImagePreviewLayout}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.fragments.SingleParticipantFragment
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.cursor.CursorMenuItem
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.views.e2ee.ShieldView
import com.waz.zclient.{ErrorsController, FragmentHelper, R}

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._

class ConversationFragment extends FragmentHelper {
  import ConversationFragment._
  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]

  private lazy val convController         = inject[ConversationController]
  private lazy val messagesController     = inject[MessagesController]
  private lazy val screenController       = inject[ScreenController]
  private lazy val collectionController   = inject[CollectionController]
  private lazy val permissions            = inject[PermissionsService]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val keyboardController     = inject[KeyboardController]
  private lazy val errorsController       = inject[ErrorsController]
  private lazy val callController         = inject[CallController]
  private lazy val callStartController    = inject[CallStartController]
  private lazy val accountsController     = inject[UserAccountsController]
  private lazy val globalPrefs            = inject[GlobalPreferences]
  private lazy val replyController        = inject[ReplyController]

  //TODO remove use of old java controllers
  private lazy val globalLayoutController     = inject[IGlobalLayoutController]
  private lazy val navigationController       = inject[INavigationController]
  private lazy val singleImageController      = inject[ISingleImageController]
  private lazy val slidingPaneController      = inject[ISlidingPaneController]
  private lazy val userPreferencesController  = inject[IUserPreferencesController]
  private lazy val cameraController           = inject[ICameraController]
  private lazy val confirmationController     = inject[IConfirmationController]

  private var subs = Set.empty[com.waz.utils.events.Subscription]

  private val previewShown = Signal(false)
  private lazy val convChange = convController.convChanged.filter { _.to.isDefined }
  private lazy val cancelPreviewOnChange = new EventStreamWithAuxSignal(convChange, previewShown)

  private lazy val draftMap = inject[DraftMap]

  private var assetIntentsManager: Option[AssetIntentsManager] = None

  private lazy val loadingIndicatorView = returning(view[LoadingIndicatorView](R.id.lbv__conversation__loading_indicator)) { vh =>
    inject[Signal[AccentColor]].map(_.color)(c => vh.foreach(_.setColor(c)))
  }

  private var containerPreview: ViewGroup = _
  private lazy val cursorView = returning(view[CursorView](R.id.cv__cursor)) { vh =>
    mentionCandidatesAdapter.onUserClicked.onUi { info =>
      vh.foreach(v => v.accentColor.head.foreach { ac =>
        v.createMention(info.id, info.name, v.cursorEditText, v.cursorEditText.getSelectionStart, ac.color)
      })
    }
  }

  private val mentionCandidatesAdapter = new MentionCandidatesAdapter()

  private var audioMessageRecordingView: AudioMessageRecordingView = _
  private lazy val extendedCursorContainer = returning(view[ExtendedCursorContainer](R.id.ecc__conversation)) { vh =>
    inject[Signal[AccentColor]].map(_.color).onUi(c => vh.foreach(_.setAccentColor(c)))
  }
  private var toolbarTitle: TextView = _
  private lazy val listView = view[MessagesListView](R.id.messages_list_view)

  private var leftMenu: ActionMenuView = _
  private var toolbar: Toolbar = _

  private lazy val guestsBanner = view[FrameLayout](R.id.guests_banner)
  private lazy val guestsBannerText = view[TypefaceTextView](R.id.banner_text)

  private var isBannerOpen = false

  private lazy val messagesOpacity = view[View](R.id.mentions_opacity)
  private lazy val mentionsList = view[RecyclerView](R.id.mentions_list)
  private lazy val replyView = view[ReplyView](R.id.reply_view)

  private def isUserGuest(user: UserData): Signal[Boolean] =
    convController.currentConv.map { conv =>
      if (!conv.isTeamOnly) user.isGuest(conv.team) else false
    }

  private def showMentionsList(visible: Boolean): Unit = {
    mentionsList.foreach(_.setVisible(visible))
    messagesOpacity.foreach(_.setVisible(visible))
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    if (nextAnim == 0 || getParentFragment == null)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_in ||
             nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_out) new MessageStreamAnimation(
      enter,
      getInt(R.integer.wire__animation__duration__medium),
      0,
      getOrientationDependentDisplayWidth - getDimenPx(R.dimen.framework__sidebar_width)
    )
    else if (enter) new ConversationListAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_long),
      getInt(R.integer.framework_animation_duration_medium),
      false,
      1f
    )
    else new ConversationListAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_medium),
      0,
      false,
      1f
    )

  override def onCreate(@Nullable savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    assetIntentsManager = Option(new AssetIntentsManager(getActivity, assetIntentsManagerCallback, savedInstanceState))

    zms.flatMap(_.errors.getErrors).onUi { _.foreach(handleSyncError) }

    convController.currentConvName.onUi { updateTitle }

    cancelPreviewOnChange.onUi {
      case (change, Some(true)) if !change.noChange => imagePreviewCallback.onCancelPreview()
      case _ =>
    }

    (for {
      (convId, isConvActive) <- convController.currentConv.map(c => (c.id, c.isActive))
      isGroup                <- convController.groupConversation(convId)
      participantsNumber     <- Signal.future(convController.participantsIds(convId).map(_.size))
      acc                    <- zms.map(_.selfUserId)
      call                   <- callController.currentCallOpt
      isCallActive           = call.exists(_.convId == convId) && call.exists(_.account == acc)
      isTeam                 <- accountsController.isTeam
    } yield {
      if (isCallActive || !isConvActive || participantsNumber <= 1)
        Option.empty[Int]
      else if (!isGroup || (isTeam && participantsNumber <= CallingService.VideoCallMaxMembers))
        Some(R.menu.conversation_header_menu_video)
      else
        Some(R.menu.conversation_header_menu_audio)
    }).onUi { id =>
      toolbar.getMenu.clear()
      id.foreach(toolbar.inflateMenu)
    }

    convChange.onUi {
      case ConversationChange(from, Some(to), _) =>
        CancellableFuture.delay(getInt(R.integer.framework_animation_duration_short).millis).map { _ =>
          convController.getConversation(to).map {
            case Some(toConv) =>
              cursorView.foreach { view =>
                from.foreach{ id => draftMap.set(id, view.getText) }
                if (toConv.convType != ConversationType.WaitForConnection) {
                  keyboardController.hideKeyboardIfVisible()
                  loadingIndicatorView.foreach(_.hide())
                  view.enableMessageWriting()

                  from.filter(_ != toConv.id).foreach { id =>

                    view.setVisible(toConv.isActive)
                    draftMap.get(toConv.id).map { draftText =>
                      view.setText(draftText)
                      view.setConversation()
                    }
                    audioMessageRecordingView.hide()
                  }
                  // TODO: ConversationScreenController should listen to this signal and do it itself
                  extendedCursorContainer.foreach(_.close(true))
                }
              }
            case None =>
          }
        }

      case _ =>
    }

    accountsController.isTeam.flatMap {
      case true  => participantsController.guestBotGroup
      case false => Signal.const((false, false, false))
    }.onUi {
      case (hasGuest, hasBot, isGroup) => updateGuestsBanner(hasGuest, hasBot, isGroup)
    }

    keyboardController.isKeyboardVisible.onUi(visible => if(visible) collapseGuestsBanner())

    replyController.replyData.map(_.isEmpty)
      .onUi(visible => cursorView.foreach(_.topBorder.setVisible(visible)))
  }

  private def updateGuestsBanner(hasGuest: Boolean, hasBot: Boolean, isGroup: Boolean): Unit = {
    def openGuestsBanner(resId: Int): Unit = {
      if (!isBannerOpen) {
        isBannerOpen = true
        guestsBanner.foreach { banner =>
          banner.setVisibility(View.VISIBLE)
          banner.setPivotY(0.0f)
          banner.setScaleY(1.0f)
        }
        guestsBannerText.foreach(_.setAlpha(1.0f))
      }
      guestsBannerText.foreach(_.setText(resId))
    }

    def hideGuestsBanner(): Unit = {
      isBannerOpen = false
      guestsBanner.foreach(_.setVisibility(View.GONE))
    }

    (hasGuest, hasBot, isGroup) match {
      case (true, true, true)   => openGuestsBanner(R.string.guests_and_services_are_present)
      case (true, false, true)  => openGuestsBanner(R.string.guests_are_present)
      case (false, true, true)  => openGuestsBanner(R.string.services_are_present)
      case _ => hideGuestsBanner()
    }
  }

  private def collapseGuestsBanner(): Unit = {
    if (isBannerOpen) {
      isBannerOpen = false
      guestsBanner.foreach { banner =>
        banner.setPivotY(0.0f)
        banner.animate().scaleY(0.1f).start()
      }
      guestsBannerText.foreach(_.animate().alpha(0.0f).start())
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_conversation, viewGroup, false)

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    if (savedInstanceState != null) previewShown ! savedInstanceState.getBoolean(SAVED_STATE_PREVIEW, false)

    findById(R.id.tiv_typing_indicator_view)

    containerPreview = findById(R.id.fl__conversation_overlay)

    returningF( findById(R.id.sv__conversation_toolbar__verified_shield) ){ view: ShieldView =>
      view.setVisible(false)
    }

    // Recording audio messages
    audioMessageRecordingView = findById[AudioMessageRecordingView](R.id.amrv_audio_message_recording)

    // invisible footer to scroll over inputfield
    returningF( new FrameLayout(getActivity) ){ footer: FrameLayout =>
      footer.setLayoutParams(
        new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.cursor__list_view_footer__height))
      )
    }

    leftMenu = findById(R.id.conversation_left_menu)
    toolbar = findById(R.id.t_conversation_toolbar)
    toolbarTitle = ViewUtils.getView(toolbar, R.id.tv__conversation_toolbar__title).asInstanceOf[TextView]

    replyView.foreach {
      _.setOnClose(replyController.clearMessage())
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
            callStartController.startCallInCurrentConv(withVideo = item.getItemId == R.id.action_video_call, forceOption = true)
            cursorView.foreach(_.closeEditMessage(false))
            true
          case _ => false
      }
    })

    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        cursorView.foreach(_.closeEditMessage(false))
        getActivity.onBackPressed()
        keyboardController.hideKeyboardIfVisible()
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

    cursorView.foreach(_.setCallback(cursorCallback))

    extendedCursorContainer.foreach(globalLayoutController.addKeyboardHeightObserver)
    extendedCursorContainer.foreach(globalLayoutController.addKeyboardVisibilityObserver)
    extendedCursorContainer.foreach(_.setCallback(extendedCursorContainerCallback))
    navigationController.addNavigationControllerObserver(navigationControllerObserver)
    navigationController.addPagerControllerObserver(pagerControllerObserver)
    singleImageController.addSingleImageObserver(singleImageObserver)
    globalLayoutController.addKeyboardVisibilityObserver(keyboardVisibilityObserver)
    slidingPaneController.addObserver(slidingPaneObserver)

    draftMap.withCurrentDraft { draftText => if (!TextUtils.isEmpty(draftText.text)) cursorView.foreach(_.setText(draftText)) }

    listView

    mentionsList.foreach { v =>
      v.setAdapter(mentionCandidatesAdapter)
      v.setLayoutManager(returning(new LinearLayoutManager(getContext)){
        _.setStackFromEnd(true)
      })
    }

    cursorView.foreach { v =>

      subs += Signal(v.mentionSearchResults, accountsController.teamId, inject[ThemeController].currentTheme).onUi {
        case (data, teamId, theme) =>
          mentionCandidatesAdapter.setData(data, teamId, theme)
          mentionsList.foreach(_.scrollToPosition(data.size - 1))
      }

      val mentionsListShouldShow = Signal(v.mentionQuery.map(_.nonEmpty), v.mentionSearchResults.map(_.nonEmpty), v.selectionHasMention).map {
        case (true, true, false) => true
        case _ => false
      }

      subs += mentionsListShouldShow.onUi(showMentionsList)

      subs += mentionsListShouldShow.zip(replyController.replyContent).onUi {
        case (false, Some(ReplyContent(messageData, asset, senderName))) =>
          replyView.foreach { rv =>
            rv.setMessage(messageData, asset, senderName)
            rv.setVisible(true)
          }
        case _ =>
          replyView.foreach(_.setVisible(false))
      }
    }
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroyView()
  }

  private def updateTitle(text: String): Unit = if (toolbarTitle != null) toolbarTitle.setText(text)

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    assetIntentsManager.foreach { _.onSaveInstanceState(outState) }
    previewShown.head.foreach { isShown => outState.putBoolean(SAVED_STATE_PREVIEW, isShown) }
  }

  override def onPause(): Unit = {
    super.onPause()
    keyboardController.hideKeyboardIfVisible()
    audioMessageRecordingView.hide()
  }

  override def onStop(): Unit = {
    extendedCursorContainer.foreach(_.close(true))
    extendedCursorContainer.foreach(_.setCallback(null))
    cursorView.foreach(_.setCallback(null))

    toolbar.setOnClickListener(null)
    toolbar.setOnMenuItemClickListener(null)
    toolbar.setNavigationOnClickListener(null)
    leftMenu.setOnMenuItemClickListener(null)

    extendedCursorContainer.foreach(globalLayoutController.removeKeyboardHeightObserver)
    extendedCursorContainer.foreach(globalLayoutController.removeKeyboardVisibilityObserver)
    singleImageController.removeSingleImageObserver(singleImageObserver)
    globalLayoutController.removeKeyboardVisibilityObserver(keyboardVisibilityObserver)
    slidingPaneController.removeObserver(slidingPaneObserver)
    navigationController.removePagerControllerObserver(pagerControllerObserver)
    navigationController.removeNavigationControllerObserver(navigationControllerObserver)

    cursorView.foreach { view =>
      if (!view.isEditingMessage) draftMap.setCurrent(view.getText)
    }
    super.onStop()
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

  private lazy val imagePreviewCallback = new ImagePreviewCallback {
    override def onCancelPreview(): Unit = {
      previewShown ! false
      navigationController.setPagerEnabled(true)
      containerPreview
        .animate
        .translationY(getView.getMeasuredHeight)
        .setDuration(getInt(R.integer.animation_duration_medium))
        .setInterpolator(new Expo.EaseIn)
        .withEndAction(new Runnable() {
          override def run(): Unit = if (containerPreview != null) containerPreview.removeAllViews()
        })
    }

    override def onSketchOnPreviewPicture(input: RawAssetInput, source: ImagePreviewLayout.Source, method: IDrawingController.DrawingMethod): Unit = {
      screenController.showSketch ! Sketch.cameraPreview(input, method)
      extendedCursorContainer.foreach(_.close(true))
    }

    override def onSendPictureFromPreview(input: RawAssetInput, source: ImagePreviewLayout.Source): Unit = {
      convController.sendMessage(input)
      extendedCursorContainer.foreach(_.close(true))
      onCancelPreview()
    }
  }

  private val assetIntentsManagerCallback = new AssetIntentsManager.Callback {
    override def onDataReceived(intentType: AssetIntentsManager.IntentType, uri: URI): Unit = intentType match {
      case AssetIntentsManager.IntentType.FILE_SHARING =>
        permissions.requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).map {
          case true =>
            convController.sendMessage(uri, getActivity)
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
        showImagePreview { _.setImage(uri, ImagePreviewLayout.Source.DeviceGallery) }
      case _ =>
        convController.sendMessage(uri, getActivity)
        navigationController.setRightPage(Page.MESSAGE_STREAM, TAG)
        extendedCursorContainer.foreach(_.close(true))
    }

    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = {
      extendedCursorContainer.foreach { ecc =>
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(intent.getAction) &&
          ecc.getType == ExtendedCursorContainer.Type.IMAGES &&
          ecc.isExpanded) {
          // Close keyboard camera before requesting external camera for recording video
          ecc.close(true)
        }
      }

      startActivityForResult(intent, intentType.requestCode)
      getActivity.overridePendingTransition(R.anim.camera_in, R.anim.camera_out)
    }

    override def onFailed(tpe: AssetIntentsManager.IntentType): Unit = {}

    override def onCanceled(tpe: AssetIntentsManager.IntentType): Unit = {}
  }

  private val extendedCursorContainerCallback = new ExtendedCursorContainer.Callback {
    override def onExtendedCursorClosed(lastType: ExtendedCursorContainer.Type): Unit = {
      cursorView.foreach(_.onExtendedCursorClosed())

      if (lastType == ExtendedCursorContainer.Type.EPHEMERAL)
        convController.currentConv.head.map {
          _.ephemeralExpiration.map { exp =>
            val eph = exp.duration.toMillis match {
              case 0 => None
              case e => Some(e.millis)
            }
            globalPrefs.preference(GlobalPreferences.LastEphemeralValue) := eph
          }
        }

      globalLayoutController.resetScreenAwakeState()
    }
  }

  private def openExtendedCursor(cursorType: ExtendedCursorContainer.Type): Unit = cursorType match {
      case ExtendedCursorContainer.Type.NONE =>
      case ExtendedCursorContainer.Type.EMOJIS =>
        extendedCursorContainer.foreach(_.openEmojis(userPreferencesController.getRecentEmojis, userPreferencesController.getUnsupportedEmojis, new EmojiKeyboardLayout.Callback {
          override def onEmojiSelected(emoji: LogTag) = {
            cursorView.foreach(_.insertText(emoji))
            userPreferencesController.addRecentEmoji(emoji)
          }
        }))
      case ExtendedCursorContainer.Type.EPHEMERAL =>
        convController.currentConv.map(_.ephemeralExpiration).head.foreach {
          case Some(ConvExpiry(_)) => //do nothing - global timer is set
          case exp => extendedCursorContainer.foreach(_.openEphemeral(new EphemeralLayout.Callback {
            override def onEphemeralExpirationSelected(expiration: Option[FiniteDuration], close: Boolean) = {
              if (close) extendedCursorContainer.foreach(_.close(false))
              convController.setEphemeralExpiration(expiration)
            }
          }, exp.map(_.duration)))
        }
      case ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING =>
        extendedCursorContainer.foreach(_.openVoiceFilter(new VoiceFilterLayout.Callback {
          override def onAudioMessageRecordingStarted(): Unit = {
            globalLayoutController.keepScreenAwake()
          }

          override def onCancel(): Unit = extendedCursorContainer.foreach(_.close(false))

          override def sendRecording(audioAssetForUpload: AudioAssetForUpload, appliedAudioEffect: AudioEffect): Unit = {
            convController.sendMessage(audioAssetForUpload, getActivity)
            extendedCursorContainer.foreach(_.close(true))
          }
        }))
      case ExtendedCursorContainer.Type.IMAGES =>
        extendedCursorContainer.foreach(_.openCursorImages(new CursorImagesLayout.Callback {
          override def openCamera(): Unit = cameraController.openCamera(CameraContext.MESSAGE)

          override def openVideo(): Unit = captureVideoAskPermissions()

          override def onGalleryPictureSelected(uri: URI): Unit = {
            previewShown ! true
            showImagePreview {
              _.setImage(uri, ImagePreviewLayout.Source.InAppGallery)
            }
          }

          override def openGallery(): Unit = assetIntentsManager.foreach {
            _.openGallery()
          }

          override def onPictureTaken(imageData: Array[Byte], isMirrored: Boolean): Unit =
            showImagePreview {
              _.setImage(imageData, isMirrored)
            }
        }))
      case _ =>
        verbose(s"openExtendedCursor(unknown)")
    }


  private def captureVideoAskPermissions() = for {
    _ <- inject[GlobalCameraController].releaseCamera() //release camera so the camera app can use it
    _ <- permissions.requestAllPermissions(ListSet(CAMERA, WRITE_EXTERNAL_STORAGE)).map {
      case true => assetIntentsManager.foreach(_.captureVideo(getContext.getApplicationContext))
      case false => //
    }(Threading.Ui)
  } yield {}

  private val cursorCallback = new CursorCallback {
    override def onMotionEventFromCursorButton(cursorMenuItem: CursorMenuItem, motionEvent: MotionEvent): Unit =
      if (cursorMenuItem == CursorMenuItem.AUDIO_MESSAGE && audioMessageRecordingView.isVisible)
        audioMessageRecordingView.onMotionEventFromAudioMessageButton(motionEvent)

    override def captureVideo(): Unit = captureVideoAskPermissions()

    override def hideExtendedCursor(): Unit = extendedCursorContainer.foreach {
      case ecc if ecc.isExpanded => ecc.close(false)
      case _ =>
    }

    override def onMessageSent(msg: MessageData): Unit = listView.foreach(_.scrollToBottom())

    override def openExtendedCursor(tpe: ExtendedCursorContainer.Type): Unit = ConversationFragment.this.openExtendedCursor(tpe)

    override def onCursorClicked(): Unit = cursorView.foreach { cView =>
      listView.foreach { lView =>
        if (!cView.isEditingMessage && lView.scrollController.targetPosition.isEmpty) lView.scrollToBottom()
      }
    }

    override def openFileSharing(): Unit = assetIntentsManager.foreach { _.openFileSharing() }

    override def onCursorButtonLongPressed(cursorMenuItem: CursorMenuItem): Unit =
      cursorMenuItem match {
        case CursorMenuItem.AUDIO_MESSAGE =>
          callController.isCallActive.head.foreach {
            case true  => showErrorDialog(R.string.calling_ongoing_call_title, R.string.calling_ongoing_call_audio_message)
            case false =>
              extendedCursorContainer.foreach(_.close(true))
              audioMessageRecordingView.show()
          }
        case _ => //
      }
  }

  private val navigationControllerObserver = new NavigationControllerObserver {
    override def onPageVisible(page: Page): Unit = if (page == Page.MESSAGE_STREAM) {
      accountsController.isTeam.head.flatMap {
        case true  => participantsController.guestBotGroup.head
        case false => Future.successful((false, false, false))
      }.foreach {
        case (hasGuest, hasBot, isGroup) => updateGuestsBanner(hasGuest, hasBot, isGroup)
      }
      inflateCollectionIcon()
      cursorView.foreach(_.enableMessageWriting())
    }
  }

  private val pagerControllerObserver = new PagerControllerObserver {
    override def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int): Unit =
      if (positionOffset > 0) extendedCursorContainer.foreach(_.close(true))

    override def onPagerEnabledStateHasChanged(enabled: Boolean): Unit = {}
    override def onPageSelected(position: Int): Unit = {}
    override def onPageScrollStateChanged(state: Int): Unit = {}
  }

  private val singleImageObserver = new SingleImageObserver {
    override def onShowSingleImage(messageId: String): Unit = {}
    override def onHideSingleImage(): Unit = navigationController.setRightPage(Page.MESSAGE_STREAM, TAG)
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
      accountsController.isTeam.head.foreach { isTeam =>
        val dialog = ViewUtils.showAlertDialog(
          getActivity,
          R.string.asset_upload_error__file_too_large__title,
          R.string.asset_upload_error__file_too_large__message_default,
          R.string.asset_upload_error__file_too_large__button,
          null,
          true
        )
        dialog.setMessage(getString(R.string.asset_upload_error__file_too_large__message, s"${AssetData.maxAssetSizeInBytes(isTeam) / (1024 * 1024)}MB"))
        errorsController.dismissSyncError(err.id)
      }
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
    if (navigationController.getCurrentPage == Page.MESSAGE_STREAM) {
      keyboardController.hideKeyboardIfVisible()

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
          if (unverifiedUsers.isEmpty) getString(R.string.conversation__degraded_confirmation__header__someone)
          else if (unverifiedUsers.size == 1)
            getQuantityString(R.plurals.conversation__degraded_confirmation__header__single_user, unverifiedDevices, unverifiedNames.head)
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
          else getQuantityString(R.plurals.conversation__degraded_confirmation__negative_action, unverifiedUsers.size)

        val messageCount = Math.max(1, err.messages.size)
        val message = getQuantityString(R.plurals.conversation__degraded_confirmation__message, messageCount)

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

        confirmationController.requestConfirmation(request, IConfirmationController.CONVERSATION)
      }
    }

  private val slidingPaneObserver = new SlidingPaneObserver {
    override def onPanelSlide(panel: View, slideOffset: Float): Unit = {}
    override def onPanelOpened(panel: View): Unit = keyboardController.hideKeyboardIfVisible()
    override def onPanelClosed(panel: View): Unit = {}
  }

  private def showImagePreview(setImage: (ImagePreviewLayout) => Any): Unit = {
    val imagePreviewLayout = ImagePreviewLayout.newInstance(getContext, containerPreview, imagePreviewCallback)
    setImage(imagePreviewLayout)
    containerPreview.addView(imagePreviewLayout)
    previewShown ! true
    navigationController.setPagerEnabled(false)
    containerPreview.setTranslationY(getView.getMeasuredHeight)
    containerPreview.animate.translationY(0).setDuration(getInt(R.integer.animation_duration_medium)).setInterpolator(new Expo.EaseOut)
  }

  override def onBackPressed(): Boolean = extendedCursorContainer.map {
    case ecc if ecc.isExpanded =>
      ecc.close(false)
      true
    case _ => false
  }.getOrElse(false)
}

object ConversationFragment {
  val TAG = ConversationFragment.getClass.getName
  val SAVED_STATE_PREVIEW = "SAVED_STATE_PREVIEW"
  val REQUEST_VIDEO_CAPTURE = 911

  def newInstance() = new ConversationFragment
}
