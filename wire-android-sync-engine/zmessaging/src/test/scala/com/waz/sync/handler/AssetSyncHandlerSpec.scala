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
package com.waz.sync.handler

import com.waz.cache._
import com.waz.model.AssetData.RemoteData
import com.waz.model.AssetStatus.{UploadDone, UploadInProgress, UploadNotStarted}
import com.waz.model.GenericContent.EncryptionAlgorithm
import com.waz.model.{Sha256, _}
import com.waz.service.assets.AssetService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.AssetClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.CancellableFuture
import com.waz.utils.sha2
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.resultOf
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Ignore

import scala.annotation.tailrec
import scala.concurrent.Future

//TODO we need to get RandomBytes working on Jenkins before we can re-enable this test...
@Ignore class AssetSyncHandlerSpec extends AndroidFreeSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  val ARB_DATA_SIZE = 10

  val assetClient = stub[AssetClient]

  def sideEffect[A](f: => A): Gen[A] = Gen.resultOf[Unit, A](_ => f)
  implicit lazy val arbAssetId: Arbitrary[AssetId] = Arbitrary(sideEffect(AssetId()))

  lazy val arbAssetData: Arbitrary[AssetData] = Arbitrary(for {
    id            <- arbitrary[AssetId]
    mime          <- Gen.oneOf(Mime.Image.supported.toSeq) // no audio mime types
    sizeInBytes   <- Gen.posNum[Long]
  } yield AssetData(id, mime, sizeInBytes, UploadNotStarted))

  implicit lazy val arbRAssetDataId: Arbitrary[RAssetId] = Arbitrary(sideEffect(RAssetId()))
  implicit lazy val arbAssetToken: Arbitrary[AssetToken] = Arbitrary(resultOf(AssetToken))
  implicit lazy val arbOtrKey: Arbitrary[AESKey] = Arbitrary(sideEffect(AESKey()))
  implicit lazy val arbSha256: Arbitrary[Sha256] = Arbitrary(arbitrary[Array[Byte]].map(b => Sha256(sha2(b))))

  implicit def optGen[T](implicit gen: Gen[T]): Gen[Option[T]] = Gen.frequency((1, Gen.const(None)), (2, gen.map(Some(_))))

  lazy val arbRemoteData: Arbitrary[RemoteData] = Arbitrary(for {
    remoteId      <- optGen(arbitrary[RAssetId])
    token         <- optGen(arbitrary[AssetToken])
    otrKey        <- optGen(arbitrary[AESKey])
    sha           <- optGen(arbitrary[Sha256])
  } yield RemoteData(remoteId, token, otrKey, sha, Some(EncryptionAlgorithm.AES_GCM)))

  @tailrec
  private def sample[T](arb: Arbitrary[T]): T = arb.arbitrary.sample match {
    case None => sample(arb)
    case Some(data) => data
  }

  private def arbitraryAssetData: List[AssetData] = (1 to 10).map(_ => sample(arbAssetData)).toList

//  feature("upload image") {
//    scenario("upload full image") {
//      arbitraryAssetData.foreach { asset =>
//        val remoteData = sample(arbRemoteData)
//        val assetUploadStarted = asset.copy(status = UploadInProgress)
//        val assetWithRemoteData = assetUploadStarted.copyWithRemoteData(remoteData)
//        val assetUploadDone = assetWithRemoteData.copy(status = UploadDone)
//
//        val assets = mock[AssetService]
//        val otrSync = mock[OtrSyncHandler]
//        val cacheService = mock[CacheService]
//
//        val cacheEntry = new CacheEntry(CacheEntryData(assetWithRemoteData.cacheKey), cacheService)
//
//        inSequence {
//          (assets.updateAsset _).expects(asset.id, *).returns(Future(Some(assetUploadStarted)))
//          (assets.getLocalData _).expects(asset.id).returns(CancellableFuture(Some(LocalData.Empty)))
//          (otrSync.uploadAssetDataV3 _).expects(LocalData.Empty, *, asset.mime).returns(CancellableFuture(Right(remoteData)))
//          (assets.updateAsset _).expects(asset.id, *).returns(Future(Some(assetWithRemoteData)))
//        }
//
//        val handler = new AssetSyncHandler(cacheService, assetClient, assets, otrSync)
//        result(handler.uploadAssetData(asset.id)).right.get shouldEqual Some(assetUploadDone)
//      }
//    }
//  }

}
