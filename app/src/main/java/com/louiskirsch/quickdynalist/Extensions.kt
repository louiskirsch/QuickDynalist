package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.text.Spannable
import android.text.TextUtils
import android.text.util.Linkify
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.find

fun EditText.setupGrowingMultiline(maxLines: Int) {
    // These properties must be set programmatically because order of execution matters
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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

fun Spannable.linkify(mask: Int = Linkify.ALL): Spannable
        = Linkify.addLinks(this, mask).let { this }

val Context.inputMethodManager: InputMethodManager
    get() = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
