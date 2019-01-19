package com.louiskirsch.quickdynalist

import android.app.Activity
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

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

val IntRange.size: Int get() = endInclusive - start + 1