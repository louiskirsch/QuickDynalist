package com.louiskirsch.quickdynalist.adapters

import android.content.Context
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

class CachedDynalistItem(val item: DynalistItem, context: Context) {
    val spannableText = item.getSpannableText(context)
    val spannableNotes = item.getSpannableNotes(context)
    val spannableChildren = item.getSpannableChildren(context)
}

class ItemListAdapter: RecyclerView.Adapter<ItemListViewHolder>() {

    private val items = ArrayList<CachedDynalistItem>()

    init {
        setHasStableIds(true)
    }

    fun updateItems(newItems: List<CachedDynalistItem>) {
        val oldSize = items.size
        if (newItems.take(oldSize).map { getItemId(it) } == items.map { getItemId(it) } ) {
            val newCount = newItems.size - items.size
            items.addAll(newItems.takeLast(newCount))
            notifyItemRangeInserted(oldSize, newCount)
        } else {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ItemListViewHolder(inflater.inflate(R.layout.item_list_item, parent, false))
    }

    private fun getItemId(item: CachedDynalistItem): Long {
        return item.item.clientId
    }

    override fun getItemId(position: Int): Long = getItemId(items[position])

    override fun onBindViewHolder(holder: ItemListViewHolder, position: Int) {
        val item = items[position]
        holder.itemText.text = item.spannableText
        holder.itemNotes.visibility = if (item.spannableNotes.isEmpty()) View.GONE else View.VISIBLE
        holder.itemNotes.text = item.spannableNotes
        holder.itemChildren.visibility = if (item.spannableChildren.isEmpty()) View.GONE else View.VISIBLE
        holder.itemChildren.text = item.spannableChildren
    }
}
