package com.louiskirsch.quickdynalist.objectbox

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcel
import android.os.Parcelable
import android.text.*
import android.text.format.DateFormat
import android.text.style.*
import android.view.textclassifier.TextLinks
import com.louiskirsch.quickdynalist.*
import io.objectbox.annotation.*
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Entity
class DynalistItem(@Index var serverFileId: String?, @Index var serverParentId: String?,
                   @Index var serverItemId: String?, var name: String, var note: String,
                   @Transient var childrenIds: List<String>? = null,
                   var isInbox: Boolean = false, var isBookmark: Boolean = false,
                   var isChecked: Boolean = false) : Serializable, Parcelable {

    constructor() : this(null, null, null, "", "")

    @Id var clientId: Long = 0
    var position: Int = 0
    @Index var syncJob: String? = null

    @Backlink(to = "parent")
    lateinit var children: ToMany<DynalistItem>
    lateinit var parent: ToOne<DynalistItem>

    override fun toString() = shortenedName.toString()
    override fun hashCode(): Int = (clientId % Int.MAX_VALUE).toInt()
    override fun equals(other: Any?): Boolean {
        return if (clientId > 0)
            return clientId == (other as? DynalistItem)?.clientId
        else
            false
    }

    val serverAbsoluteId: Pair<String, String>?
        get() = Pair(serverFileId, serverItemId).selfNotNull

    val shortenedName get() = strippedMarkersName.ellipsis(30)

    fun getSpannableText(context: Context) = parseText(name, context)
    fun getSpannableNotes(context: Context) = parseText(note, context)

    fun getSpannableChildren(context: Context, maxItems: Int): Spannable {
        val sb = SpannableStringBuilder()
        val children = children.filter { !it.isChecked } .take(maxItems)
        children.mapIndexed { idx, child ->
            child.getSpannableText(context).run {
                if (isNotBlank()) {
                    setSpan(BulletSpan(15), 0, length, 0)
                    sb.append(this)
                    if (idx < children.size - 1) sb.append("\n")
                }
            }
        }
        return sb
    }

    fun getPlainChildren(context: Context, maxDepth: Int = 0): CharSequence {
        val sb = SpannableStringBuilder()
        recursivePlainChildren(context, sb, maxDepth, 0)
        return sb.dropLast(1)
    }

    private fun recursivePlainChildren(context: Context, sb: SpannableStringBuilder,
                                       maxDepth: Int = 0, depth: Int = 0) {
        if (depth > maxDepth)
            return
        children.sortedBy { it.position }.forEachIndexed { idx, child ->
            repeat(depth) { sb.append("    ") }
            sb.append("- ")
            sb.append(child.getSpannableText(context))
            sb.append("\n")
            child.recursivePlainChildren(context, sb, maxDepth, depth + 1)
        }
    }

    private fun parseText(text: String, context: Context): Spannable {
        val spannable = SpannableStringBuilder(text).linkify()
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val spanHighlight = context.getColor(R.color.spanHighlight)
        val codeColor = context.getColor(R.color.codeColor)

        spannable.replaceAll(imageRegex) { "" }
        spannable.replaceAll(dateTimeRegex) {
            val date = dateReader.parse(it.groupValues[1])
            val replaceText = if (it.groupValues[2].isEmpty()) {
                "\uD83D\uDCC5 ${dateFormat.format(date)}"
            } else {
                val time = timeReader.parse(it.groupValues[2])
                "\uD83D\uDCC5 ${dateFormat.format(date)} ${timeFormat.format(time)}"
            }
            SpannableString(replaceText).apply {
                val bg = BackgroundColorSpan(spanHighlight)
                setSpan(bg, 3, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        spannable.replaceAll(dynalistLinkRegex) {
            SpannableString("∞ ${it.groupValues[1]}").apply {
                val fileId = it.groupValues[2]
                val itemId = it.groupValues[3]
                val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
                val item = box.query {
                    equal(DynalistItem_.serverFileId, fileId)
                    and()
                    equal(DynalistItem_.serverItemId, itemId)
                }.findFirst()
                item?.apply {
                    val span = DynalistLinkSpan(this)
                    setSpan(span, 2, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
                val bg = BackgroundColorSpan(spanHighlight)
                setSpan(bg, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        spannable.replaceAll(linkRegex) {
            SpannableString(it.groupValues[1]).apply {
                val span = URLSpan(it.groupValues[2])
                setSpan(span, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        val replace = { regex: Regex, createSpan: () -> Any ->
            spannable.replaceAll(regex) {
                SpannableString(it.groupValues[1]).apply {
                    setSpan(createSpan(), 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            }
        }
        replace(boldRegex) { StyleSpan(Typeface.BOLD) }
        replace(italicRegex) { StyleSpan(Typeface.ITALIC) }
        replace(lineThroughRegex) { StrikethroughSpan() }

        spannable.replaceAll(inlineCodeRegex) {
            SpannableString(it.groupValues[1]).apply {
                setSpan(BackgroundColorSpan(spanHighlight), 0, length,
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(codeColor), 0, length,
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        tagRegex.findAll(spannable).forEach {
            val bg = BackgroundColorSpan(spanHighlight)
            val range = it.groups[2]!!.range
            spannable.setSpan(bg, range.start, range.endInclusive + 1,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
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

    val image: String? get() {
        return imageRegex.find(name)?.groupValues?.get(2)
                ?: imageRegex.find(note)?.groupValues?.get(2)
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

    val strippedMarkersName: String
        get() = tagMarkers.fold(name) { acc, marker ->
            acc.replace(marker, "", true) } .trim()

    companion object {
        fun newInbox() = DynalistItem(null, null, "inbox",
                "\uD83D\uDCE5 Inbox", "", emptyList(), isInbox = true, isBookmark = true)
        private val tagMarkers = listOf("#quickdynalist", "#inbox")
        @SuppressLint("SimpleDateFormat")
        private val dateReader = SimpleDateFormat("yyyy-MM-dd")
        @SuppressLint("SimpleDateFormat")
        private val timeReader = SimpleDateFormat("HH:mm")
        private val dateTimeRegex = Regex("""!\(([0-9\-]+)[ ]?([0-9:]+)?\)""")
        private val tagRegex = Regex("""(^| )([#@][\d\w_-]+)""")
        private val boldRegex = Regex("""\*\*(.*?)\*\*""")
        private val italicRegex = Regex("""__(.*?)__""")
        private val inlineCodeRegex = Regex("""`(.*?)`""")
        private val lineThroughRegex = Regex("""~~(.*?)~~""")
        private val linkRegex = Regex("""\[(.*?)]\((.*?)\)""")
        private val imageRegex = Regex("""!\[(.*?)]\((.*?)\)""")
        private val dynalistLinkRegex = Regex("""\[(.*?)]\(https://dynalist\.io/d/(.*?)#z=(.*?)\)""")

        @Suppress("unused")
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
                            isBookmark = readInt() > 0,
                            isChecked = readInt() > 0
                    ).apply {
                        clientId = readLong()
                        position = readInt()
                        syncJob = readString()
                        parent.targetId = readLong()
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
            writeInt(isChecked.int)
            writeLong(clientId)
            writeInt(position)
            writeString(syncJob)
            writeLong(parent.targetId)
        }
    }

    override fun describeContents(): Int = 0
}
