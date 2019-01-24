package com.louiskirsch.quickdynalist

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import android.webkit.URLUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import org.jetbrains.anko.okButton


class ProcessTextActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var text: String
    private lateinit var bookmarks: List<DynalistItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        dynalist.subscribe()

        if (!intent.hasExtra(Intent.EXTRA_TEXT) && !intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)) {
            alert {
                titleResource = R.string.dialog_title_error
                messageResource = R.string.invalid_intent_error
                okButton {}
                show()
            }
            finish()
            return
        }

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

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> {
            bookmarks = it
        })
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
                alert {
                    items(bookmarks) { _, selectedLocation: DynalistItem, _ ->
                        dynalist.addItem(text, selectedLocation)
                    }
                    onCancelled {
                        dynalist.addItem(text, bookmarks.find { it.isInbox }!!)
                    }
                    show()
                }
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION)
                        dynalist.addItem(text, bookmarks.find { it.isInbox }!!)
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
