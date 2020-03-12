package com.louiskirsch.quickdynalist.utils

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.text.util.Linkify
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.find
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.os.Parcel
import android.text.*
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.roundToInt
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.RemoteViews
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.network.DynalistResponse
import retrofit2.Call
import retrofit2.Response


fun EditText.setupGrowingMultiline(maxLines: Int) {
    // These properties must be set programmatically because order of execution matters
    inputType = (InputType.TYPE_CLASS_TEXT or
                 InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                 InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    setSingleLine(true)
    this.maxLines = maxLines
    setHorizontallyScrolling(false)
    imeOptions = EditorInfo.IME_ACTION_DONE
}

fun Activity.fixedFinishAfterTransition() {
    if (currentFocus != null) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
    }
    finishAfterTransition()
}

val AppCompatActivity.actionBarView: View
    get() = window.decorView.find(R.id.action_bar_container)

val IntRange.size: Int get() = endInclusive - start + 1

val <A, B> Pair<A?, B?>.selfNotNull: Pair<A, B>? get() {
    return if (first != null && second != null)
        Pair(first!!, second!!)
    else
        null
}

val Boolean.int: Int get() = if (this) 1 else 0

fun CharSequence.prependIfNotBlank(text: CharSequence) =
        if (isNotBlank()) TextUtils.concat(text, this) else this

fun <T: Spannable> T.linkify(mask: Int = Linkify.ALL): T
        = Linkify.addLinks(this, mask).let { this }

val Context.inputMethodManager: InputMethodManager
    get() = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

fun CharSequence.ellipsis(maxLength: Int): CharSequence {
    return if (length > maxLength)
        "${take(maxLength - 1)}…"
    else
        this
}

fun String.toBitmap(textSize: Float, textColor: Int): Bitmap {
    val paint = Paint(ANTI_ALIAS_FLAG)
    paint.textSize = textSize
    paint.color = textColor
    paint.textAlign = Paint.Align.LEFT
    val baseline = -paint.ascent() // ascent() is negative
    val width = (paint.measureText(this) + 0.5f).roundToInt()
    val height = (baseline + paint.descent() + 0.5f).roundToInt()
    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawText(this, 0f, baseline, paint)
    return image
}

fun SpannableStringBuilder.replaceAll(regex: Regex, transform: (MatchResult) -> CharSequence): SpannableStringBuilder {
    var match: MatchResult? = regex.find(this) ?: return this

    var lastStart: Int
    do {
        val foundMatch = match!!
        replace(foundMatch.range.start, foundMatch.range.endInclusive + 1,
                transform(foundMatch))
        lastStart = foundMatch.range.endInclusive + 1
        match = foundMatch.next()
    } while (lastStart < length && match != null)
    return this
}

fun TextView.isEllipsized(callback: (isEllipsized: Boolean) -> Unit) {
    if (layout != null) {
        val lines = layout.lineCount
        callback(lines > 0 && layout.getEllipsisCount(lines - 1) > 0)
        return
    }
    viewTreeObserver.addOnGlobalLayoutListener(object: OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            var result = false
            layout?.let {
                val lines = it.lineCount
                result = lines > 0 && it.getEllipsisCount(lines - 1) > 0
            }
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            callback(result)
        }
    })
}

fun <T: DynalistResponse>
        Call<T>.execRespectRateLimit(delayCallback: ((Response<T>, Long) -> Unit)? = null,
                                     delay: Long = 60 * 1000L): Response<T> {
    val response = execute()
    val body = response.body()!!
    if (body.isRateLimitExceeded) {
        delayCallback?.invoke(response, delay)
        Thread.sleep(delay)
        return clone().execute()
    }
    return response
}

val RecyclerView.children: List<RecyclerView.ViewHolder> get() {
    return IntRange(0, layoutManager!!.childCount - 1).map {
        getChildViewHolder(layoutManager!!.getChildAt(it)!!)
    }
}

val ViewGroup.children: List<View> get() {
    return (0 until childCount).map { getChildAt(it) }
}

fun Parcel.writeNullableLong(value: Long?) {
    writeInt((value == null).int)
    if (value != null)
        writeLong(value)
}

fun Parcel.readNullableLong(): Long? {
    val isNull = readInt() > 0
    return if (!isNull) readLong() else null
}

fun Parcel.writeNullableInt(value: Int?) {
    writeInt((value == null).int)
    if (value != null)
        writeInt(value)
}

fun Parcel.readNullableInt(): Int? {
    val isNull = readInt() > 0
    return if (!isNull) readInt() else null
}

fun Parcel.writeNullableBoolean(value: Boolean?) {
    writeInt((value == null).int)
    if (value != null)
        writeInt(value.int)
}

fun Parcel.readNullableBoolean(): Boolean? {
    val isNull = readInt() > 0
    return if (!isNull) readInt() > 0 else null
}

val PopupMenu.helper: MenuPopupHelper? get() {
    return try {
        javaClass.getDeclaredField("mPopup").let {
            it.isAccessible = true
            it.get(this) as MenuPopupHelper
        }
    } catch (e: Exception) { null }
}

fun Resources.dpToPixels(dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics)
}

fun Context.resolveColorAttribute(attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return if (typedValue.resourceId != 0)
        ContextCompat.getColor(this, typedValue.resourceId)
    else
        typedValue.data
}

fun TextView.scaleImageSpans() {
    val spannable = text as SpannedString
    spannable.getSpans(0, text.length, ImageSpan::class.java).forEach {
        val width = it.drawable.bounds.width()
        it.drawable.setBounds(0, 0, width, (lineHeight * 1.3f).toInt())
    }
}

fun Spannable.clearSpans(types: List<Class<*>>, start: Int = 0, end: Int = length) {
    types.forEach { type -> getSpans(start, end, type).forEach { removeSpan(it) } }
}