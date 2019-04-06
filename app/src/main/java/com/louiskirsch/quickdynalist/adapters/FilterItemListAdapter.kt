package com.louiskirsch.quickdynalist.adapters

import android.content.Context
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.text.ThemedSpan
import kotlinx.android.synthetic.main.search_list_item.view.*

class FilterItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemParent = itemView.itemParent!!
    val itemText = itemView.itemText!!
    val itemNotes = itemView.itemNotes!!
    val itemChildren = itemView.itemChildren!!
}

class FilterItemListAdapter(context: Context): RecyclerView.Adapter<FilterItemListViewHolder>() {

    private val items = ArrayList<CachedDynalistItem>()
    private val highlightColor = context.getColor(R.color.searchHighlight)

    var searchTerm: String = ""
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    var onClickListener: ((DynalistItem) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    fun updateItems(newItems: List<CachedDynalistItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterItemListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return FilterItemListViewHolder(inflater.inflate(R.layout.search_list_item,
                parent, false))
    }

    override fun getItemCount(): Int = items.size
    override fun getItemId(position: Int): Long = items[position].item.clientId

    override fun onBindViewHolder(holder: FilterItemListViewHolder, position: Int) {
        holder.itemView.setOnClickListener {
            onClickListener?.invoke(items[position].item)
        }

        val context = holder.itemView.context
        val applySearchTerm: (Spannable) -> Unit = { highlightSearchTerm(it) }
        val item = items[position]
        item.applyThemedSpans(context, includeParent = true)
        val parentText = item.spannableParent
        val text = item.spannableText.also(applySearchTerm)
        val notes = item.spannableNotes.also(applySearchTerm)
        val children = item.spannableChildren

        holder.apply {
            itemParent.text = parentText
            itemText.text = text
            itemNotes.visibility = if (notes.isBlank()) View.GONE else View.VISIBLE
            itemNotes.text = notes
            itemChildren.visibility = if (children.isBlank()) View.GONE else View.VISIBLE
            itemChildren.text = children
        }
    }

    private fun highlightSearchTerm(text: Spannable) {
        val start = text.indexOf(searchTerm, ignoreCase = true)
        if (start >= 0) {
            val span = BackgroundColorSpan(highlightColor)
            text.setSpan(span, start, start + searchTerm.length,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
    }

}