package com.waz.service.assets

import scala.concurrent.Future

trait AssetPreviewService {
  def extractPreview(rawAsset: UploadAsset, content: PreparedContent): Future[Content]
}
