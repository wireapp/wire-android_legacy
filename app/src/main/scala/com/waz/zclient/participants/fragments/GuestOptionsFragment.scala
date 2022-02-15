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
package com.waz.zclient.participants.fragments

import android.content.{ClipData, ClipboardManager, Context, DialogInterface}
import android.os.Bundle
import androidx.core.app.ShareCompat
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, FrameLayout, TextView}
import androidx.appcompat.widget.SwitchCompat
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.MenuRowButton
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.showToast
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, R, SpinnerController}

import scala.concurrent.Future
import com.waz.threading.Threading._

final class GuestOptionsFragment extends FragmentHelper {
  import Threading.Implicits.Background

  implicit def cxt: Context = getActivity

  private lazy val zms = inject[Signal[ZMessaging]]

  private lazy val convCtrl = inject[ConversationController]
  private lazy val spinnerController = inject[SpinnerController]

  //TODO look into using something more similar to SwitchPreference
  private lazy val guestsSwitch = returning(view[SwitchCompat](R.id.guest_toggle)) { vh =>
    convCtrl.currentConvIsTeamOnly.currentValue.foreach(teamOnly => vh.foreach(_.setChecked(!teamOnly)))
    convCtrl.currentConvIsTeamOnly.onUi(teamOnly => vh.foreach(_.setChecked(!teamOnly)))
  }

  private lazy val guestsTitle = view[TextView](R.id.guest_toggle_title)
  private lazy val linkButton = view[FrameLayout](R.id.link_button)
  private lazy val guestLinkText = returning(view[TypefaceTextView](R.id.link_button_link_text)) { text =>
    convCtrl.currentConv.map(_.link).onUi {
      case Some(link) =>
        text.foreach { t =>
          t.setVisibility(View.VISIBLE)
          t.setText(link.url)
        }
      case None =>
        text.foreach(_.setVisibility(View.GONE))
    }
  }
  private lazy val guestLinkCreate = returning(view[MenuRowButton](R.id.link_button_create_link)) { text =>
    convCtrl.currentConv.map(_.link.isDefined).onUi { hasLink =>
      text.foreach(_.setVisibility(if (hasLink) View.GONE else View.VISIBLE))
    }
  }
  private lazy val copyLinkButton = returning(view[MenuRowButton](R.id.copy_link_button)) { button =>
    convCtrl.currentConv.map(_.link.isDefined).onUi { hasLink =>
      button.foreach(_.setVisibility(if (hasLink) View.VISIBLE else View.GONE))
    }
  }
  private lazy val shareLinkButton = returning(view[MenuRowButton](R.id.share_link)) { button =>
    convCtrl.currentConv.map(_.link.isDefined).onUi { hasLink =>
      button.foreach(_.setVisibility(if (hasLink) View.VISIBLE else View.GONE))
    }
  }
  private lazy val revokeLinkButton = returning(view[MenuRowButton](R.id.revoke_link_button)) { button =>
    convCtrl.currentConv.map(_.link.isDefined).onUi { hasLink =>
      button.foreach(_.setVisibility(if (hasLink) View.VISIBLE else View.GONE))
    }
  }
  private lazy val guestLinkOptions = returning(view[ViewGroup](R.id.guest_link_options)) { linkOptions =>
    convCtrl.currentConv.map(_.isTeamOnly).onUi { isTeamOnly =>
      linkOptions.foreach(_.setVisibility(if (isTeamOnly) View.GONE else View.VISIBLE))
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.guest_options_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    guestsSwitch
    guestLinkText
    guestLinkCreate.foreach { v =>
      v.divider.setVisibility(View.GONE)
      v.setClickable(false)
      v.setFocusable(false)
    }

    linkButton.foreach(_.onClick {
      spinnerController.showSpinner()
      zms.head.map { zms =>
        convCtrl.currentConv.head.flatMap { conv =>
          conv.link match {
            case Some(link) =>
              copyToClipboard(link.url)
              spinnerController.hideSpinner()
              Future.successful(())
            case _ =>
              zms.conversations.createLink(conv.id).map {
                case Left(_) =>
                  spinnerController.hideSpinner()
                  ViewUtils.showAlertDialog(getContext, R.string.empty_string, R.string.allow_guests_error_title, android.R.string.ok, new DialogInterface.OnClickListener {
                    override def onClick(dialog: DialogInterface, which: Int): Unit = dialog.dismiss()
                  }, true)
                case _ =>
                  spinnerController.hideSpinner()
              } (Threading.Ui)
          }
        } (Threading.Ui)

      }
    })

    copyLinkButton.foreach(_.onClick {
      convCtrl.currentConv.head.map(_.link.foreach(link => copyToClipboard(link.url)))(Threading.Ui)
    })
    shareLinkButton.foreach(_.onClick {
      convCtrl.currentConv.head.map(_.link.foreach { link =>
        val intentBuilder = ShareCompat.IntentBuilder.from(getActivity)
        intentBuilder.setType("text/plain")
        intentBuilder.setText(link.url)
        intentBuilder.startChooser()
      })(Threading.Ui)
    })
    revokeLinkButton.foreach(_.onClick {
      ViewUtils.showAlertDialog(getContext,
        R.string.empty_string,
        R.string.revoke_link_message,
        R.string.revoke_link_confirm,
        android.R.string.cancel,
        new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit = {
            spinnerController.showSpinner()
            (for {
              zms  <- zms.head
              conv <- convCtrl.currentConv.head
              res  <- zms.conversations.removeLink(conv.id)
            } yield res).map {
              case Left(_) =>
                spinnerController.hideSpinner()
                ViewUtils.showAlertDialog(getContext, R.string.empty_string, R.string.allow_guests_error_title, android.R.string.ok, new DialogInterface.OnClickListener {
                  override def onClick(dialog: DialogInterface, which: Int): Unit = dialog.dismiss()
                }, true)
              case _ =>
                spinnerController.hideSpinner()
            } (Threading.Ui)
            dialog.dismiss()
          }
        }, new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int): Unit = dialog.dismiss()
        })
    })
    guestLinkOptions
  }

  private def copyToClipboard(text: String): Unit = {
    val clipboard: ClipboardManager = getContext.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip: ClipData = ClipData.newPlainText("", text) //TODO: label?
    clipboard.setPrimaryClip(clip)
    showToast(R.string.link_copied_toast)
  }

  override def onResume() = {
    super.onResume()
    guestsSwitch.foreach {
      _.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {

          def setTeamOnly(): Unit = {
            (for {
              z <- zms.head
              c <- convCtrl.currentConvId.head
              resp <- z.conversations.setToTeamOnly(c, !isChecked)
            } yield resp).map { resp =>
              setGuestsSwitchEnabled(true)
              resp match {
                case Right(_) => //
                case Left(_) =>
                  ViewUtils.showAlertDialog(getContext, R.string.allow_guests_error_title, R.string.allow_guests_error_body, android.R.string.ok, new DialogInterface.OnClickListener {
                    override def onClick(dialog: DialogInterface, which: Int): Unit = dialog.dismiss()
                  }, true)
              }
            }(Threading.Ui)
          }

          setGuestsSwitchEnabled(false)

          if (!isChecked) {
            convCtrl.currentConv.map(_.isTeamOnly).head.map {
              case false =>
                ViewUtils.showAlertDialog(getContext,
                  R.string.empty_string,
                  R.string.allow_guests_warning_body,
                  R.string.allow_guests_warning_confirm,
                  android.R.string.cancel,
                  new DialogInterface.OnClickListener {
                    override def onClick(dialog: DialogInterface, which: Int): Unit = {
                      setTeamOnly()
                      dialog.dismiss()
                    }
                  }, new DialogInterface.OnClickListener {
                    override def onClick(dialog: DialogInterface, which: Int): Unit = {
                      guestsSwitch.foreach(_.setChecked(true))
                      setGuestsSwitchEnabled(true)
                      dialog.dismiss()
                    }
                  })
              case _ =>
                setGuestsSwitchEnabled(true)
            } (Threading.Ui)
          } else {
            setTeamOnly()
          }
        }
      })
    }
  }

  private def setGuestsSwitchEnabled(enabled: Boolean): Unit = {
    guestsSwitch.foreach(_.setEnabled(enabled))
    guestsTitle.foreach(_.setAlpha(if (enabled) 1f else 0.5f))
  }

  override def onStop() = {
    guestsSwitch.foreach(_.setOnCheckedChangeListener(null))
    super.onStop()
  }
}

object GuestOptionsFragment {
  val Tag: String = getClass.getSimpleName
}
