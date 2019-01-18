package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
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

class ProcessTextActivity : Activity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var text: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()

        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        if (subject != null && URLUtil.isNetworkUrl(text))
            text = "[$subject]($text)"
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()

        if (!dynalist.isAuthenticated) {
            dynalist.authenticate()
        } else {
            addItem()
        }
    }

    override fun onStop() {
        super.onStop()
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
        Snackbar.make(contentView!!, R.string.add_item_success, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.item_change_target_location) {
                val bookmarks = listOf(Bookmark.newInbox()) + dynalist.bookmarks
                alert {
                    items(bookmarks) { _: DialogInterface, selectedLocation: Bookmark, _: Int ->
                        dynalist.addItem(text, selectedLocation)
                    }
                    onCancelled {
                        dynalist.addItem(text, Bookmark.newInbox())
                    }
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
        if (event.success)
            toast(R.string.add_item_success)
        else
            toast(R.string.add_item_error)
        finish()
    }
}
