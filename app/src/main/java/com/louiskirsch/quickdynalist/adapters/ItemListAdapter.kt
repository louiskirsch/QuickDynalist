package com.louiskirsch.quickdynalist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.jobs.Bookmark
import kotlinx.android.synthetic.main.item_list_item.view.*

class ItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemText = itemView.itemText
}

class ItemListAdapter(items: List<Bookmark>): RecyclerView.Adapter<ItemListViewHolder>() {

    private val items = items.toMutableList()

    init {
        setHasStableIds(true)
    }

    fun updateItems(newItems: List<Bookmark>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ItemListViewHolder(inflater.inflate(R.layout.item_list_item, parent, false))
    }

    override fun getItemId(position: Int): Long {
        val item = items[position]
        return item.id?.hashCode()?.toLong() ?: item.clientId
    }

    override fun onBindViewHolder(holder: ItemListViewHolder, position: Int) {
        holder.itemText.text = items[position].name
    }
}
