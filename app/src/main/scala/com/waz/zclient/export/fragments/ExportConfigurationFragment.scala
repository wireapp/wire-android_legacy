package com.waz.zclient.export.fragments;

import java.text.DateFormat
import java.util.Date

import android.app.{Activity, DatePickerDialog, TimePickerDialog}
import android.os.{Bundle, Environment}
import android.content.Intent
import android.provider.DocumentsContract
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{Button, CompoundButton, LinearLayout, Switch, Toast}
import com.google.android.material.textfield.TextInputLayout
import com.waz.model.RemoteInstant
import com.waz.utils.returningF
import com.waz.zclient.`export`.ExportController
import com.waz.zclient.{FragmentHelper, R, WireApplication}

class ExportConfigurationFragment extends FragmentHelper {
  private val SELECT_FILE_REQUEST = 1
  private lazy val exportController         = inject[ExportController]
  private lazy val exportButton=view[Button](R.id.b__export_start).get
  private lazy val exportIncludeMediaSwitch=view[Switch](R.id.export_include_media).get
  private lazy val exportIncludeHtmlSwitch=view[Switch](R.id.export_include_html).get
  private lazy val exportLimitFromSwitch=view[Switch](R.id.export_limit_from).get
  private lazy val exportLimitToSwitch=view[Switch](R.id.export_limit_to).get
  private lazy val filePathInput=view[TextInputLayout](R.id.b__export_file_input).get
  private lazy val dateFromInput=view[Button](R.id.export_date_from).get
  private lazy val dateToInput=view[Button](R.id.export_date_to).get
  private lazy val exportLoadingIndicator=view[LinearLayout](R.id.export_loading_indicator).get
  private var dateFrom: Option[RemoteInstant] = None
  private var dateTo: Option[RemoteInstant] = None

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_export_configuration, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    exportButton.setEnabled(false)
    exportButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        exportLoadingIndicator.setVisibility(View.VISIBLE)
        exportController.`export`(()=>{
          getContext.asInstanceOf[Activity].runOnUiThread(new Runnable {
            override def run(): Unit = {
              exportLoadingIndicator.setVisibility(View.GONE)
              Toast.makeText(getContext,WireApplication.APP_INSTANCE.getApplicationContext.getString(R.string.export_done),Toast.LENGTH_LONG)
            }
          })
        })
      }
    })

    exportIncludeMediaSwitch.setChecked(exportController.exportFiles)
    exportIncludeMediaSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {exportController.exportFiles=isChecked}
    })

    exportIncludeHtmlSwitch.setChecked(exportController.includeHtml)
    exportIncludeHtmlSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {exportController.includeHtml=isChecked}
      })

    dateFrom=exportController.timeFrom
    exportLimitFromSwitch.setChecked(dateFrom.nonEmpty)
    exportLimitFromSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if(isChecked)
          exportController.timeFrom=dateFrom
        else
          exportController.timeFrom=None
      }
    })
    dateFromInput.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        showDateTimePicker(dateFrom,r=>{
          dateFrom=Some(r)
          val date = new Date()
          date.setTime(r.toEpochMilli)
          val format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
          dateFromInput.setText(format.format(date))
          if(exportLimitFromSwitch.isChecked)
            exportController.timeFrom=dateFrom;
        })
      }
    })

    dateTo=exportController.timeTo
    exportLimitToSwitch.setChecked(dateTo.nonEmpty)
    exportLimitToSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if(isChecked)
          exportController.timeTo=dateTo
        else
          exportController.timeTo=None
      }
    })
    dateToInput.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        showDateTimePicker(dateTo,r=>{
          dateTo=Some(r)
          val date = new Date()
          date.setTime(r.toEpochMilli)
          val format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
          dateToInput.setText(format.format(date))
          if(exportLimitToSwitch.isChecked)
            exportController.timeTo=dateTo
        })
      }
    })

    filePathInput.getEditText.setEnabled(false)
    returningF(findById(R.id.b__export_file_select)){ b: Button =>
      b.setOnClickListener(new View.OnClickListener {
        override def onClick(v: View): Unit = {
          selectFile()
        }
      })
    }
  }

  import android.icu.util.Calendar
  import android.widget.DatePicker
  import android.widget.TimePicker

  def showDateTimePicker(time: Option[RemoteInstant], callback: RemoteInstant => Unit): Unit = {
    val currentDate = Calendar.getInstance
    val date = Calendar.getInstance
    if(time.nonEmpty)
      date.setTimeInMillis(time.get.toEpochMilli)
    new DatePickerDialog(getContext, new DatePickerDialog.OnDateSetListener() {
      override def onDateSet(view: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int): Unit = {
        date.set(year, monthOfYear, dayOfMonth)
        new TimePickerDialog(getContext, new TimePickerDialog.OnTimeSetListener() {
          def onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int): Unit = {
            date.set(Calendar.HOUR_OF_DAY, hourOfDay)
            date.set(Calendar.MINUTE, minute)
            callback(RemoteInstant.ofEpochMilli(date.getTimeInMillis))
          }
        }, currentDate.get(Calendar.HOUR_OF_DAY), currentDate.get(Calendar.MINUTE), false).show()
      }
    }, currentDate.get(Calendar.YEAR), currentDate.get(Calendar.MONTH), currentDate.get(Calendar.DATE)).show()
  }

  def selectFile(): Unit = {
    val intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType("application/zip")
    intent.putExtra(Intent.EXTRA_TITLE, "chatexport.zip")
    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.DIRECTORY_DOCUMENTS)
    startActivityForResult(intent, SELECT_FILE_REQUEST)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent): Unit = {
    if (requestCode == SELECT_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
      if (resultData != null) {
        exportController.exportFile = Some(resultData.getData)
        filePathInput.getEditText.setText(exportController.exportFile.get.toString)
        exportButton.setEnabled(true)
      }
    }
  }
}

object ExportConfigurationFragment {
    val Tag: String = getClass.getSimpleName
}
