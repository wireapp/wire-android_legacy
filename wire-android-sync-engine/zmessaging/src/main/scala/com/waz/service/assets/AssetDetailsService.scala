package com.waz.service.assets

import com.waz.model.Mime

trait AssetDetailsService {
  def extract(content: PreparedContent): (AssetDetails, Mime)
}
