/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.model.messages.media

import com.waz.api.KindOfMedia
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp.Instant

object MediaAssetDataProtocol {
  import MediaAssetData._
  import JsonDecoder._

  implicit lazy val ArtistEncoder: JsonEncoder[ArtistData] = new JsonEncoder[ArtistData] {
    override def apply(v: ArtistData): JSONObject = JsonEncoder { o =>
      o.put("name", v.name)
      v.avatar foreach (a => o.put("avatar", a.str))
    }
  }

  implicit lazy val TrackEncoder: JsonEncoder[TrackData] = new JsonEncoder[TrackData] {
    override def apply(v: TrackData): JSONObject = JsonEncoder { o =>
      encodeCommonMediaAssetFields(o, v)
      o.put("streamable", v.streamable)
      v.streamUrl foreach (o.put("streamUrl", _))
      v.previewUrl foreach (o.put("previewUrl", _))
    }
  }

  implicit lazy val PlaylistEncoder: JsonEncoder[PlaylistData] = new JsonEncoder[PlaylistData] {
    override def apply(v: PlaylistData): JSONObject = JsonEncoder { o =>
      encodeCommonMediaAssetFields(o, v)
      o.put("tracks", JsonEncoder.arr(v.tracks)) }
  }

  implicit lazy val EmptyEncoder: JsonEncoder[EmptyMediaAssetData] = new JsonEncoder[EmptyMediaAssetData] {
    override def apply(v: EmptyMediaAssetData): JSONObject = JsonEncoder { o =>
      o.put("kind", KindOfMediaCodec.encode(v.kind))
      o.put("provider", MediaProviderCodec.encode(v.provider))
    }
  }

  implicit lazy val MediaAssetEncoder: JsonEncoder[MediaAssetData] = new JsonEncoder[MediaAssetData] {
    override def apply(v: MediaAssetData): JSONObject = v match {
      case t: TrackData => TrackEncoder(t)
      case pl: PlaylistData => PlaylistEncoder(pl)
      case e: EmptyMediaAssetData => EmptyEncoder(e)
    }
  }

  private def encodeCommonMediaAssetFields(o: JSONObject, v: MediaAssetData): Unit = {
    o.put("kind", KindOfMediaCodec.encode(v.kind))
    o.put("provider", MediaProviderCodec.encode(v.provider))
    o.put("title", v.title)
    v.artist foreach (a => o.put("artist", ArtistEncoder(a)))
    v.duration foreach (d => o.put("durationMillis", d.toMillis))
    o.put("linkUrl", v.linkUrl)
    v.artwork foreach (a => o.put("artwork", a.str))
    o.put("expires", v.expires.toEpochMilli)
  }

  implicit lazy val ArtistDecoder: JsonDecoder[ArtistData] = new JsonDecoder[ArtistData] {
    override def apply(implicit js: JSONObject): ArtistData = ArtistData(name = 'name, avatar = decodeOptAssetId('avatar))
  }

  implicit lazy val TrackDecoder: JsonDecoder[TrackData] = new JsonDecoder[TrackData] {
    override def apply(implicit js: JSONObject): TrackData =
      TrackData(provider = MediaProviderCodec.decode(js.getString("provider")),
                title = 'title,
                artist = opt[ArtistData]('artist),
                linkUrl = 'linkUrl,
                artwork = decodeOptUploadAssetId('artwork),
                duration = decodeOptDuration('durationMillis),
                streamable = 'streamable,
                streamUrl = 'streamUrl,
                previewUrl = 'previewUrl,
                expires = decodeExpires)
  }

  implicit lazy val PlaylistDecoder: JsonDecoder[PlaylistData] = new JsonDecoder[PlaylistData] {
    override def apply(implicit js: JSONObject): PlaylistData =
      PlaylistData(provider = MediaProviderCodec.decode(js.getString("provider")),
                   title = 'title,
                   artist = opt[ArtistData]('artist),
                   linkUrl = 'linkUrl,
                   artwork = decodeOptUploadAssetId('artwork),
                   duration = decodeOptDuration('durationMillis),
                   tracks = decodeSeq[TrackData]('tracks),
                   expires = decodeExpires)
  }

  implicit lazy val EmptyDecoder: JsonDecoder[EmptyMediaAssetData] = new JsonDecoder[EmptyMediaAssetData] {
    override def apply(implicit js: JSONObject): EmptyMediaAssetData = EmptyMediaAssetData(provider = MediaProviderCodec.decode(js.getString("provider")))
  }

  private def decodeExpires(implicit js: JSONObject): Instant = if (!js.has("expires")) Instant.EPOCH else Instant.ofEpochMilli('expires)

  implicit lazy val MediaAssetDecoder: JsonDecoder[MediaAssetData] = new JsonDecoder[MediaAssetData] {
    override def apply(implicit js: JSONObject): MediaAssetData = KindOfMediaCodec.decode('kind) match {
      case KindOfMedia.TRACK => TrackDecoder(js)
      case KindOfMedia.PLAYLIST => PlaylistDecoder(js)
      case KindOfMedia.UNKNOWN => EmptyDecoder(js)
    }
  }
}
