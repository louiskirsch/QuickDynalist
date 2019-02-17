package com.louiskirsch.quickdynalist.adapters

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class FilterAdapter<T>(context: Context, resource: Int, objects: MutableList<T>)
    : ArrayAdapter<T>(context, resource, objects) {

    private val filterableItems = ArrayList<T>(objects)
    private val excludedItems = HashSet<T>()

    fun excludeItem(item: T) {
        excludedItems.add(item)
    }

    fun includeItem(item: T) {
        excludedItems.remove(item)
    }

    fun updateFilterableItems(items: List<T>) {
        filterableItems.clear()
        filterableItems.addAll(items)
        clear()
        addAll(items)
        notifyDataSetChanged()
    }

    private val filter = object: Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            if (constraint != null && constraint.isNotEmpty()) {
                val items: List<T> = filterableItems.filter {
                    it.toString().contains(constraint, true) &&
                            it !in excludedItems
                }
                results.values = items
                results.count = items.size
            } else {
                results.values = filterableItems
                results.count = filterableItems.size
            }
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            val values = results.values as List<T>
            clear()
            addAll(values)

            if (results.count > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

    }

    override fun getFilter(): Filter = filter
}