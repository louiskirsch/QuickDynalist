package com.louiskirsch.quickdynalist

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import io.objectbox.annotation.*
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Entity
class DynalistItem(var serverFileId: String?, @Index var serverParentId: String?,
                   var serverItemId: String?, var name: String, var note: String,
                   @Transient var childrenIds: List<String>? = null,
                   var isInbox: Boolean = false,
                   var isBookmark: Boolean = false) : Serializable, Parcelable {

    constructor() : this(null, null, null, "", "")

    @Id var clientId: Long = 0
    var position: Int = 0

    @Backlink(to = "parent")
    lateinit var children: ToMany<DynalistItem>
    lateinit var parent: ToOne<DynalistItem>

    override fun toString() = shortenedName
    override fun hashCode(): Int = (clientId % Int.MAX_VALUE).toInt()
    override fun equals(other: Any?): Boolean {
        return if (clientId > 0)
            return clientId == (other as? DynalistItem)?.clientId
        else
            false
    }

    val serverAbsoluteId: Pair<String, String>?
        get() = Pair(serverFileId, serverItemId).selfNotNull

    val shortenedName: String get() {
        val label = strippedMarkersName
        return if (label.length > 30)
            "${label.take(27)}..."
        else
            label
    }

    fun getSpannableText(context: Context) = parseText(name, context)
    fun getSpannableNotes(context: Context) = parseText(note, context)

    fun getBulletedChildren(context: Context): CharSequence {
        return TextUtils.concat(*children.sortedBy { it.position } .mapIndexed { idx, child ->
            child.getSpannableText(context).run {
                setSpan(BulletSpan(15), 0, length, 0)
                if (idx == children.size - 1) this else TextUtils.concat(this, "\n")
            }
        }.toTypedArray())
    }

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

    fun populateChildren(itemMap: Map<Pair<String, String>, DynalistItem>) {
        childrenIds!!.forEachIndexed { idx, childId ->
            itemMap[Pair(serverFileId, childId)]!!.let { child ->
                child.serverParentId = serverItemId
                child.parent.target = this
                child.position = idx
            }
        }
    }

    private val tags: List<String> get() {
        return listOf(name, note).flatMap {
            tagRegex.findAll(it).map { m -> m.groupValues[2] } .toList()
        }
    }

    val markedAsPrimaryInbox: Boolean
        get() = markedAsBookmark && "#primary" in tags

    val markedAsBookmark: Boolean
        get() = tagMarkers.any { it in tags }

    private val strippedMarkersName: String
        get() = tagMarkers.fold(name) { acc, marker ->
            acc.replace(marker, "", true) } .trim()

    companion object {
        fun newInbox() = DynalistItem(null, null, "inbox",
                "\uD83D\uDCE5 Inbox", "", emptyList(), isInbox = true, isBookmark = true)
        private val tagMarkers = listOf("#quickdynalist", "#inbox")
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
