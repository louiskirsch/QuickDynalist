package com.louiskirsch.quickdynalist

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.activity_item_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.adapters.ItemListAdapter
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import org.jetbrains.anko.*


class ItemListActivity : AppCompatActivity() {
    private val dynalist: Dynalist = Dynalist(this)

    lateinit var parent: DynalistItem
    lateinit var adapter: ItemListAdapter

    companion object {
        const val EXTRA_BOOKMARK = "EXTRA_BOOKMARK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        actionBarView.transitionName = "toolbar"
        window.allowEnterTransitionOverlap = true

        itemContents.setText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))

        parent = intent.getParcelableExtra(EXTRA_BOOKMARK) as DynalistItem
        title = parent.shortenedName

        setupItemContentsTextField()

        submitButton.setOnClickListener {
            dynalist.addItem(itemContents.text.toString(), parent)
            itemContents.text.clear()
        }
        submitButton.isEnabled = itemContents.text.isNotEmpty()

        itemList.layoutManager = LinearLayoutManager(this)
        adapter = ItemListAdapter(emptyList()).apply {
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount == 1)
                        itemList.scrollToPosition(positionStart)
                }
            })
        }
        itemList.adapter = adapter

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.getItemsLiveData(parent).observe(this, Observer<List<DynalistItem>> {
            adapter.updateItems(it)
        })
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (parent.serverFileId != null && parent.serverItemId != null)
            menuInflater.inflate(R.menu.item_list_activity_menu, menu)
        if (parent.isInbox && !parent.markedAsPrimaryInbox)
            menuInflater.inflate(R.menu.item_list_activity_primary_inbox_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            android.R.id.home -> {
                // TODO actually I should use NavUtils here,
                // but they don't have the transition magic
                fixedFinishAfterTransition()
                return true
            }
            R.id.inbox_help -> showInboxHelp()
            R.id.open_in_dynalist -> openInDynalist()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openInDynalist(): Boolean {
        browse("https://dynalist.io/d/${parent.serverFileId}#z=${parent.serverItemId}")
        return true
    }

    private fun showInboxHelp(): Boolean {
        alert {
            titleResource = R.string.inbox_help
            messageResource = R.string.inbox_help_text
            okButton {}
            show()
        }
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
