package com.louiskirsch.quickdynalist.objectbox

import android.content.Context
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.view.View
import android.widget.EditText
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.text.EnhancedMovementMethod
import io.objectbox.Box
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.converter.PropertyConverter
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import java.io.Serializable

@Entity
class DynalistTag(): Serializable {
    enum class Type { UNKNOWN, HASH_TAG, AT_TAG }
    class TypeConverter: PropertyConverter<Type, Int> {
        override fun convertToDatabaseValue(entityProperty: Type?): Int
                = entityProperty?.ordinal ?: 0
        override fun convertToEntityProperty(databaseValue: Int?): Type
                = databaseValue?.let { Type.values()[it] } ?: Type.UNKNOWN
    }

    @Id var id: Long = 0
    @Index
    @Convert(converter = TypeConverter::class, dbType = Integer::class)
    var type: Type = Type.HASH_TAG
    @Index var name: String = ""

    val fullName: String get() {
        val prefix = if (type == Type.AT_TAG) '@' else '#'
        return "$prefix$name"
    }

    override fun toString(): String = fullName
    override fun hashCode(): Int = (id % Int.MAX_VALUE).toInt()
    override fun equals(other: Any?): Boolean {
        return if (id > 0)
            return id == (other as? DynalistTag)?.id
        else
            false
    }

    private constructor(fromString: String) : this() {
        type = when(fromString[0]) {
            '#' -> Type.HASH_TAG
            '@' -> Type.AT_TAG
            else -> throw InvalidTagException()
        }
        name = fromString.substring(1).toLowerCase()
    }

    companion object {
        val box: Box<DynalistTag> get() = DynalistApp.instance.boxStore.boxFor()
        private val cache by lazy { box.all.associateBy { it.fullName }.toMutableMap() }

        fun find(fromString: String): DynalistTag
                = cache[fromString.toLowerCase()] ?: find(DynalistTag(fromString))

        fun clearCache() = cache.clear()

        private fun find(tag: DynalistTag): DynalistTag {
            var foundTag: DynalistTag? = null
            DynalistApp.instance.boxStore.runInTx {
                foundTag = box.query {
                    equal(DynalistTag_.type, tag.type.ordinal.toLong())
                    and()
                    equal(DynalistTag_.name, tag.name)
                }.findFirst() ?: tag.also { box.put(it) }
            }
            cache[foundTag!!.fullName] = foundTag
            return foundTag!!
        }

        private val wordRegex = Regex("""[^\s]+""")
        fun highlightTags(context: Context, s: Editable) {
            val words = wordRegex.findAll(s)
            val tags = words.map { it.value.toLowerCase() in cache }
            val firstNonTag = tags.indexOfFirst { !it }
            val lastNonTag = tags.indexOfLast { !it }
            words.forEachIndexed { i, word ->
                if (word.value[0] == '#' || word.value[0] == '@') {
                    val deleteText = i < firstNonTag || i > lastNonTag
                    val start = word.range.start
                    val length = word.value.length
                    val existingSpans =
                            s.getSpans(start, start + length, ImageSpan::class.java)
                    if (existingSpans.isEmpty())
                        highlightTag(context, s, start, length, !deleteText)
                }
            }
        }

        private fun highlightTag(context: Context, s: Editable, start: Int, len: Int,
                                 keepTextOnDelete: Boolean) {
            val chip = ChipDrawable.createFromResource(context, R.xml.chip)
            chip.setText(s.substring(start, start + len))
            chip.setBounds(0, 0, chip.intrinsicWidth, chip.intrinsicHeight)
            val chipSpan = ImageSpan(chip)
            s.setSpan(chipSpan, start, start + len, 0)
            val clickSpan = object: ClickableSpan() {
                override fun onClick(widget: View) {
                    val newStart = s.getSpanStart(this)
                    s.removeSpan(chipSpan)
                    s.removeSpan(this)
                    if (keepTextOnDelete)
                        s.delete(newStart, newStart + 1)
                    else
                        s.delete(newStart, newStart + len)
                }
            }
            s.setSpan(clickSpan, start, start + len, 0)
        }

        private fun looksLikeTag(s: CharSequence): Boolean {
            return s[0] == '@' || s[0] == '#'
        }

        fun setupTagDetection(editText: EditText, autoDetectTagNames: Boolean) {
            class TagCandidate(val tag: DynalistTag)
            editText.movementMethod = EnhancedMovementMethod.instance
            editText.addTextChangedListener(object: TextWatcher {
                var processingCandidates = false

                override fun afterTextChanged(s: Editable) {
                    if (processingCandidates) return
                    processingCandidates = true
                    s.getSpans(0, s.length, TagCandidate::class.java).forEach {
                        val start = s.getSpanStart(it)
                        val end = s.getSpanEnd(it)
                        val keepTextOnDelete = s[start] != '@' && s[start] != '#'
                        val fullName = it.tag.fullName
                        s.removeSpan(it)
                        s.replace(start, end, it.tag.fullName)
                        highlightTag(editText.context, s, start, fullName.length, keepTextOnDelete)
                    }
                    processingCandidates = false
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    val clickables = editText.text.getSpans(start, start + count, ClickableSpan::class.java)
                    val chips = editText.text.getSpans(start, start + count, ImageSpan::class.java)
                    clickables.forEach { editText.text.removeSpan(it) }
                    chips.forEach { editText.text.removeSpan(it) }
                }
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (processingCandidates) return
                    val end = start + count
                    val newText = s.substring(start, end)
                    if (newText.contains(' ')) {
                        val prevText = s.substring(0, end)
                        val prevWord = prevText.trim().takeLastWhile { it != ' ' }
                        if (prevWord.isEmpty())
                            return
                        val tagName = prevWord.toLowerCase()
                        val manualTag = looksLikeTag(tagName)
                        val tag = cache[tagName] ?: if (manualTag) {
                            DynalistTag(tagName)
                        } else null ?: if (autoDetectTagNames) {
                            cache["#$tagName"] ?: cache["@$tagName"]
                        } else null
                        tag?.let {
                            val tagStart = prevText.lastIndexOf(prevWord, prevText.length)
                            val tagEnd = tagStart + tagName.length
                            val existingSpans =
                                    editText.text.getSpans(tagStart, tagEnd, ImageSpan::class.java)
                            if (existingSpans.isEmpty())
                                editText.text.setSpan(TagCandidate(tag), tagStart, tagEnd, 0)
                        }
                    }
                }
            })
        }
    }
}
