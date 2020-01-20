package com.waz.zclient.settings.account.color

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R
import com.waz.zclient.core.extension.invisible
import com.waz.zclient.core.extension.visible
import kotlinx.android.synthetic.main.item_accent_color.view.*
import kotlinx.coroutines.InternalCoroutinesApi

@InternalCoroutinesApi
class AccentColorAdapter(private val colors: List<AccentColor>, private val defaultColorId: Int, private val listener: OnAccentColorChangedListener?) :
    RecyclerView.Adapter<AccentColorAdapter.AccentColorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccentColorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_accent_color, parent, false)
        return AccentColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccentColorViewHolder, position: Int) {
        holder.bind(colors[position],defaultColorId)
    }
    
    override fun getItemCount(): Int = colors.size

    inner class AccentColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(color: AccentColor,defaultColorId: Int) {
            with(itemView) {
                setBackgroundColor(color.colorValue)
                when (color.id) {
                    defaultColorId -> accent_color_selection_image.visible()
                    else -> accent_color_selection_image.invisible()
                }
                setOnClickListener { listener?.onAccentColorChanged(color.id) }
            }
        }
    }
}
