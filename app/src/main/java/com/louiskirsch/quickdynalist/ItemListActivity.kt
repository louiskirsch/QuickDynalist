package com.louiskirsch.quickdynalist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.core.app.NavUtils
import com.louiskirsch.quickdynalist.jobs.Bookmark
import kotlinx.android.synthetic.main.activity_item_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.louiskirsch.quickdynalist.adapters.ItemListAdapter


class ItemListActivity : Activity() {
    private val dynalist: Dynalist = Dynalist(this)

    lateinit var parent: Bookmark
    lateinit var adapter: ItemListAdapter

    companion object {
        const val EXTRA_BOOKMARK = "EXTRA_BOOKMARK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        itemContents.setText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        parent = intent.getSerializableExtra(EXTRA_BOOKMARK) as Bookmark
        parent = dynalist.bookmarks.first { it.id == parent.id }
        title = parent.shortenedName

        setupItemContentsTextField()

        submitButton.setOnClickListener {
            dynalist.addItem(itemContents.text.toString(), parent)
            itemContents.text.clear()
        }
        submitButton.isEnabled = itemContents.text.isNotEmpty()

        itemList.layoutManager = LinearLayoutManager(this)
        val dividerItemDecoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        itemList.addItemDecoration(dividerItemDecoration)
        adapter = ItemListAdapter(parent.children)
        itemList.adapter = adapter
    }

    private fun setupItemContentsTextField() {
        with(itemContents!!) {
            setupGrowingMultiline(5)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    submitButton.isEnabled = itemContents.text.isNotEmpty()
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                val isDone = actionId == EditorInfo.IME_ACTION_DONE
                if (isDone && submitButton.isEnabled) {
                    submitButton!!.performClick()
                }
                isDone
            }
        }
    }

    private val actionBarView: View get() {
        val resId = resources.getIdentifier("action_bar_container", "id", "android")
        return window.decorView.findViewById(resId) as View
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            android.R.id.home -> {
                // TODO actually I should use NavUtils here,
                // but they don't have the transition magic
                fixedFinishAfterTransition()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBookmarkContentsUpdatedEvent(event: BookmarkContentsUpdatedEvent) {
        if (event.bookmark.id == parent.id) {
            parent = event.bookmark
            adapter.updateItems(parent.children)
            itemList.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
