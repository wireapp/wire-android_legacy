package com.waz.zclient.core.images

import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import org.junit.Test

class AssetKeyTest {

    @Test
    fun `given two instances with same key, width, height and option, when equals is called, returns true`() {
        val assetKey1 = AssetKey(KEY, WIDTH, HEIGHT, options)
        val assetKey2 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(assetKey1.equals(assetKey2))
    }

    @Test
    fun `given two instances with at least one property different, when equals is called, returns false`() {
        val assetKey1 = AssetKey("otherKey", WIDTH, HEIGHT, options)
        val assetKey2 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(!assetKey1.equals(assetKey2))

        val assetKey3 = AssetKey(KEY, 4, HEIGHT, options)
        val assetKey4 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(!assetKey3.equals(assetKey4))

        val assetKey5 = AssetKey(KEY, WIDTH, 5, options)
        val assetKey6 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(!assetKey5.equals(assetKey6))

        val assetKey7 = AssetKey(KEY, WIDTH, HEIGHT, optionsWithMemory)
        val assetKey8 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(!assetKey7.equals(assetKey8))
    }

    @Test
    fun `given two instances with same key, width, height and option, when hashCode is called, returns true`() {
        val assetKey1 = AssetKey(KEY, WIDTH, HEIGHT, options)
        val assetKey2 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(assetKey1.hashCode() == assetKey2.hashCode())
    }

    @Test
    fun `given two instances with at least one property different, when hashCode is called, returns false`() {
        val assetKey1 = AssetKey("otherKey", WIDTH, HEIGHT, options)
        val assetKey2 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(assetKey1.hashCode() != assetKey2.hashCode())

        val assetKey3 = AssetKey(KEY, 4, HEIGHT, options)
        val assetKey4 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(assetKey3.hashCode() != assetKey4.hashCode())

        val assetKey5 = AssetKey(KEY, WIDTH, 5, options)
        val assetKey6 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(assetKey5.hashCode() != assetKey6.hashCode())

        val assetKey7 = AssetKey(KEY, WIDTH, HEIGHT, optionsWithMemory)
        val assetKey8 = AssetKey(KEY, WIDTH, HEIGHT, options)
        assert(assetKey7.hashCode() != assetKey8.hashCode())
    }

    companion object {
        private const val KEY = "key"
        private const val WIDTH = 2
        private const val HEIGHT = 3
        private val options = Options()
        private val optionsWithMemory = Options().set(Option.memory("memory"), "memory")
    }
}
