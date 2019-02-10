package com.louiskirsch.quickdynalist.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

@Keep
class ScrollFABBehavior(context: Context?, attrs: AttributeSet?)
    : FloatingActionButton.Behavior(context, attrs) {

    companion object {
        val hideListener = object: FloatingActionButton.OnVisibilityChangedListener() {
            override fun onHidden(fab: FloatingActionButton) {
                fab.visibility = View.INVISIBLE
            }
        }
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout,
                                     child: FloatingActionButton, directTargetChild: View,
                                     target: View, axes: Int, type: Int): Boolean {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL ||
                super.onStartNestedScroll(coordinatorLayout, child, directTargetChild,target, axes,
                        type)
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout, child: FloatingActionButton,
                                target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
                                dyUnconsumed: Int, type: Int) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed,
                dyUnconsumed, type)
        if (dyConsumed < 0 && child.visibility == View.VISIBLE) {
            child.hide(hideListener)
        } else if (dyConsumed > 0 && child.visibility != View.VISIBLE) {
            child.show()
        }
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout,
                                    child: FloatingActionButton, target: View, type: Int) {
        super.onStopNestedScroll(coordinatorLayout, child, target, type)
        (target as? RecyclerView)?.let { recycler ->
            (recycler.layoutManager as? LinearLayoutManager)?.let {
                val lastItem = it.itemCount - 1
                val lastVisible = it.findLastVisibleItemPosition()
                if (lastVisible >= lastItem) {
                    child.hide(hideListener)
                }
            }
        }
    }
}