package com.waz.service.assets

import java.io.{InputStream, OutputStream}

import com.waz.model.{Dim2, Mime}

trait ImageRecoder {
  def recode(dim: Dim2, targetMime: Mime, scaleTo: Int, source: () => InputStream, target: () => OutputStream): Unit
}
