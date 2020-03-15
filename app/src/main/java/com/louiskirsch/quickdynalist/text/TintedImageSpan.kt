package com.louiskirsch.quickdynalist.text

import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

class TintedImageSpan(drawable: Drawable, verticalAlignment: Int, val lineHeight: Float = 1.0f)
    : ImageSpan(drawable, verticalAlignment)