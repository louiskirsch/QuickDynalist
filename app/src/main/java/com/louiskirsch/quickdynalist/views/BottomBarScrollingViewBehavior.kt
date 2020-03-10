package com.louiskirsch.quickdynalist.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

class BottomBarScrollingViewBehavior: AppBarLayout.ScrollingViewBehavior {

    constructor() : super()
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        val superDependence = super.layoutDependsOn(parent, child, dependency)
        return superDependence || dependency.tag == "bottomBar"
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        parent.findViewWithTag<View>("bottomBar")?.let { dependency ->
            val paddingBottom = if (dependency.visibility == View.VISIBLE) dependency.height else 0
            child.setPadding(child.paddingLeft, child.paddingTop, child.paddingRight, paddingBottom)
        }
        return super.onLayoutChild(parent, child, layoutDirection)
    }
}