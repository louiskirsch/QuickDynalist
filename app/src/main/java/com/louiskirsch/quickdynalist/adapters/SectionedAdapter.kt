package com.louiskirsch.quickdynalist.adapters

import android.content.Context
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.objectbox.DynalistItem

class SectionedAdapter<T>: ArrayAdapter<SectionedAdapter.Item<T>> {
    interface Item<T>
    class Separator<T>(val separatorText: CharSequence): Item<T>
    class DisplayItem<T>(val display: T): Item<T> {
        override fun toString(): String {
            return display.toString()
        }
    }

    private val layoutInflater by lazy { LayoutInflater.from(context) }

    constructor(context: Context, resource: Int) : super(context, resource)
    constructor(context: Context, resource: Int, textViewResourceId: Int)
            : super(context, resource, textViewResourceId)
    constructor(context: Context, resource: Int, objects: Array<Item<T>>)
            : super(context, resource, objects)
    constructor(context: Context, resource: Int, textViewResourceId: Int, objects: Array<Item<T>>)
            : super(context, resource, textViewResourceId, objects)
    constructor(context: Context, resource: Int, objects: MutableList<Item<T>>)
            : super(context, resource, objects)
    constructor(context: Context, resource: Int, textViewResourceId: Int, objects: MutableList<Item<T>>)
            : super(context, resource, textViewResourceId, objects)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        return if (item is Separator) {
            createSeparator(convertView, parent, item)
        } else {
            super.getView(position, convertView, parent)
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        return if (item is Separator) {
            createSeparator(convertView, parent, item)
        } else {
            super.getDropDownView(position, convertView, parent)
        }
    }

    private fun createSeparator(convertView: View?, parent: ViewGroup, item: Separator<T>): View {
        val v = convertView ?: layoutInflater.inflate(R.layout.list_separator, parent, false)
        (v as TextView).text = item.separatorText
        return v
    }

    override fun isEnabled(position: Int): Boolean {
        return getItem(position)!! is DisplayItem<T>
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            getItem(position)!! is DisplayItem -> 0
            else -> 1
        }
    }

    fun addSection(header: CharSequence, items: List<T>) {
        add(Separator(header))
        addAll(items.map { DisplayItem(it) })
    }

    fun getItemPayload(position: Int): T? {
        return (getItem(position) as? DisplayItem)?.display
    }
}