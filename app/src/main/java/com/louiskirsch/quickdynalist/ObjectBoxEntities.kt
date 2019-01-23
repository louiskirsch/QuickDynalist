package com.louiskirsch.quickdynalist

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.BackgroundColorSpan
import io.objectbox.annotation.*
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Entity
class DynalistItem(val serverFileId: String?, @Index var serverParentId: String?,
                   val serverItemId: String?, val name: String, val note: String,
                   @Transient val childrenIds: List<String>? = null,
                   var isInbox: Boolean = false,
                   var isBookmark: Boolean = false) : Serializable, Parcelable {

    constructor() : this(null, null, null, "", "")

    @Id var clientId: Long = 0
    var position: Int = 0

    val serverAbsoluteId: Pair<String, String>?
        get() = Pair(serverFileId, serverItemId).selfNotNull

    @Backlink(to = "parent")
    lateinit var children: ToMany<DynalistItem>
    lateinit var parent: ToOne<DynalistItem>

    override fun toString() = shortenedName

    val shortenedName: String get() {
        val label = strippedMarkersName
        return if (label.length > 30)
            "${label.take(27)}..."
        else
            label
    }

    fun getSpannableText(context: Context) = parseText(name, context)
    fun getSpannableNotes(context: Context) = parseText(note, context)

    private fun parseText(text: String, context: Context): SpannableString {
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val highlightPositions = ArrayList<IntRange>()
        var offset = 0
        val newText = dateTimeRegex.replace(text) {
            val start = it.range.start + offset
            val date = dateReader.parse(it.groupValues[1])
            val replaceText = if (it.groupValues[2].isEmpty()) {
                "\uD83D\uDCC5 ${dateFormat.format(date)}"
            } else {
                val time = timeReader.parse(it.groupValues[2])
                "\uD83D\uDCC5 ${dateFormat.format(date)} ${timeFormat.format(time)}"
            }
            val end = start + replaceText.length - 1
            // offsetting the dynalistItem
            highlightPositions.add(IntRange(start + 3, end))
            offset += replaceText.length - it.range.size
            replaceText
        }
        highlightPositions.addAll(tagRegex.findAll(newText).map { it.groups[2]!!.range })
        val spannable = SpannableString(newText)
        highlightPositions.forEach {
            val bg = BackgroundColorSpan(context.getColor(R.color.spanHighlight))
            spannable.setSpan(bg, it.start, it.endInclusive + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    fun populateChildren(itemMap: Map<Pair<String, String>, DynalistItem>, maxDepth: Int = 1) {
        children.clear()
        children.addAll(childrenIds!!.mapIndexed { idx, childId ->
            itemMap[Pair(serverFileId, childId)]!!.apply { position = idx }
        })
        if (maxDepth > 1)
            children.forEach { it.populateChildren(itemMap, maxDepth - 1) }
    }

    fun fixParents(itemMap: Map<Pair<String, String>, DynalistItem>) {
        // TODO Currently the API does not send the serverParentId according to the spec
        childrenIds!!.forEach { itemMap[Pair(serverFileId, it)]!!.serverParentId = serverItemId }
    }

    val childrenCount: Int
        get() = childrenIds?.size ?: children.size

    val mightBeInbox: Boolean
        get() = name.toLowerCase() == "inbox"

    private val tags: List<String> get() {
        return listOf(name, note).flatMap {
            tagRegex.findAll(it).map { m -> m.groupValues[2] } .toList()
        }
    }

    val markedAsPrimaryInbox: Boolean
        get() = markedAsBookmark && "#primary" in tags

    val markedAsBookmark: Boolean
        get() = emojiMarkers.any { name.contains(it, true)
                                || note.contains(it, true) } ||
                tagMarkers.any   { name.contains(" $it", true)
                                || name.startsWith(it, true)
                                || note.contains(" $it", true)
                                || note.startsWith(it, true) }

    private val strippedMarkersName: String
        get() = markers.fold(name) { acc, marker ->
            acc.replace(marker, "", true) }
                .replace("# ", "")
                .trim()

    companion object {
        fun newInbox() = DynalistItem(null, null, "inbox",
                "\uD83D\uDCE5 Inbox", "", emptyList(), isInbox = true, isBookmark = true)
        private val tagMarkers = listOf("#quickdynalist", "#inbox")
        private val emojiMarkers = listOf("📒", "📓", "📔", "📕", "📖", "📗", "📘", "📙")
        private val markers = tagMarkers + emojiMarkers
        private val random = Random()
        private val dateReader = SimpleDateFormat("yyyy-MM-dd")
        private val timeReader = SimpleDateFormat("HH:mm")
        private val dateTimeRegex = Regex("""!\(([0-9\-]+)[ ]?([0-9:]+)?\)""")
        private val tagRegex = Regex("""(^| )(#[\d\w_-]+)""")

        @JvmField
        val CREATOR = object : Parcelable.Creator<DynalistItem> {
            override fun createFromParcel(parcel: Parcel): DynalistItem {
                with (parcel) {
                    return DynalistItem(
                            serverFileId = readString(),
                            serverParentId = readString(),
                            serverItemId = readString(),
                            name = readString()!!,
                            note = readString()!!,
                            childrenIds = ArrayList<String>().also { readStringList(it) },
                            isInbox = readInt() > 0,
                            isBookmark = readInt() > 0
                    ).apply {
                        clientId = readLong()
                        position = readInt()
                    }
                }
            }
            override fun newArray(size: Int) = arrayOfNulls<DynalistItem>(size)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        with (parcel) {
            writeString(serverFileId)
            writeString(serverParentId)
            writeString(serverItemId)
            writeString(name)
            writeString(note)
            writeStringList(childrenIds)
            writeInt(isInbox.int)
            writeInt(isBookmark.int)
            writeLong(clientId)
            writeInt(position)
        }
    }

    override fun describeContents(): Int = 0
}
