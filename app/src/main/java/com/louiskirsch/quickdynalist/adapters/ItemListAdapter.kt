package com.louiskirsch.quickdynalist.adapters

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.DynalistItem
import kotlinx.android.synthetic.main.item_list_item.view.*

class ItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemText = itemView.itemText!!
    val itemNotes = itemView.itemNotes!!
    val itemChildren = itemView.itemChildren!!
}

class ItemListAdapter(items: List<DynalistItem>): RecyclerView.Adapter<ItemListViewHolder>() {

    private val items = items.filter { it.name.trim().isNotEmpty() }.toMutableList()

    init {
        setHasStableIds(true)
    }

    fun updateItems(newItems: List<DynalistItem>) {
        val oldSize = items.size
        val filtered = newItems.filter { it.name.trim().isNotEmpty() }
        if (filtered.take(oldSize).map { getItemId(it) } == items.map { getItemId(it) } ) {
            val newCount = filtered.size - items.size
            items.addAll(filtered.takeLast(newCount))
            notifyItemRangeInserted(oldSize, newCount)
        } else {
            items.clear()
            items.addAll(filtered)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ItemListViewHolder(inflater.inflate(R.layout.item_list_item, parent, false))
    }

    private fun getItemId(item: DynalistItem): Long {
        return item.clientId
    }

    override fun getItemId(position: Int): Long = getItemId(items[position])

    override fun onBindViewHolder(holder: ItemListViewHolder, position: Int) {
        val item = items[position]
        val text = item.getSpannableText(holder.itemText.context)
        holder.itemText.text = text
        val note = item.getSpannableNotes(holder.itemNotes.context)
        holder.itemNotes.visibility = if (note.isEmpty()) View.GONE else View.VISIBLE
        holder.itemNotes.text = note
        val children = item.getBulletedChildren(holder.itemNotes.context)
        holder.itemChildren.visibility = if (children.isEmpty()) View.GONE else View.VISIBLE
        holder.itemChildren.text = children
    }
}
