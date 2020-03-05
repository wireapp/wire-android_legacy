package com.waz.zclient.core.exception

/**
 * Base Class for handling errors/failures/exceptions.
 * Every feature specific failure should extend [FeatureFailure] class.
 */

sealed class Failure

sealed class NetworkFailure : Failure()
sealed class DatabaseFailure : Failure()

object NetworkConnection : NetworkFailure()
object ServerError : NetworkFailure()
object BadRequest : NetworkFailure()
object Unauthorized : NetworkFailure()
object Forbidden : NetworkFailure()
object NotFound : NetworkFailure()
object Cancelled : NetworkFailure()
object InternalServerError : NetworkFailure()
object Conflict : NetworkFailure()

object EmptyResponseBody : NetworkFailure()

object DatabaseStateError : DatabaseFailure()
object SQLError : DatabaseFailure()
object DatabaseError : DatabaseFailure()

//TODO: Improve to a more sufficient error propagation for Flow "data flows"
data class GenericUseCaseError(val throwable: Throwable) : Failure()

/** * Extend this class for UseCase specific failures.*/
abstract class FeatureFailure : Failure()
