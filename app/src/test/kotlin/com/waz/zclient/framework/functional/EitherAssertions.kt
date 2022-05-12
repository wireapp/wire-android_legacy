package com.waz.zclient.framework.functional

import com.waz.zclient.core.functional.Either
import junit.framework.Assert.fail

fun <L, R> Either<L, R>.assertLeft(leftAssertion: (L) -> Unit) =
    this.fold({ leftAssertion(it) }) { fail("Expected a Left value but got Right") }!!

fun <L, R> Either<L, R>.assertRight(rightAssertion: (R) -> Unit) =
    this.fold({ fail("Expected a Right value but got Left") }) { rightAssertion(it) }!!

fun <L> Either<L, Unit>.assertRight() = assertRight { }
