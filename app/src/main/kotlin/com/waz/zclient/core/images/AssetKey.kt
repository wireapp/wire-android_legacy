package com.waz.zclient.core.images

import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import java.security.MessageDigest

class AssetKey(
    private val key: String,
    private val width: Int,
    private val height: Int,
    private val options: Options
) : Key {
    override fun hashCode(): Int = toString().hashCode()

    override fun equals(other: Any?): Boolean {
        return other is AssetKey &&
            other.key == this.key && other.width == this.width &&
            other.height == this.height && other.options == this.options
    }

    override fun toString(): String = "$key-$width-$height-$options"

    override fun updateDiskCacheKey(messageDigest: MessageDigest) =
        messageDigest.update(toString().toByteArray(Key.CHARSET))
}
