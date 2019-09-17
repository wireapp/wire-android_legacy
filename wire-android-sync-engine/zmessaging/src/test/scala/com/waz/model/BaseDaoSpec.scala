package com.waz.model

import java.util.UUID.randomUUID

import com.waz.DisabledTrackingService
import com.waz.db.{BaseDao, Dao, DaoDB, Table, ZMessagingDB}
import com.waz.utils.wrappers.{DB, DBCursor}
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
class BaseDaoSpec extends FeatureSpec with Matchers with RobolectricTests {

  case class TestModel(id: Uid)

  object TestDao extends Dao[TestModel, Uid] {
    import com.waz.db.Col._
    val Id = id[Uid]('_id, "PRIMARY KEY").apply(_.id)
    override val idCol = Id
    override val table: Table[TestModel] = Table("TestTable", Id)
    override def apply(implicit c: DBCursor): TestModel = TestModel(Id)
  }

  def dummyData(size: Int): Seq[TestModel] = (0 until size).map(_ => TestModel(Uid()))

  def withDB(f: DB => Unit): Unit = {
    val dbHelper = new DaoDB(Robolectric.application, s"testDB-$randomUUID", null, 1, List(TestDao), List.empty, DisabledTrackingService)
    try f(dbHelper.getWritableDatabase) finally dbHelper.close()
  }

  scenario("findInSet queries in batches") (withDB { implicit db =>
    // Given
    val numberOfRows = 1000
    val data = dummyData(numberOfRows)

    TestDao insertOrReplace data
    TestDao.list.size shouldEqual numberOfRows

    // When
    val cursors = TestDao.findInSet(TestDao.Id, data.map(_.id).toSet)

    // Then
    cursors.size shouldEqual 2
    cursors.map(_.getCount) shouldEqual List.fill(2)(BaseDao.MaxQueryVariables)
  })

  scenario("iterating over several cursors") (withDB { implicit db =>
    // Given
    val numberOfRows = 1200
    val data = dummyData(numberOfRows)
    val ids = data.map(_.id).toSet

    TestDao insertOrReplace data
    TestDao.list.size shouldEqual numberOfRows
    
    // When
    val cursors = TestDao.findInSet(TestDao.Id, ids)
    val result = TestDao.iteratingMultiple(cursors).acquire(_.map(_.id).toSet)

    // Then
    result shouldEqual ids
  })
}
