package com.waz.zclient.testutils

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import org.amshove.kluent.shouldBe

infix fun Either<Failure, *>.verifyLeft(other: Failure) = this.fold({it shouldBe other}, {})

infix fun <T> Either<*, T>.verifyRight(other: T) = this.fold({}) { it shouldBe other }
