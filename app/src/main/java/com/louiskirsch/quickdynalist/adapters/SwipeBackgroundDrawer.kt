package com.louiskirsch.quickdynalist.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView


class SwipeBackgroundDrawer(context: Context, swipeLeftDrawable: Int, swipeRightDrawable: Int,
                            swipeLeftColor: Int, swipeRightColor: Int) {

    private class Dimensions(icon: Drawable) {
        val intrinsicWidth = icon.intrinsicWidth
        val intrinsicHeight = icon.intrinsicHeight
    }

    private val swipeRightDrawable = context.getDrawable(swipeRightDrawable)!!
    private val swipeLeftDrawable = context.getDrawable(swipeLeftDrawable)!!
    private val swipeLeftDimensions = Dimensions(this.swipeLeftDrawable)
    private val swipeRightDimensions = Dimensions(this.swipeRightDrawable)
    private val background = ColorDrawable()
    private val swipeLeftColor = ContextCompat.getColor(context, swipeLeftColor)
    private val swipeRightColor = ContextCompat.getColor(context, swipeRightColor)
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    fun draw(c: Canvas, viewHolder: RecyclerView.ViewHolder, dX: Float, isCurrentlyActive: Boolean) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(c, itemView.right + dX, itemView.top.toFloat(),
                    itemView.right.toFloat(), itemView.bottom.toFloat())
            return
        }

        val isSwipeRight = dX > 0f
        val drawable = if(isSwipeRight) swipeRightDrawable else swipeLeftDrawable
        val dimensions = if(isSwipeRight) swipeRightDimensions else swipeLeftDimensions
        val color = if (isSwipeRight) swipeRightColor else swipeLeftColor

        background.color = color
        if (isSwipeRight) {
            background.setBounds(itemView.left, itemView.top,
                    itemView.left + dX.toInt(), itemView.bottom)
        } else {
            background.setBounds(itemView.right + dX.toInt(), itemView.top,
                    itemView.right, itemView.bottom)
        }
        background.draw(c)

        val iconTop = itemView.top + (itemHeight - dimensions.intrinsicHeight) / 2
        val iconMargin = (itemHeight - dimensions.intrinsicHeight) / 2
        val iconLeft = if (isSwipeRight) {
            itemView.left + iconMargin
        } else {
            itemView.right - iconMargin - dimensions.intrinsicWidth
        }
        val iconRight = if (isSwipeRight) {
            itemView.left + iconMargin + dimensions.intrinsicWidth
        } else {
            itemView.right - iconMargin
        }
        val iconBottom = iconTop + dimensions.intrinsicHeight

        drawable.setBounds(iconLeft, iconTop, iconRight, iconBottom)
        drawable.draw(c)
    }

    private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
        c?.drawRect(left, top, right, bottom, clearPaint)
    }
}