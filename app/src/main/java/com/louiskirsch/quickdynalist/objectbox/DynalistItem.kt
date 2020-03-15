package com.louiskirsch.quickdynalist.objectbox

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Parcel
import android.os.Parcelable
import android.text.*
import android.text.format.DateFormat
import android.text.style.*
import android.util.Log
import androidx.core.content.ContextCompat
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.jobs.EditItemJob
import com.louiskirsch.quickdynalist.text.IndentedBulletSpan
import com.louiskirsch.quickdynalist.text.ThemedSpan
import com.louiskirsch.quickdynalist.text.TintedImageSpan
import com.louiskirsch.quickdynalist.utils.*
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.annotation.*
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import org.scilab.forge.jlatexmath.TeXConstants
import ru.noties.jlatexmath.JLatexMathDrawable
import java.io.Serializable
import java.lang.Exception
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

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
    var hidden: Boolean = false
    var isChecklist: Boolean = false
    var areCheckedItemsVisible: Boolean = false
    var modified: Date = Date()
    var created: Date = Date()
    var color: Int = 0
    var heading: Int = 0

    @Backlink(to = "parent")
    lateinit var children: ToMany<DynalistItem>
    lateinit var parent: ToOne<DynalistItem>
    lateinit var metaData: ToOne<DynalistItemMetaData>

    // MetaData
    var metaDate: Date? = null
    var metaImage: String? = null
    var metaSymbol: String? = null
    lateinit var metaTags: ToMany<DynalistTag>
    @Backlink(to = "metaLinkedItem")
    lateinit var metaBacklinks: ToMany<DynalistItem>
    lateinit var metaLinkedItem: ToOne<DynalistItem>

    fun notifyModified(time: Date = Date()) {
        modified = time
        updateMetaData()
    }

    fun updateMetaData() {
        metaDate = date
        metaImage = image
        metaSymbol = symbol
        metaTags.clear()
        metaTags.addAll(tags.map { DynalistTag.find(it) })
        metaLinkedItem.target = linkedItem
    }

    fun hasParent(parentId: Long, maxDepth: Int = 1): Boolean {
        if (maxDepth < 1)
            return false
        if (parent.targetId == parentId)
            return true
        return parent.target?.hasParent(parentId, maxDepth - 1) ?: false
    }

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

    fun getSpannableText(context: Context, displayParent: DynalistItem? = null)
            = parseText(name, context, displayParent)
    fun getSpannableNotes(context: Context) = parseText(note, context)

    fun getSpannableChildren(context: Context, maxItems: Int, maxDepth: Int = 0,
                             displayParent: DynalistItem? = null): Spannable {
        val sb = SpannableStringBuilder()
        recursiveSpannableChildren(context, sb, maxItems, maxDepth, 0, true, displayParent)
        if (sb.isNotEmpty())
            sb.delete(sb.length - 1, sb.length)
        return sb
    }

    private val visibleChildren
        get() = children.filter { !it.hidden && (areCheckedItemsVisible || !it.isChecked) }

    private val visibleChildrenIncludingLinking: List<DynalistItem>
        get() {
            val forwardLinks = metaLinkedItem.target?.children?.sortedBy { it.position }
                    ?: emptyList()
            val backwardLinks = metaBacklinks.mapNotNull { it.parent.target }.distinct()
                    .sortedBy { it.position }
            return (children + forwardLinks + backwardLinks)
                    .filter { !it.hidden && (areCheckedItemsVisible || !it.isChecked) }
        }

    enum class LinkingChildType { NON_LINKING, FORWARD_LINK, BACKWARD_LINK }
    fun getLinkingChildType(displayParent: DynalistItem?): LinkingChildType {
        return when {
            displayParent == null -> LinkingChildType.NON_LINKING
            parent.targetId == displayParent.metaLinkedItem.targetId ->
                LinkingChildType.FORWARD_LINK
            metaLinkedItem.targetId == displayParent.clientId ->
                LinkingChildType.BACKWARD_LINK
            displayParent.clientId in children.map { it.metaLinkedItem.targetId } ->
                LinkingChildType.BACKWARD_LINK
            else -> LinkingChildType.NON_LINKING
        }
    }

    private fun markLinkingChildType(spannable: SpannableStringBuilder, type: LinkingChildType) {
        val marker = when (type) {
            LinkingChildType.FORWARD_LINK -> R.drawable.ic_forward_link
            LinkingChildType.BACKWARD_LINK -> R.drawable.ic_backward_link
            else -> return
        }
        val imageSpan = ThemedSpan {
            val drawable = it.getDrawable(marker)!!.apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
            ImageSpan(drawable)
        }
        spannable.insert(0, "$linkingChildTypeIcon ")
        spannable.setSpan(imageSpan, 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun recursiveSpannableChildren(context: Context, sb: SpannableStringBuilder,
                                           maxItems: Int, maxDepth: Int, depth: Int,
                                           showLinking: Boolean, displayParent: DynalistItem?) {
        if (maxItems == 0 || depth > maxDepth)
            return
        val showLinkingUpdated = showLinking && (displayParent == null ||
                        displayParent.clientId != metaLinkedItem.targetId)
        val children = if (showLinkingUpdated) visibleChildrenIncludingLinking else visibleChildren
        children.let {
            if (maxItems == -1) it else it.take(maxItems)
        }.forEach { child ->
            child.getSpannableText(context, this).apply {
                if (isNotBlank() && trim().toString() != linkingChildTypeIcon) {
                    if (depth == 0)
                        setSpan(BulletSpan(15), 0, length, 0)
                    else
                        setSpan(IndentedBulletSpan(15, 30 * depth), 0, length, 0)
                    if (child.color > 0) {
                        val span = ThemedSpan {
                            val colors = it.resources.getIntArray(R.array.itemColors)
                            BackgroundColorSpan(colors[child.color])
                        }
                        setSpan(span, 0, length, 0)
                    }
                    if (child.isChecked) {
                        setSpan(StrikethroughSpan(), 0, length, 0)
                    }
                    sb.append(this)
                    sb.append("\n")
                    child.recursiveSpannableChildren(context, sb, maxItems, maxDepth, depth + 1,
                                                     showLinkingUpdated, displayParent)
                }
            }
        }
    }

    fun getPlainChildren(context: Context, maxDepth: Int = 0): CharSequence {
        val sb = StringBuilder()
        recursivePlainChildren(context, sb, maxDepth, 0)
        if (sb.isNotEmpty())
            sb.delete(sb.length - 1, sb.length)
        return sb.toString()
    }

    private fun recursivePlainChildren(context: Context, sb: StringBuilder,
                                       maxDepth: Int = 0, depth: Int = 0) {
        if (depth > maxDepth)
            return
        visibleChildren.sortedBy { it.position }.forEach { child ->
            repeat(depth) { sb.append("    ") }
            if (child.isChecked)
                sb.append("\u2713 ")
            else
                sb.append("- ")
            sb.append(child.getSpannableText(context))
            sb.append("\n")
            child.recursivePlainChildren(context, sb, maxDepth, depth + 1)
        }
    }

    private fun parseText(text: String, context: Context, displayParent: DynalistItem? = null)
            : SpannableStringBuilder {
        val spannable = SpannableStringBuilder(text).linkify()
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)

        spannable.replaceAll(imageRegex) { "" }
        spannable.replaceAll(dateTimeRegex) {
            val replaceText = try {
                val date = dateReader.get()!!.parse(it.groupValues[1])
                "\uD83D\uDCC5 ${dateFormat.format(date)}" +
                    (it.groupValues[2].ifEmpty { null }?.let { text ->
                        " ${timeFormat.format(timeReader.get()!!.parse(text))}"
                    } ?: "") +
                    (it.groupValues[3].ifEmpty { null }?.let { text ->
                        " - ${dateFormat.format(dateReader.get()!!.parse(text))}"
                    } ?: "") +
                    (it.groupValues[4].ifEmpty { null }?.let { text ->
                        " ${timeFormat.format(timeReader.get()!!.parse(text))}"
                    } ?: "") +
                    (it.groupValues[5].ifEmpty { null }?.let { text ->
                        val quantityType = dateRepStrings.getValue(it.groupValues[6])
                        val quantity = text.toInt()
                        ", ${context.resources.getQuantityString(quantityType, quantity, quantity)}"
                    } ?: "")
            } catch (e: Exception) {
                "\uD83D\uDCC5 ${context.getString(R.string.invalid_date)}"
            }
            SpannableString(replaceText).apply {
                val bg = ThemedSpan(highlightSpanCreator)
                setSpan(bg, 3, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        spannable.replaceAll(dynalistLinkRegex) {
            val fileId = it.groupValues[2]
            val itemId = it.groupValues[3].ifEmpty { "root" }
            val item = box.query {
                equal(DynalistItem_.serverFileId, fileId)
                and()
                equal(DynalistItem_.serverItemId, itemId)
            }.findFirst()
            if (item != null && displayParent == item) return@replaceAll ""
            SpannableString("∞ ${it.groupValues[1]}").apply {
                item?.apply {
                    val span = DynalistLinkSpan(this)
                    setSpan(span, 2, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
                val bg = ThemedSpan(highlightSpanCreator)
                setSpan(bg, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        spannable.replaceAll(linkRegex) {
            SpannableString(it.groupValues[1]).apply {
                val span = URLSpan(it.groupValues[2])
                setSpan(span, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

        markdownToSpans(spannable)
        spannable.replaceAll(whitespaceRegex) { "" }

        latexRegex.findAll(spannable).forEach {
            val latex = it.groupValues[1]
            val range = it.range
            val drawable = JLatexMathDrawable.builder(latex).apply {
                textSize(50f)
                fitCanvas(true)
                padding(0)
                background(Color.TRANSPARENT)
                style(TeXConstants.STYLE_TEXT)
                align(JLatexMathDrawable.ALIGN_LEFT)
            }.build()

            val span = TintedImageSpan(drawable, ImageSpan.ALIGN_BOTTOM, lineHeight = 1.3f)
            spannable.setSpan(span, range.start, range.endInclusive + 1,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        tagRegex.findAll(spannable).forEach {
            val tag = DynalistTag.find(it.groupValues[1])
            val range = it.groups[1]!!.range

            val bg = ThemedSpan(highlightSpanCreator)
            spannable.setSpan(bg, range.start, range.endInclusive + 1,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            val tagSpan = DynalistTagSpan(tag)
            spannable.setSpan(tagSpan, range.start + 1, range.endInclusive + 1,
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        markLinkingChildType(spannable, getLinkingChildType(displayParent))
        return spannable
    }

    fun populateChildren(itemMap: Map<Pair<String, String>, DynalistItem>) {
        childrenIds!!.forEachIndexed { idx, childId ->
            itemMap[Pair(serverFileId, childId)]?.let { child ->
                if (child.syncJob == null) {
                    child.serverParentId = serverItemId
                    child.parent.target = this
                    child.position = idx
                }
            }
        }
    }

    val image: String? get() {
        return imageRegex.find(name)?.groupValues?.get(2)
                ?: imageRegex.find(note)?.groupValues?.get(2)
    }

    var tags: List<String>
        get() {
            return listOf(name, note).flatMap {
                tagRegex.findAll(it).map { m -> m.groupValues[1].toLowerCase() } .toList()
            }
        }
        set(value) {
            val currentTags = tags
            removeTags(currentTags - value)
            addTags(value - currentTags)
        }

    private fun addTags(tags: List<String>) {
        val joinedTags = tags.joinToString(" ")
        note = when {
            note.isBlank() -> joinedTags
            note.startsWith('#') || note.startsWith('@') -> "$joinedTags $note"
            else -> "$joinedTags\n$note"
        }
    }

    private fun removeTags(tags: List<String>) {
        val regexes = tags.map { Regex("""\s*$it""") }
        val remove = { str: String ->
            StringBuilder(str).apply {
                regexes.forEach { regex ->
                    regex.find(this)?.let { replace(it.range.first, it.range.last + 1, "") }
                }
            }.toString()
        }
        name = remove(name)
        note = remove(note)
    }

    var markedAsBookmark: Boolean
        get() = tagMarkers.any { it in tags }
        set(value) {
            if (value && !markedAsBookmark) {
                note = "${tagMarkers[0]} $note"
            }
            if (!value && markedAsBookmark) {
                name = strippedMarkersName
                note = strippedMarkersNote
            }
            isBookmark = value
        }

    val strippedMarkersName: String get() = removeMarkers(name).ifEmpty { name }
    private val strippedMarkersNote: String get() = removeMarkers(note)

    private fun removeMarkers(text: String) = tagMarkers.fold(text) { acc, marker ->
        acc.replace(marker, "", true)
    }.trim()

    var date: Date?
        get() = dateTimeRegex.find(name)?.groupValues?.get(1)?.let { date ->
            try { dateReader.get()!!.parse(date) } catch(e: ParseException) {
                Log.w("DynalistItem", "Could not parse date", e)
                null
            }
        }
        set(value) {
            val stripped = name.replace(dateTimeRegex, "").trim()
            name = if (value != null) {
                val date = "!(${dateReader.get()!!.format(value)})"
                "$stripped $date"
            } else {
                stripped
            }
        }

    val time: Date?
        get() = dateTimeRegex.find(name)?.groupValues?.get(2)?.let { time ->
            return if (time.isNotBlank())
                timeReader.get()!!.parse(time)
            else
                null
        }

    var linkedItem: DynalistItem?
        get() = dynalistLinkRegex.find(name + note)?.groupValues?.let { values ->
            val fileId = values[2]
            val itemId = values[3].ifEmpty { "root" }
            return byServerId(fileId, itemId)
        }
        set(value) {
            val newLinkText = value?.linkText ?: ""
            when {
                name.contains(dynalistLinkRegex) -> {
                    name = name.replaceFirst(dynalistLinkRegex, newLinkText)
                }
                note.contains(dynalistLinkRegex) -> {
                    note = note.replaceFirst(dynalistLinkRegex, newLinkText)
                }
                name.isBlank() -> {
                    name = newLinkText
                }
                note.isBlank() -> {
                    note = newLinkText
                }
                else -> {
                    note = "$newLinkText\n$note"
                }
            }
        }

    val linkText: String? get() {
        if (serverFileId == null || serverItemId == null) return null
        return "[${name}](https://dynalist.io/d/${serverFileId}#z=${serverItemId})"
    }

    val symbol: String?
        get() = EmojiFactory.emojis.firstOrNull { it in name }

    val nameWithoutSymbol: String
        get() = symbol?.let { strippedMarkersName.replace(it, "").trim() }
                ?: strippedMarkersName

    val nameWithoutDate: String
        get() = name.
                replace(dateTimeRegex, "").trim()

    companion object {
        const val LOCATION_TYPE = "item"
        val box get() = DynalistApp.instance.boxStore.boxFor<DynalistItem>()

        private val tagMarkers = listOf("#inbox", "#quickdynalist")
        private val dateReader = object : ThreadLocal<SimpleDateFormat>() {
            @SuppressLint("SimpleDateFormat")
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd")
        }
        private val timeReader = object : ThreadLocal<SimpleDateFormat>() {
            @SuppressLint("SimpleDateFormat")
            override fun initialValue() = SimpleDateFormat("HH:mm")
        }
        private val dateRepStrings = mapOf(
                "d" to R.plurals.date_repetition_d,
                "w" to R.plurals.date_repetition_w,
                "m" to R.plurals.date_repetition_m,
                "y" to R.plurals.date_repetition_y
        )
        private val dateTimeRegex = Regex("""!\(([0-9\-]+)[ ]?([0-9:]+)?(?: - ([0-9\-]+)[ ]?([0-9:]+)?)?[ |]*(?:([0-9]+)([dwmy]))?\)""")
        private val tagRegex = Regex("""(?:^|\s)([#@][^\s]*[^\s\d]+)""")
        private val boldRegex = Regex("""\*\*(.*?)\*\*""")
        private val italicRegex = Regex("""__(.*?)__""")
        private val inlineCodeRegex = Regex("""`(.*?)`""")
        private val lineThroughRegex = Regex("""~~(.*?)~~""")
        private val latexRegex = Regex("""\$\$(.*?)\$\$""")
        private val linkRegex = Regex("""\[(.*?)]\((.*?)\)""")
        private val imageRegex = Regex("""!\[(.*?)]\((.*?)\)""")
        private val dynalistLinkRegex = Regex("""\[(.*?)]\(https://dynalist\.io/d/([^#]+?)(?:#z=([^&]+?))?\)""")
        private val whitespaceRegex = Regex("""(^\s+)|(\s+$)""")

        private val linkingChildTypeIcon = "\uD83D\uDD17"

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
                        hidden = readInt() > 0
                        isChecklist = readInt() > 0
                        areCheckedItemsVisible = readInt() > 0
                        modified = Date(readLong())
                        created = Date(readLong())
                        color = readInt()
                        heading = readInt()
                        parent.targetId = readLong()
                    }
                }
            }
            override fun newArray(size: Int) = arrayOfNulls<DynalistItem>(size)
        }

        fun updateLocally(item: DynalistItem, updater: (DynalistItem) -> Unit) {
            var updatedItem: DynalistItem? = null
            DynalistApp.instance.boxStore.runInTx {
                box.get(item.clientId)?.let {
                    updater(it)
                    box.put(it)
                    updatedItem = it
                }
            }
            updatedItem?.let {
                ListAppWidget.notifyItemChanged(DynalistApp.instance, it)
            }
        }

        fun updateGlobally(item: DynalistItem, updater: (DynalistItem) -> Unit) {
            box.get(item.clientId)?.apply {
                updater(this)
                DynalistApp.instance.jobManager.addJobInBackground(EditItemJob(this))
            }
        }

        fun byServerId(fileId: String, itemId: String): DynalistItem? {
            return box.query {
                equal(DynalistItem_.serverFileId, fileId)
                equal(DynalistItem_.serverItemId, itemId)
            }.findFirst()
        }

        val markdownSpanTypes = listOf(
                StyleSpan::class.java, StrikethroughSpan::class.java,
                ForegroundColorSpan::class.java, BackgroundColorSpan::class.java
        )
        private val highlightSpanCreator = { context: Context ->
            BackgroundColorSpan(ContextCompat.getColor(context, R.color.spanHighlight))
        }
        private val codeSpanCreator = { context: Context ->
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.codeColor))
        }

        fun spannedToMarkdown(spanned: Spanned): String {
            val stringBuilder = StringBuilder()
            val appendMarkdown = { it: Any ->
                stringBuilder.append(when {
                    it is StyleSpan && it.style == Typeface.BOLD -> "**"
                    it is StyleSpan && it.style == Typeface.ITALIC -> "__"
                    it is StrikethroughSpan -> "~~"
                    it is ForegroundColorSpan -> "`"
                    else -> ""
                })
            }
            var before = 0
            var offset = spanned.nextSpanTransition(-1, spanned.length + 1, null)
            while (offset <= spanned.length) {
                stringBuilder.append(spanned, before, offset)
                val spans = spanned.getSpans(offset, offset, Object::class.java).filter {
                    it.javaClass in markdownSpanTypes &&
                            (offset == spanned.getSpanStart(it) || offset == spanned.getSpanEnd(it))
                }.groupBy { spanned.getSpanEnd(it) == offset }.let {
                    it[true]?.reversed().orEmpty() + it[false].orEmpty()
                }
                spans.forEach { appendMarkdown(it) }
                before = offset
                offset = spanned.nextSpanTransition(offset, spanned.length + 1, null)
            }
            stringBuilder.append(spanned, before, spanned.length)
            return stringBuilder.toString()
        }

        fun markdownToSpans(spannable: SpannableStringBuilder,
                            context: Context? = null): SpannableStringBuilder {
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
                    val highlight = context?.let { ctx -> highlightSpanCreator(ctx) }
                            ?: ThemedSpan(highlightSpanCreator)
                    setSpan(highlight, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                    val code = context?.let { ctx -> codeSpanCreator(ctx) }
                            ?: ThemedSpan(codeSpanCreator)
                    setSpan(code, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            }
            return spannable
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
            writeInt(hidden.int)
            writeInt(isChecklist.int)
            writeInt(areCheckedItemsVisible.int)
            writeLong(modified.time)
            writeLong(created.time)
            writeInt(color)
            writeInt(heading)
            writeLong(parent.targetId)
        }
    }

    override fun describeContents(): Int = 0
}
