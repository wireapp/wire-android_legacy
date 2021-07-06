package com.waz.service

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.specs.AndroidFreeSpec

class SearchQuerySpec extends AndroidFreeSpec with DerivedLogTag {
  scenario("parse a name query") {
    val query = SearchQuery("aaa")
    query.query shouldEqual "aaa"
    query.isEmpty shouldEqual false
    query.hasDomain shouldEqual false
    query.handleOnly shouldEqual false
  }

  scenario("parse a handle query") {
    val query = SearchQuery("@aaa")
    query.query shouldEqual "aaa"
    query.isEmpty shouldEqual false
    query.hasDomain shouldEqual false
    query.handleOnly shouldEqual true
  }

  scenario("parse an empty query") {
    val query = SearchQuery("")
    query.query shouldEqual ""
    query.isEmpty shouldEqual true
    query.hasDomain shouldEqual false
    query.handleOnly shouldEqual false
  }

  scenario("parse an empty query with the @ sign") {
    val query = SearchQuery("@")
    query.query shouldEqual ""
    query.isEmpty shouldEqual true
    query.hasDomain shouldEqual false
    query.handleOnly shouldEqual true
  }

  scenario("parse a query with a domain") {
    val query = SearchQuery("aaa@chala.wire.link")
    query.query shouldEqual "aaa"
    query.isEmpty shouldEqual false
    query.hasDomain shouldEqual true
    query.domain shouldEqual "chala.wire.link"
    query.handleOnly shouldEqual false
  }

  scenario("parse a query with a domain and the @ sign") {
    val query = SearchQuery("@aaa@chala.wire.link")
    query.query shouldEqual "aaa"
    query.isEmpty shouldEqual false
    query.hasDomain shouldEqual true
    query.domain shouldEqual "chala.wire.link"
    query.handleOnly shouldEqual true
  }
}
