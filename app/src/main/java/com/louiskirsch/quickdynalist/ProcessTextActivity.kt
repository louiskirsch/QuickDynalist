package com.louiskirsch.quickdynalist

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import android.webkit.URLUtil
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.SpeechRecognitionHelper


class ProcessTextActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)
    private val speechRecognitionHelper = SpeechRecognitionHelper()
    private var text: String? = null
    private var location: DynalistItem? = null
    private var bookmarks: List<DynalistItem> = emptyList()

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        location = dynalist.inbox
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.bookmarksLiveData.observe(this, Observer<List<DynalistItem>> {
            bookmarks = it
        })

        if (intent.action == getString(R.string.ACTION_RECORD_SPEECH)) {
            speechRecognitionHelper.startSpeechRecognition(this)
        } else {
            text = deriveText()
            if (text == null) {
                finishWithError()
            } else if(savedInstanceState == null) {
                tryAddItem()
            }
        }
    }

    private fun finishWithError() {
        toast(R.string.invalid_intent_error)
        finish()
    }

    private fun deriveText(): String? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: return null
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        return if (subject != null && URLUtil.isNetworkUrl(text))
            "[$subject]($text)"
        else
            text
    }

    private fun tryAddItem() {
        if (!dynalist.isAuthenticated || location == null) {
            val configureOnlyInbox = dynalist.isAuthenticated
            startActivityForResult(dynalist.createAuthenticationIntent(configureOnlyInbox),
                    resources.getInteger(R.integer.request_code_authenticate))
        } else {
            showSnackbar()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val lp = (window.decorView.layoutParams as WindowManager.LayoutParams).apply {
            gravity = Gravity.FILL_HORIZONTAL or Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        windowManager.updateViewLayout(window.decorView, lp)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        speechRecognitionHelper.dispatchResult(this,
                requestCode, resultCode, data, ::finish) {
            text = it
            tryAddItem()
        }
        when (requestCode) {
            resources.getInteger(R.integer.request_code_authenticate) -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (location == null)
                        location = dynalist.inbox
                    showSnackbar()
                } else {
                    finishWithError()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing && text != null && location != null)
            dynalist.addItem(text!!, location!!)
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && location != null) {
            dynalist.addItem(text!!, location!!)
            finish()
        }
    }

    private fun showSnackbar() {
        Snackbar.make(window.decorView, R.string.add_item_success, Snackbar.LENGTH_SHORT).apply {
            setAction(R.string.item_change_target_location) {
                alert {
                    items(bookmarks) { _, selectedLocation: DynalistItem, _ ->
                        location = selectedLocation
                        finish()
                    }
                    onCancelled { finish() }
                    show()
                }
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION)
                        finish()
                }
            })
            show()
        }
    }
}
