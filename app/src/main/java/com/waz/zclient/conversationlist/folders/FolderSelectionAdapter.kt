package com.waz.zclient.conversationlist.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.waz.zclient.R

class FolderSelectionAdapter(private val items: ArrayList<String> = arrayListOf(),
                             private var currentSelectedIndex: Int? = null,
                             private val onItemSelected: (Int) -> Unit)
    : RecyclerView.Adapter<FolderSelectionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderSelectionViewHolder =
        FolderSelectionViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_folder_selection, parent, false),
            ::onNewItemSelected
        )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: FolderSelectionViewHolder, position: Int) {
        holder.bind(items[position], position == currentSelectedIndex)
    }

    private fun onNewItemSelected(index: Int) {
        val oldIndex = currentSelectedIndex
        currentSelectedIndex = index
        oldIndex?.let { notifyItemChanged(it) }
        currentSelectedIndex?.let {
            notifyItemChanged(it)
            onItemSelected(it)
        }
    }

}

class FolderSelectionViewHolder(view: View, private val onItemSelected: (Int) -> Unit) : RecyclerView.ViewHolder(view) {

    private val textViewName: TextView by lazy {
        itemView.findViewById<TextView>(R.id.item_folder_selection_textview_folder_name)
    }

    private val textViewIcon: TextView by lazy {
        itemView.findViewById<TextView>(R.id.item_folder_selection_textview_icon)
    }

    fun bind(name: String, selected: Boolean) {
        textViewName.text = name
        textViewIcon.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        itemView.setOnClickListener {
            onItemSelected(adapterPosition)
        }
    }

}
