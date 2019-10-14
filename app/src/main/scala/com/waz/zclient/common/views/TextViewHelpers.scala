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
package com.waz.zclient.common.views

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.waz.content.GlobalPreferences.IncognitoKeyboardEnabled
import com.waz.service.ZMessaging
import com.waz.utils.events.EventContext
import com.waz.zclient.BuildConfig
import com.waz.zclient.ui.utils.MathUtils


/**
  * Utilities related to input fields
  */
object TextViewHelpers {

  implicit class TextViewFlagsImprovement(textView: TextView) {

    /**
      * Add an ime option to the existing options
      * @param option
      */
    def addImeOption(option: Int): Unit = {
      textView.setImeOptions(textView.getImeOptions() | option)
    }

    /**
      * Remove an ime option from the existing options, if present
      * @param option
      */
    def removeImeOption(option: Int): Unit = {
      textView.setImeOptions(MathUtils.removeBinaryFlag(textView.getImeOptions(), option))
    }

    /**
      * Add an input type to the existing input types
      * @param inputType
      */
    def addInputType(inputType: Int): Unit = {
      textView.setInputType(textView.getInputType() | inputType)
    }

    /**
      * Remove an input type from the existing input types, if present
      * @param inputType
      */
    def removeInputType(inputType: Int): Unit = {
      textView.setInputType(MathUtils.removeBinaryFlag(textView.getInputType(), inputType))
    }

    /**
      * Enable or disable private mode and suggestions on the field
      * - If enabled, enables incognito mode and disables suggestions
      * - If disabled, disables incognito mode and enables suggestions
      * @param on true if private mode should be switched on, false if it should be switched off
      */
    def setPrivateMode(on: Boolean): Unit = {
      if(on) {
        textView.addInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        // this disables autocomplete because it implies that you will provide your
        // own autocomplete facility. We don't, so no autocomplete is shown
        textView.addInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        textView.addImeOption(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
      } else {
        textView.removeInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        textView.removeInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        textView.removeImeOption(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
      }
    }

    /**
      * Set incognito/suggestion mode on/off according to preferences
      */
    def setPrivateModeFromPreferences()(implicit eventContext: EventContext): Unit = {
      import com.waz.threading.Threading.Implicits.Ui
      // If hardcoded by configuration, just turn it on
      if(BuildConfig.FORCE_PRIVATE_KEYBOARD) {
        textView.setPrivateMode(true)
        return
      }

      // else read from user preferences
      for {
        globalModule <- ZMessaging.globalModule
        prefs = globalModule.prefs
        settingSignal = prefs(IncognitoKeyboardEnabled).signal
      } yield {
        settingSignal.onUi { v =>
          textView.setPrivateMode(v)
        }
      }
    }
  }
}

