package com.waz.zclient.`export`

import java.io.{FileOutputStream, InputStream, OutputStream}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import android.os.ParcelFileDescriptor
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.WireApplication
import com.waz.zclient.R
import com.waz.zclient.log.LogUI.{showString, verbose, _}

import scala.collection.mutable

class ExportZip(fileDescriptor: ParcelFileDescriptor) extends DerivedLogTag {
  private val fileOutputStream = new FileOutputStream(fileDescriptor.getFileDescriptor)
  private val zip = new ZipOutputStream(fileOutputStream)
  private val files = mutable.HashSet[String]()
  def writeFile(filepath: String, in: InputStream): Unit = {
    this.synchronized{
      if(files.contains(filepath)){
        verbose(l"ZIP - File already exists: ${showString(filepath)}")
      } else {
        files.add(filepath)
        zip.putNextEntry(new ZipEntry(filepath))
        val b = new Array[Byte](1024)
        var count = 0
        while({count=in.read(b);count}>0) zip.write(b,0,count)
        zip.closeEntry()
      }
    }
  }
  def writeFile(filepath: String, callback: OutputStream => Unit): Unit = {
    this.synchronized {
      if(files.contains(filepath)){
        verbose(l"ZIP - File already exists: ${showString(filepath)}")
      } else {
        files.add(filepath)
        zip.putNextEntry(new ZipEntry(filepath))
        callback.apply(zip)
        zip.closeEntry()
      }
    }
  }
  def fileExists(filepath: String): Boolean ={
    files.contains(filepath)
  }
  def close(): Unit = {
    this.synchronized {
      zip.close()
      fileOutputStream.close()
      fileDescriptor.close()
    }
  }
  def addHtmlViewerFiles(): Unit = {
    val is=WireApplication.APP_INSTANCE.getResources.openRawResource(R.raw.export_html_viewer)
    val zipIs=new ZipInputStream(is)
    var zipEntry=zipIs.getNextEntry
    while(zipEntry!=null){
      writeFile(zipEntry.getName,zipIs)
      zipEntry=zipIs.getNextEntry
    }
    zipIs.close()
    is.close()
  }
}
