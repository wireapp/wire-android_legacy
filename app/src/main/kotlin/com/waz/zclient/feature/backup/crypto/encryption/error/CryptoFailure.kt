package com.waz.zclient.feature.backup.crypto.encryption.error

import com.waz.zclient.core.exception.FeatureFailure

object DecryptionFailed : CryptoFailure()
object UnableToReadMetaData : CryptoFailure()
object HashesDoNotMatch : CryptoFailure()

object HashingFailed : CryptoFailure()
object HashWrongSize : CryptoFailure()
object HashInvalid : CryptoFailure()
object EncryptionFailed : CryptoFailure()

object UnsatisfiedLink : CryptoFailure()
object InvalidKeyLength : CryptoFailure()
object InvalidHeaderLength : CryptoFailure()
sealed class CryptoFailure : FeatureFailure()
