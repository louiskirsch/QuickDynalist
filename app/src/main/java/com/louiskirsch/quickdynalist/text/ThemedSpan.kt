package com.louiskirsch.quickdynalist.text

import android.content.Context
import android.content.res.Resources
import android.text.Spannable

class ThemedSpan(private val spanCreator: (Resources) -> Any) {

    fun apply(context: Context, spannable: Spannable) {
        val start = spannable.getSpanStart(this)
        val end = spannable.getSpanEnd(this)
        val flags = spannable.getSpanFlags(this)
        val newSpan = spanCreator(context.resources)
        spannable.removeSpan(this)
        spannable.setSpan(newSpan, start, end, flags)
    }

    companion object {
        fun applyAll(context: Context, spannable: Spannable) {
            spannable.getSpans(0, spannable.length, ThemedSpan::class.java).forEach {
                it.apply(context, spannable)
            }
        }
    }
}