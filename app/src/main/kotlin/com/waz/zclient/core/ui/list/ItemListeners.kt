package com.waz.zclient.core.ui.list

interface OnItemClickListener {
    fun onItemClicked(position: Int)
}

interface RecyclerViewItemClickListener<in Item> {
    fun onItemClicked(item: Item)
}
