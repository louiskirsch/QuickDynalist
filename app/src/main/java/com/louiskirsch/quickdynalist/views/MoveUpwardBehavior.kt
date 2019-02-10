package com.louiskirsch.quickdynalist.views

import android.content.Context
import androidx.core.view.ViewCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.util.AttributeSet
import android.view.View
import com.google.android.material.snackbar.Snackbar
import androidx.annotation.Keep


@Keep
class MoveUpwardBehavior(context: Context?, attrs: AttributeSet?)
    : CoordinatorLayout.Behavior<View>(context, attrs) {

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency is Snackbar.SnackbarLayout
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        child.translationY = Math.min(0f, dependency.translationY - dependency.height)
        return true
    }

    override fun onDependentViewRemoved(parent: CoordinatorLayout, child: View, dependency: View) {
        ViewCompat.animate(child).translationY(0f).start()
    }
}