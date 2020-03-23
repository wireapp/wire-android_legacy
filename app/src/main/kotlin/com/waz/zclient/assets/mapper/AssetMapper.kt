package com.waz.zclient.assets.mapper

import okhttp3.ResponseBody
import java.io.InputStream

class AssetMapper {
    fun toInputStream(responseBody: ResponseBody): InputStream = responseBody.byteStream()
}
