package com.louiskirsch.quickdynalist.text

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Parcel
import android.text.Layout
import android.text.style.BulletSpan

class IndentedBulletSpan: BulletSpan {
    private val indentWidth: Int

    constructor(gapWidth: Int, indentWidth: Int) : super(gapWidth) {
        this.indentWidth = indentWidth
    }
    constructor(src: Parcel) : super(src) {
        this.indentWidth = src.readInt()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeInt(indentWidth)
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return super.getLeadingMargin(first) + indentWidth
    }

    override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int,
                                   baseline: Int, bottom: Int, text: CharSequence, start: Int,
                                   end: Int, first: Boolean, layout: Layout?) {
        val newX = x + indentWidth * dir
        super.drawLeadingMargin(canvas, paint, newX, dir, top, baseline, bottom, text, start, end,
                first, layout)
    }

}