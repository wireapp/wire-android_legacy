/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.widget.{ImageView, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.Message
import com.waz.zclient.R
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils._
/**
  * Created by admin on 03/03/17.
  */
class SpotifyPartView (context: Context, attrs: AttributeSet, style: Int) extends SoundPreviewPartView(context, attrs, style, Message.Part.Type.SPOTIFY) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Spotify

  override def getImageView: ImageView = imageView
  override def getTitleView: TextView = titleView
  override def getArtistView: TextView = artistView
  override def getErrorView: GlyphTextView = errorView

  lazy val imageView: ImageView       = findById(R.id.iv__spotify_message__image)
  lazy val titleView: TextView        = findById(R.id.ttv__spotify_message__title)
  lazy val artistView: TextView       = findById(R.id.ttv__spotify_message__artist)
  lazy val errorView: GlyphTextView   = findById(R.id.gtv__spotify_message__error)

  inflate(R.layout.message_spotify_content)

  init()
}

