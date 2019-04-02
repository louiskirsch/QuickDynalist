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
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.SpeechRecognitionHelper
import org.jetbrains.anko.okButton


class ProcessTextActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)
    private val speechRecognitionHelper = SpeechRecognitionHelper()
    private var text: String? = null
    private lateinit var bookmarks: List<DynalistItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        dynalist.subscribe()

        if (intent.action == "com.louiskirsch.quickdynalist.RECORD_SPEECH") {
            speechRecognitionHelper.startSpeechRecognition(this)
        } else {
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (text == null) {
                toast(R.string.invalid_intent_error)
                finish()
            } else {
                val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
                if (subject != null && URLUtil.isNetworkUrl(text))
                    text = "[$subject]($text)"

                if (savedInstanceState == null)
                    tryAddItem()
            }
        }

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> {
            bookmarks = it
        })
    }

    private fun tryAddItem() {
        if (!dynalist.isAuthenticated) {
            dynalist.authenticate()
        } else {
            addItem()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        speechRecognitionHelper.dispatchResult(this,
                requestCode, resultCode, data, ::finish) {
            text = it
            tryAddItem()
        }
        super.onActivityResult(requestCode, resultCode, data)
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
                        dynalist.addItem(text!!, selectedLocation)
                    }
                    onCancelled {
                        dynalist.addItem(text!!, bookmarks.find { it.isInbox }!!)
                    }
                    show()
                }
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION)
                        dynalist.addItem(text!!, bookmarks.find { it.isInbox }!!)
                }
            })
            show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success && !event.retrying)
            toast(R.string.error_update_server)
        finish()
    }
}
