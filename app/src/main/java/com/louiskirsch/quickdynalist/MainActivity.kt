package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import com.louiskirsch.quickdynalist.jobs.Bookmark
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.*
import java.util.*

class MainActivity : Activity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var adapter: ArrayAdapter<Bookmark>

    private val preferences: SharedPreferences
        get() = getSharedPreferences("MAIN_ACTIVITY", Context.MODE_PRIVATE)

    private var bookmarkHintCounter: Int
        get() = preferences.getInt("BOOKMARK_HINT_COUNTER", 0)
        set(value) = preferences.edit().putInt("BOOKMARK_HINT_COUNTER", value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupItemContentsTextField()

        submitButton!!.setOnClickListener {
            dynalist.addItem(itemContents!!.text.toString(),
                    itemLocation!!.selectedItem as Bookmark)
            itemContents!!.text.clear()
        }

        itemLocation.setOnTouchListener { _, e ->
            if ((e.action == MotionEvent.ACTION_DOWN ||
                    e.action == MotionEvent.ACTION_POINTER_DOWN) && bookmarkHintCounter < 5) {
                longToast(R.string.bookmark_hint)
                bookmarkHintCounter += 1
            }
            false
        }

        val bookmarks = arrayOf(Bookmark.newInbox()) + dynalist.bookmarks
        adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, bookmarks.toMutableList())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemLocation!!.adapter = adapter

        val bookmarksOutdated = dynalist.lastBookmarkQuery.time <
                Date().time - 60 * 1000L
        if (savedInstanceState == null && dynalist.isAuthenticated && bookmarksOutdated) {
            val jobManager = DynalistApp.instance.jobManager
            jobManager.addJobInBackground(BookmarksJob())
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    private fun setupItemContentsTextField() {
        with(itemContents!!) {
            // These properties must be set programmatically because order of execution matters
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            maxLines = 5
            setHorizontallyScrolling(false)
            imeOptions = EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    updateSubmitEnabled()
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                val isDone = actionId == EditorInfo.IME_ACTION_DONE
                if (isDone) {
                    submitButton!!.performClick()
                }
                isDone
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSubmitEnabled()
        if (!dynalist.isAuthenticated && !dynalist.isAuthenticating) {
            dynalist.authenticate()
        } else {
            itemContents!!.requestFocus()
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    private fun updateSubmitEnabled() {
        val enabled = dynalist.isAuthenticated && !itemContents!!.text.toString().isEmpty()
        submitButton!!.isEnabled = enabled
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBookmarksUpdated(event: BookmarksUpdatedEvent) {
        val bookmarks = arrayOf(Bookmark.newInbox()) + event.newBookmarks
        adapter.clear()
        adapter.addAll(bookmarks.toList())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        updateSubmitEnabled()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
