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
package com.waz.model

import com.waz.api.impl.AccentColor
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.AssetStatus.UploadDone
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try
import com.waz.model.UserInfo.Service
import org.threeten.bp.Instant

case class UserInfo(id:           UserId,
                    name:         Option[String]          = None,
                    accentId:     Option[Int]             = None,
                    email:        Option[EmailAddress]    = None,
                    phone:        Option[PhoneNumber]     = None,
                    picture:      Option[Seq[AssetData]]  = None, //the empty sequence is used to delete pictures
                    trackingId:   Option[TrackingId]      = None,
                    deleted:      Boolean                 = false,
                    handle:       Option[Handle]          = None,
                    privateMode:  Option[Boolean]         = None,
                    service:      Option[Service]         = None,
                    teamId:       Option[TeamId]          = None,
                    expiresAt:    Option[RemoteInstant]   = None
                   ) {
  //TODO Dean - this will actually prevent deleting profile pictures, since the empty seq will be mapped to a None,
  //And so in UserData, the current picture will be used instead...
  def mediumPicture = picture.flatMap(_.collectFirst { case a@AssetData.IsImageWithTag(Medium) => a })
}

object UserInfo {
  import JsonDecoder._

  case class Service(id: IntegrationId, provider: ProviderId)

  def decodeService(s: Symbol)(implicit js: JSONObject): Service = Service(decodeId[IntegrationId]('id), decodeId[ProviderId]('provider))

  def decodeOptService(s: Symbol)(implicit js: JSONObject): Option[Service] = decodeOptObject(s) match {
    case Some(serviceJs) => Option(decodeService(s)(serviceJs))
    case _ => None
  }

  implicit object Decoder extends JsonDecoder[UserInfo] {

    def imageData(userId: UserId, js: JSONObject) = {
      val mime = decodeString('content_type)(js)
      val size = decodeInt('content_length)(js)
      val data = decodeOptString('data)(js)
      val id = RAssetId(decodeString('id)(js))
      implicit val info = js.getJSONObject("info")

      AssetData(
        status = UploadDone,
        sizeInBytes = size,
        mime = Mime(mime),
        metaData = Some(AssetMetaData.Image(Dim2('width, 'height), Image.Tag('tag))),
        data = data.map(AssetData.decodeData),
        convId = Some(RConvId(userId.str)), //v2 asset needs user conv for downloading
        v2ProfileId = Some(id)
      )

    }

    def getAssets(implicit js: JSONObject): Option[AssetData] = fromArray(js, "assets") flatMap { assets =>
      Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
        AssetData(
          remoteId = decodeOptRAssetId('key)(js),
          metaData = Some(AssetMetaData.Image(Dim2(0, 0), Image.Tag(decodeString('size)(js))))
        )
      }.collectFirst { case a@AssetData.IsImageWithTag(Tag.Medium) => a } //discard preview
    }

    def getPicture(userId: UserId)(implicit js: JSONObject): Option[AssetData] = fromArray(js, "picture") flatMap { pic =>
      val id = decodeOptString('correlation_id)(pic.getJSONObject(0).getJSONObject("info")).fold(AssetId())(AssetId(_))

      Seq.tabulate(pic.length())(i => imageData(userId, pic.getJSONObject(i))).collectFirst {
        case a@AssetData.IsImageWithTag(Medium) => a //discard preview
      }.map(_.copy(id = id))
    }

    private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)

    override def apply(implicit js: JSONObject): UserInfo = {
      val accentId = decodeOptInt('accent_id).orElse {
        decodeDoubleSeq('accent) match {
          case Seq(r, g, b, a) => Some(AccentColor(r, g, b, a).id)
          case _ => None
        }
      }
      val id = UserId('id)
      //prefer v3 ("assets") over v2 ("picture") - this will prevent unnecessary uploading of v3 if a v2 also exists.
      val pic = getAssets.orElse(getPicture(id)).toSeq
      val privateMode = decodeOptBoolean('privateMode)
      UserInfo(
        id, 'name, accentId, 'email, 'phone, Some(pic), decodeOptString('tracking_id) map (TrackingId(_)),
        deleted = 'deleted, handle = 'handle, privateMode = privateMode, service = decodeOptService('service),
        'team, decodeOptISORemoteInstant('expires_at))
    }
  }

  def encodeAsset(assets: Seq[AssetData]): JSONArray = {
    val arr = new json.JSONArray()
    assets.collect {
      case a@AssetData.WithRemoteId(rId) =>
        val size = a.tag match {
          case Preview => "preview"
          case Medium => "complete"
          case _ => ""
        }
        JsonEncoder { o =>
          o.put("size", size)
          o.put("key", rId.str)
          o.put("type", "image")
        }
    }.foreach(arr.put)
    arr
  }

  def encodeService(service: Service): JSONObject = JsonEncoder { o =>
    o.put("id", service.id)
    o.put("provider", service.provider)
  }

  implicit lazy val Encoder: JsonEncoder[UserInfo] = new JsonEncoder[UserInfo] {
    override def apply(info: UserInfo): JSONObject = JsonEncoder { o =>
      o.put("id", info.id.str)
      info.name.foreach(o.put("name", _))
      info.phone.foreach(p => o.put("phone", p.str))
      info.email.foreach(e => o.put("email", e.str))
      info.accentId.foreach(o.put("accent_id", _))
      info.handle.foreach(h => o.put("handle", h.string))
      info.trackingId.foreach(id => o.put("tracking_id", id.str))
      info.picture.foreach(ps => o.put("assets", encodeAsset(ps)))
    }
  }

//  implicit lazy val ContentEncoder: ContentEncoder[UserInfo] = JsonContentEncoder.map { (info: UserInfo) =>
//    JsonEncoder { o =>
//      info.name.foreach(o.put("name", _))
//      info.accentId.foreach(o.put("accent_id", _))
//      info.picture.foreach(ps => o.put("assets", encodeAsset(ps)))
//    }
//  }
}
