package com.louiskirsch.quickdynalist.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.DynalistItem
import kotlinx.android.synthetic.main.item_list_item.view.*
import nl.pvdberg.hashkode.compareFields
import nl.pvdberg.hashkode.hashKode

class ItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemText = itemView.itemText!!
    val itemNotes = itemView.itemNotes!!
    val itemChildren = itemView.itemChildren!!
}

class CachedDynalistItem(val item: DynalistItem, context: Context) {
    val spannableText = item.getSpannableText(context)
    val spannableNotes = item.getSpannableNotes(context)
    val spannableChildren = item.getSpannableChildren(context, 5)

    override fun equals(other: Any?) = compareFields(other) {
        compareField(CachedDynalistItem::spannableText)
        compareField(CachedDynalistItem::spannableNotes)
        compareField(CachedDynalistItem::spannableChildren)
        compareField(CachedDynalistItem::item)
    }

    override fun hashCode() = hashKode(spannableText, spannableNotes, spannableChildren, item)
}

class ItemListAdapter: RecyclerView.Adapter<ItemListViewHolder>() {

    private val items = ArrayList<CachedDynalistItem>()
    var onClickListener: ((DynalistItem) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    fun updateItems(newItems: List<CachedDynalistItem>) {
        val update = {
            items.clear()
            items.addAll(newItems)
            Unit
        }
        val oldSize = items.size
        if (newItems.take(oldSize) == items) {
            val newCount = newItems.size - oldSize
            update()
            notifyItemRangeInserted(oldSize, newCount)
        } else {
            update()
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ItemListViewHolder(inflater.inflate(R.layout.item_list_item, parent, false))
    }

    private fun getItemId(item: CachedDynalistItem): Long = item.item.clientId
    override fun getItemId(position: Int): Long = getItemId(items[position])

    override fun onBindViewHolder(holder: ItemListViewHolder, position: Int) {
        val item = items[position]
        holder.itemText.text = item.spannableText
        holder.itemNotes.visibility = if (item.spannableNotes.isEmpty()) View.GONE else View.VISIBLE
        holder.itemNotes.text = item.spannableNotes
        holder.itemChildren.visibility = if (item.spannableChildren.isEmpty()) View.GONE else View.VISIBLE
        holder.itemChildren.text = item.spannableChildren
        holder.itemView.setOnClickListener { onClickListener?.invoke(items[position].item) }
    }
}
