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

import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.AssetStatus.UploadDone
import com.waz.model.UserInfo.{ProfilePicture, Service}
import com.waz.model.ManagedBy.ManagedBy
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try

final case class UserInfo(id:           UserId,
                          domain:       Domain                  = Domain.Empty,
                          name:         Option[Name]            = None,
                          accentId:     Option[Int]             = None,
                          email:        Option[EmailAddress]    = None,
                          phone:        Option[PhoneNumber]     = None,
                          picture:      Option[Seq[ProfilePicture]]  = None, //the empty sequence is used to delete pictures
                          trackingId:   Option[TrackingId]      = None,
                          deleted:      Boolean                 = false,
                          handle:       Option[Handle]          = None,
                          privateMode:  Option[Boolean]         = None,
                          service:      Option[Service]         = None,
                          teamId:       Option[TeamId]          = None,
                          expiresAt:    Option[RemoteInstant]   = None,
                          ssoId:        Option[SSOId]           = None,
                          managedBy:    Option[ManagedBy]       = None,
                          fields:       Option[Seq[UserField]]  = None)

object UserInfo {
  import JsonDecoder._

  final case class Service(id: IntegrationId, provider: ProviderId)

  final case class ProfilePicture(id: AssetId, tag: Tag)

  def decodeService(s: Symbol)(implicit js: JSONObject): Service = Service(decodeId[IntegrationId]('id), decodeId[ProviderId]('provider))

  def decodeOptService(s: Symbol)(implicit js: JSONObject): Option[Service] = decodeOptObject(s) match {
    case Some(serviceJs) => Option(decodeService(s)(serviceJs))
    case _ => None
  }

  implicit object Decoder extends JsonDecoder[UserInfo] {

    def imageData(userId: UserId, js: JSONObject): AssetData = {
      val mime = decodeString('content_type)(js)
      val size = decodeInt('content_length)(js)
      val data = decodeOptString('data)(js)
      implicit val info: JSONObject = js.getJSONObject("info")

      AssetData(
        status = UploadDone,
        sizeInBytes = size,
        mime = Mime(mime),
        metaData = Some(AssetMetaData.Image(Dim2('width, 'height), Image.Tag('tag))),
        data = data.map(AssetData.decodeData)
      )

    }

    def getAssets(implicit js: JSONObject): Seq[ProfilePicture] = fromArray(js, "assets").map { assets =>
      Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
        val id = AssetId(decodeString('key)(js))
        val tag = Image.Tag(decodeString('size)(js))
        ProfilePicture(id, tag)
      }
    }.getOrElse(Seq())

    private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)

    override def apply(implicit js: JSONObject): UserInfo = {
      val accentId = decodeOptInt('accent_id).orElse {
        decodeDoubleSeq('accent) match {
          case Seq(r, g, b, a) => Some(AccentColor(r, g, b, a).id)
          case _ => None
        }
      }
      val qualifiedId = QualifiedId.decodeOpt('qualified_id)
      val id = qualifiedId.map(_.id).getOrElse(UserId('id))
      val domain = Domain(qualifiedId.map(_.domain))
      val pic = getAssets
      val privateMode = decodeOptBoolean('privateMode)
      val ssoId = SSOId.decodeOptSSOId('sso_id)
      val managedBy = ManagedBy.decodeOptManagedBy('managed_by)
      val fields = UserField.decodeOptUserFields('fields)
      UserInfo(
        id, domain, 'name, accentId, 'email, 'phone, Some(pic), decodeOptString('tracking_id) map (TrackingId(_)),
        deleted = 'deleted, handle = 'handle, privateMode = privateMode, service = decodeOptService('service),
        'team, decodeOptISORemoteInstant('expires_at), ssoId = ssoId, managedBy = managedBy, fields = fields)
    }
  }

  def encodeAsset(assets: Seq[ProfilePicture]): JSONArray = {
    val arr = new json.JSONArray()
    assets
      .map { pic =>
        val size = pic.tag match {
          case Preview => "preview"
          case Medium  => "complete"
          case _       => ""
        }

        JsonEncoder { o =>
          o.put("size", size)
          o.put("key", pic.id.str)
          o.put("type", "image")
        }
      }
      .foreach(arr.put)
    arr
  }

  def encodeService(service: Service): JSONObject = JsonEncoder { o =>
    o.put("id", service.id)
    o.put("provider", service.provider)
  }

  def encodeQualifiedId(qualifiedId: QualifiedId): JSONObject = JsonEncoder { o =>
    o.put("id", qualifiedId.id.str)
    o.put("domain", qualifiedId.domain)
  }

  implicit lazy val Encoder: JsonEncoder[UserInfo] = new JsonEncoder[UserInfo] {
    override def apply(info: UserInfo): JSONObject = JsonEncoder { o =>
      o.put("id", info.id.str)
      info.domain.mapOpt(d => o.put("qualified_id", encodeQualifiedId(QualifiedId(info.id, d))))
      info.name.foreach(o.put("name", _))
      info.phone.foreach(p => o.put("phone", p.str))
      info.email.foreach(e => o.put("email", e.str))
      info.accentId.foreach(o.put("accent_id", _))
      info.handle.foreach(h => o.put("handle", h.toString))
      info.trackingId.foreach(id => o.put("tracking_id", id.str))
      info.picture.foreach(ps => o.put("assets", encodeAsset(ps)))
      info.managedBy.foreach(m => o.put("managed_by", m.toString))
    }
  }
}
