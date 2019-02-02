package com.louiskirsch.quickdynalist.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.children

class ItemTouchCallback(private val adapter: ItemTouchHelperContract): ItemTouchHelper.Callback() {

    private var dropIntoTarget: RecyclerView.ViewHolder? = null
    private var dragging: Boolean = false

    override fun isLongPressDragEnabled(): Boolean = true
    override fun isItemViewSwipeEnabled(): Boolean = true

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder,
                             target: RecyclerView.ViewHolder): Boolean {
        return adapter.canDropOver(target.adapterPosition)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean {
        adapter.onRowMoved(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.onRowSwiped(viewHolder.adapterPosition)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            dragging = true
            adapter.onMoveStart(viewHolder.adapterPosition)
            val recyclerView = viewHolder.itemView.parent as RecyclerView
            recyclerView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE) {
                    val curY = event.y
                    val targets = recyclerView.children.filter { it != viewHolder }
                    val targetBelow = targets.firstOrNull {
                        curY > it.itemView.top && curY < it.itemView.bottom
                    }
                    if (dropIntoTarget != targetBelow) {
                        dropIntoTarget?.itemView?.isActivated = false
                        targetBelow?.itemView?.isActivated = true
                        dropIntoTarget = targetBelow
                    }
                }
                false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (dragging) {
            if (dropIntoTarget != null) {
                adapter.onRowMovedInto(viewHolder.adapterPosition, dropIntoTarget!!.adapterPosition)
                dropIntoTarget!!.itemView.isActivated = false
                dropIntoTarget = null
            } else {
                adapter.onRowMovedToDestination(viewHolder.adapterPosition)
            }
            recyclerView.setOnTouchListener(null)
            adapter.onMoveEnd(viewHolder.adapterPosition)
            dragging = false
        }
    }

    interface ItemTouchHelperContract {
        fun canDropOver(position: Int): Boolean
        fun onMoveStart(position: Int)
        fun onMoveEnd(position: Int)
        fun onRowMoved(fromPosition: Int, toPosition: Int)
        fun onRowMovedToDestination(toPosition: Int)
        fun onRowMovedInto(fromPosition: Int, intoPosition: Int)
        fun onRowSwiped(position: Int)
    }
}