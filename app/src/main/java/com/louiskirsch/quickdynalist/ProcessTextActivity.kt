package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import android.webkit.URLUtil
import com.louiskirsch.quickdynalist.jobs.Bookmark
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.alert
import org.jetbrains.anko.contentView
import org.jetbrains.anko.toast
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.find
import android.R.attr.y
import android.R.attr.x
import android.view.Gravity
import android.R.attr.gravity
import android.view.WindowManager




class ProcessTextActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var text: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        dynalist.subscribe()

        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()

        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        if (subject != null && URLUtil.isNetworkUrl(text))
            text = "[$subject]($text)"

        if (savedInstanceState == null) {
            if (!dynalist.isAuthenticated) {
                dynalist.authenticate()
            } else {
                addItem()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val lp = (window.decorView.layoutParams as WindowManager.LayoutParams).apply {
            gravity = Gravity.FILL_HORIZONTAL or Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        windowManager.updateViewLayout(window.decorView, lp)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        if (event.success) {
            addItem()
        }
    }

    private fun addItem() {
        Snackbar.make(window.decorView, R.string.add_item_success, Snackbar.LENGTH_SHORT).apply {
            setAction(R.string.item_change_target_location) {
                val bookmarks = listOf(Bookmark.newInbox()) + dynalist.bookmarks
                alert {
                    items(bookmarks) { _: DialogInterface, selectedLocation: Bookmark, _: Int ->
                        dynalist.addItem(text, selectedLocation)
                    }
                    onCancelled {
                        dynalist.addItem(text, Bookmark.newInbox())
                    }
                    show()
                }
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION)
                        dynalist.addItem(text, Bookmark.newInbox())
                }
            })
            show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
        finish()
    }
}
