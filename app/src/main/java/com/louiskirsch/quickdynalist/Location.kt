package com.louiskirsch.quickdynalist

import android.content.Context
import android.text.SpannableString
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import com.louiskirsch.quickdynalist.objectbox.DynalistFolder
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.text.ThemedSpan
import com.louiskirsch.quickdynalist.utils.ellipsis
import com.louiskirsch.quickdynalist.utils.resolveColorAttribute
import org.jetbrains.anko.colorAttr

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
open class ItemLocation(val item: DynalistItem): Location {
    override val id: Long get() = item.clientId
    override val name: String get() = item.strippedMarkersName
    override val shortenedName: CharSequence get() = item.shortenedName
    override val nameWithoutSymbol get() = item.nameWithoutSymbol
    override val symbol get() = item.symbol
    override val extraKey: String get() = DynalistApp.EXTRA_DISPLAY_ITEM
    override val extraIdKey: String get() = DynalistApp.EXTRA_DISPLAY_ITEM_ID
    override val supportsInsertion: Boolean get() = true
    override val typeName: String get() = DynalistItem.LOCATION_TYPE

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
    override val typeName: String get() = DynalistItemFilter.LOCATION_TYPE

    override fun toString() = shortenedName.toString()
    override fun hashCode(): Int = filter.hashCode()
    override fun equals(other: Any?): Boolean {
        return filter == (other as? FilterLocation)?.filter
    }
}
class FolderLocation(val folder: DynalistFolder,
                     context: Context, private val depth: Int): Location {
    private val defaultName = context.getString(R.string.folder_unnamed)

    override val id: Long get() = folder.id
    override val name: String get() = folder.title ?: defaultName
    override val shortenedName: CharSequence get() = format(name.ellipsis(30))
    override val nameWithoutSymbol: String get() = name
    override val symbol: String? get() = null
    override val extraKey: String get() = throw NotImplementedError()
    override val extraIdKey: String get() = throw NotImplementedError()
    override val supportsInsertion: Boolean get() = false
    override val typeName: String get() = DynalistFolder.LOCATION_TYPE

    override fun toString() = shortenedName.toString()
    override fun hashCode(): Int = folder.hashCode()
    override fun equals(other: Any?): Boolean {
        return folder == (other as? FolderLocation)?.folder
    }

    private fun format(text: CharSequence): SpannableString {
        return SpannableString("  $text").apply {
            setSpan(LeadingMarginSpan.Standard(30 * depth), 0, length, 0)
            val span = ThemedSpan {
                val iconTint = it.resolveColorAttribute(R.attr.colorControlNormal)
                val drawable = it.getDrawable(R.drawable.ic_folder)!!.apply {
                    mutate()
                    setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                    setTint(iconTint)
                }
                ImageSpan(drawable)
            }
            setSpan(span, 0, 1, 0)
        }
    }
}
class DocumentLocation(item: DynalistItem, private val depth: Int) : ItemLocation(item) {
    override val shortenedName: CharSequence get() {
        return SpannableString(super.shortenedName).apply {
            setSpan(LeadingMarginSpan.Standard(30 * depth), 0, length, 0)
        }
    }
}