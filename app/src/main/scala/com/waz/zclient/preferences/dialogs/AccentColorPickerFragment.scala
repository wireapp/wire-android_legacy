/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.api.impl.{AccentColor, AccentColors}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.utils.RichView
import com.waz.ZLog.ImplicitTag._

class AccentColorPickerFragment extends DialogFragment with FragmentHelper {

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val layout = LayoutInflater.from(getActivity).inflate(R.layout.preference_dialog_accent_color, null)

    returning(findById[RecyclerView](layout, R.id.rv__accent_color)) { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getContext, LinearLayoutManager.VERTICAL, false))
      rv.setAdapter(new AccentColorAdapter)
    }

    new AlertDialog.Builder(getActivity)
      .setView(layout)
      .setCancelable(true)
      .create
  }

  private class AccentColorAdapter extends RecyclerView.Adapter[AccentColorViewHolder] {

    //TODO - coordinate with BE/Other clients to pass actual color value rather than an arbitrary id
    //At the moment, we have to make sure that color ids don't change, or else different clients will interpret
    //colors differently. This prevents us from making changes to the list of accent colors on the client side
    //For example, we currently don't want to display yellow, so we have to create an array of colors with ids:
    //1, 2, 4, 5, 6, 7.
    private val selectableColors = getContext.getResources.getIntArray(R.array.selectable_accents_color).toSeq.map { c =>
      AccentColors.colors.find(_.getColor() == c).getOrElse(AccentColors.defaultColor)
    }

    def onCreateViewHolder(parent: ViewGroup, viewType: Int) = {
      val view = LayoutInflater.from(getContext).inflate(R.layout.preference_dialog_accent_color_item, parent, false)
      new AccentColorViewHolder(view)
    }

    def onBindViewHolder(holder: AccentColorViewHolder, position: Int) =
      holder.viewColor ! selectableColors(position)

    def getItemCount: Int = selectableColors.length
  }

  private class AccentColorViewHolder(view: View) extends RecyclerView.ViewHolder(view) {

    import Threading.Implicits.Background

    val zms      = inject[Signal[ZMessaging]]
    val viewColor = Signal[AccentColor]()

    val selectionView = itemView.findViewById(R.id.gtv__accent_color__selected).asInstanceOf[View]

    (for {
      vC <- viewColor
      sC <- zms.flatMap(_.users.selfUser).map(_.accent).map(AccentColor(_))
    } yield vC == sC).on(Threading.Ui)(selectionView.setVisible)

    viewColor.map(_.getColor()).on(Threading.Ui)(itemView.setBackgroundColor)

    viewColor.onChanged.on(Threading.Ui) { color =>
      itemView.setOnClickListener(new OnClickListener {
        override def onClick(v: View): Unit =
          zms.map(_.users).head.flatMap(_.updateAccentColor(color)).map(_ => dismiss())
      })
    }
  }
}

object AccentColorPickerFragment {
  val fragmentTag = "AccentColorPickerFragment"
}
