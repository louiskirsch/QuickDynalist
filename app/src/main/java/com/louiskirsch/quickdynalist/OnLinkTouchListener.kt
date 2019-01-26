package com.louiskirsch.quickdynalist

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.text.style.ClickableSpan
import android.widget.TextView
import android.text.Spannable


/**
 * Allows TextViews with clickable links while still not handling all touch events
 */
class OnLinkTouchListener: View.OnTouchListener {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        var ret = false
        val text = (v as TextView).text
        val stext = Spannable.Factory.getInstance().newSpannable(text)
        val action = event!!.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            var x = event.x
            var y = event.y

            x -= v.totalPaddingLeft
            y -= v.totalPaddingTop

            x += v.scrollX
            y += v.scrollY

            val layout = v.layout
            val line = layout.getLineForVertical(y.toInt())
            val off = layout.getOffsetForHorizontal(line, x)

            val link = stext.getSpans(off, off, ClickableSpan::class.java)

            if (link.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) {
                    link[0].onClick(v)
                }
                ret = true
            }
        }
        return ret
    }
}