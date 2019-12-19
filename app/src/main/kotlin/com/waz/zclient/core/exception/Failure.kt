package com.waz.zclient.core.exception

/**
 * Base Class for handling errors/failures/exceptions.
 * Every feature specific failure should extend [FeatureFailure] class.
 */

sealed class Failure

sealed class NetworkFailure : Failure()
sealed class DatabaseFailure : Failure()

object NetworkConnection : NetworkFailure()
object NetworkServiceError : NetworkFailure()
data class HttpError(val errorCode: Int, val errorMessage: String) : NetworkFailure()

object DatabaseError : DatabaseFailure()

/** * Extend this class for feature specific failures.*/
abstract class FeatureFailure : Failure()
