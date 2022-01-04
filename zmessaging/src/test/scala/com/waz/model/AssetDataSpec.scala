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

import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.AssetMetaData.Loudness
import com.waz.model.AssetStatus.{UploadInProgress, UploadNotStarted}
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.Matchers._
import com.waz.utils.wrappers._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.alphaNumChar
import org.scalacheck.{Gen, _}
import org.scalatest.Ignore
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, TableDrivenPropertyChecks}
import org.threeten.bp.Duration

@Ignore
class AssetDataSpec extends AndroidFreeSpec with TableDrivenPropertyChecks with GeneratorDrivenPropertyChecks {

  import com.waz.model.AssetData._

  lazy val alphaNumStr = Gen.nonEmptyListOf(alphaNumChar).map(_.mkString)
  lazy val genDimension = Gen.chooseNum(0, 10000)
  def sideEffect[A](f: => A): Gen[A] = Gen.resultOf[Unit, A](_ => f)

  implicit def optGen[T](implicit gen: Gen[T]): Gen[Option[T]] = Gen.frequency((1, Gen.const(None)), (2, gen.map(Some(_))))

  implicit lazy val arbDim2: Arbitrary[Dim2] = Arbitrary(for (w <- genDimension; h <- genDimension) yield Dim2(w, h))
  implicit lazy val arbDuration: Arbitrary[Duration] = Arbitrary(Gen.posNum[Long] map Duration.ofMillis)
  implicit lazy val arbAssetId: Arbitrary[AssetId]       = Arbitrary(sideEffect(AssetId()))
  implicit lazy val arbRConvId: Arbitrary[RConvId]       = Arbitrary(sideEffect(RConvId()))

  implicit lazy val arbLoudness: Arbitrary[Loudness] = Arbitrary(for {
    len <- Gen.chooseNum(1,10)
    floats <- Gen.listOfN(len, Gen.chooseNum(0.0f, 1.0f))
  } yield Loudness(floats.toVector))

  implicit lazy val arbUri: Arbitrary[URI] = Arbitrary(for {
    scheme <- Gen.oneOf("file", "content", "http")
    path <- alphaNumStr
  } yield URI.parse(s"$scheme://$path"))

  implicit lazy val arbImageMetaData: Arbitrary[AssetMetaData.Image] = Arbitrary(for (d <- arbitrary[Dim2]; t <- Gen.oneOf(Medium, Preview)) yield AssetMetaData.Image(d, t))
  implicit lazy val arbVideoMetaData: Arbitrary[AssetMetaData.Video] = Arbitrary(Gen.resultOf(AssetMetaData.Video(_: Dim2, _: Duration)))
  implicit lazy val arbAudioMetaData: Arbitrary[AssetMetaData.Audio] = Arbitrary(Gen.resultOf(AssetMetaData.Audio(_: Duration, _: Option[Loudness])))

  implicit lazy val arbMetaData: Arbitrary[AssetMetaData] = Arbitrary(Gen.oneOf(arbImageMetaData.arbitrary, arbVideoMetaData.arbitrary, arbAudioMetaData.arbitrary))

  implicit lazy val arbAssetData: Arbitrary[AssetData] = Arbitrary(for {
    id            <- arbitrary[AssetId]
    mime          <- Gen.oneOf(Seq(Mime.Default) ++ Mime.Video.supported ++ Mime.Image.supported) // no audio mime types
    sizeInBytes   <- Gen.posNum[Long]
    name          <- optGen(alphaNumStr)
    source        <- optGen(arbitrary[URI])
  } yield AssetData(id, mime, sizeInBytes, UploadNotStarted, None, None, None, None, None, name, None, None, source, None, None))

  private def arbitraryAssetData: List[AssetData] = (1 to 10).flatMap(_ => arbAssetData.arbitrary.sample).toList

  // TODO: Right now the default implicit AssetData JSON decoder is v2 AnyAssetDataDecoder. FInd a way to change it

  feature("json serialization") {
    scenario("Random metadata") {
      forAll((_: AssetMetaData) should beUnchangedByEncodingAndDecoding[AssetMetaData])
    }
  }

  feature("Database") {

    scenario("Store a list of assets and retrieve them again") {

      implicit val db = mock[DB]

      val assets = arbitraryAssetData.toVector
      val values = assets.map(AssetDataDao.values)

      val stmt = mock[DBStatement]
      val cursor = mock[DBCursor]
      inSequence {
        (db.delete _).expects("Assets", null, null)
        (db.inTransaction _).expects().returning(true)
        (db.compileStatement _).expects(*).returning(stmt)
        values.foreach(v => {
          (stmt.bindString _).expects(1, v.getAsString("_id"))
          (stmt.bindString _).expects(2, v.getAsString("asset_type"))
          (stmt.bindString _).expects(3, v.getAsString("data"))
          (stmt.execute _).expects()
        })
        (stmt.close _).expects()

        (db.query _).expects("Assets", null, null, null, null, null, null, null).returning(cursor)
        (cursor.moveToFirst _).expects()
        values.foreach(v => {
          (cursor.isClosed _).expects().returning(false)
          (cursor.isAfterLast _).expects().returning(false)
          (cursor.getColumnIndex _).expects("asset_type").returning(1)
          (cursor.getString _).expects(1).returning(v.getAsString("asset_type"))
          (cursor.getColumnIndex _).expects("data").returning(0)
          (cursor.getString _).expects(0).returning(v.getAsString("data"))
          (cursor.moveToNext _).expects().returning(true)
        })
        (cursor.isClosed _).expects().returning(false)
        (cursor.isAfterLast _).expects().returning(true)
        (cursor.close _).expects()
      }

      AssetDataDao.deleteAll
      AssetDataDao.insertOrReplace(assets)
      AssetDataDao.list shouldEqual assets
    }
  }

  feature("AnyAssetData.updated") {

    val id = AssetId()
    val conv = RConvId()
    val mime = Mime("text/plain")
    lazy val asset = AssetData(id = id, mime = mime, sizeInBytes = 100, status = UploadInProgress, name = Some("file.txt"))

  }
}
