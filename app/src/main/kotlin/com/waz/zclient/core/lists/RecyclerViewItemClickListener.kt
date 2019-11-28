package com.waz.zclient.core.lists

interface RecyclerViewItemClickListener<in T> {
    fun onItemClicked(item: T)
}
