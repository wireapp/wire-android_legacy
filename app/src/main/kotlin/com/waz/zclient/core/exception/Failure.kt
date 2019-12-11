package com.waz.zclient.core.exception

/**
 * Base Class for handling errors/failures/exceptions.
 * Every feature specific failure should extend [FeatureFailure] class.
 */
sealed class Failure {
    // Server Connection Failures
    object NoNetworkConnection : Failure()
    object ServerError : Failure()
    object BadRequest : Failure()
    object Unauthorized : Failure()
    object Forbidden : Failure()
    object NotFound : Failure()
    object InternalServerError : Failure()

    /** * Extend this class for feature specific failures.*/
    abstract class FeatureFailure: Failure()
}
