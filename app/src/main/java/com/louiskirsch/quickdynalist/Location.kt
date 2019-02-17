package com.louiskirsch.quickdynalist

import android.content.Context
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.utils.ellipsis

interface Location {
    val id: Long
    val name: String
    val shortenedName: CharSequence
    val nameWithoutSymbol: String
    val symbol: String?
    val extraKey: String
    val extraIdKey: String
    val supportsInsertion: Boolean
    val typeName: String
}
class ItemLocation(val item: DynalistItem): Location {
    override val id: Long get() = item.clientId
    override val name: String get() = item.strippedMarkersName
    override val shortenedName: CharSequence get() = item.shortenedName
    override val nameWithoutSymbol get() = item.nameWithoutSymbol
    override val symbol get() = item.symbol
    override val extraKey: String get() = DynalistApp.EXTRA_DISPLAY_ITEM
    override val extraIdKey: String get() = DynalistApp.EXTRA_DISPLAY_ITEM_ID
    override val supportsInsertion: Boolean get() = true
    override val typeName: String get() = "item"

    override fun toString() = item.toString()
    override fun hashCode(): Int = item.hashCode()
    override fun equals(other: Any?): Boolean {
        return item == (other as? ItemLocation)?.item
    }
}
class FilterLocation(val filter: DynalistItemFilter,
                     context: Context): Location {
    private val defaultName = context.getString(R.string.filter_name_generic)
    override val id: Long get() = filter.id
    override val name: String get() = filter.name ?: defaultName
    override val shortenedName: CharSequence
        get() = filter.name?.ellipsis(30) ?: defaultName
    override val nameWithoutSymbol: String get() = filter.nameWithoutSymbol ?: defaultName
    override val symbol get() = filter.symbol
    override val extraKey: String get() = DynalistApp.EXTRA_DISPLAY_FILTER
    override val extraIdKey: String get() = DynalistApp.EXTRA_DISPLAY_FILTER_ID
    override val supportsInsertion: Boolean get() = false
    override val typeName: String get() = "filter"

    override fun toString() = shortenedName.toString()
    override fun hashCode(): Int = filter.hashCode()
    override fun equals(other: Any?): Boolean {
        return filter == (other as? FilterLocation)?.filter
    }
}
