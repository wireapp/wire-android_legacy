package com.waz.zclient.core.lists

interface OnItemClickListener {
    fun onItemClicked(position: Int)
}

interface RecyclerViewItemClickListener<in Item> {
    fun onItemClicked(item: Item)
}
