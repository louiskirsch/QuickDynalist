package com.louiskirsch.quickdynalist


import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.birbit.android.jobqueue.TagConstraint
import com.google.android.material.snackbar.Snackbar
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.adapters.ItemListAdapter
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import android.util.Pair as UtilPair


private const val ARG_BOOKMARK = "EXTRA_BOOKMARK"
private const val ARG_ITEM_TEXT = "EXTRA_ITEM_TEXT"

class ItemListFragment : Fragment() {

    private lateinit var dynalist: Dynalist
    private lateinit var parent: DynalistItem
    private lateinit var adapter: ItemListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynalist = Dynalist(context!!)
        arguments?.let {
            parent = it.getParcelable(ARG_BOOKMARK)!!
        }
        setHasOptionsMenu(true)

        adapter = ItemListAdapter().apply {
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount == 1)
                        itemList.scrollToPosition(positionStart)
                }
            })
        }

        adapter.onClickListener = {
            if (it.serverItemId != null) {
                openDynalistItem(it)
            } else {
                alertRequireSync()
            }
        }

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.getItemsLiveData(parent).observe(this, Observer<List<DynalistItem>> { items ->
            doAsync {
                items.forEach { item -> item.children.sortBy { child -> child.position } }
                val cachedItems = items.map { CachedDynalistItem(it, context!!) }
                uiThread {
                    adapter.updateItems(cachedItems)
                }
            }
        })
    }

    private fun alertRequireSync() {
        Snackbar.make(itemList, R.string.alert_sync_required, Snackbar.LENGTH_SHORT).apply {
            setAction(R.string.action_sync) {
                DynalistApp.instance.jobManager.run {
                    cancelJobsInBackground({
                        val job = BookmarksJob(false)
                        addJobInBackground(job)
                    }, TagConstraint.ALL, arrayOf(BookmarksJob.TAG))
                }
            }
            show()
        }
    }

    private fun openDynalistItem(item: DynalistItem): Boolean {
        val fragment = newInstance(item, itemContents.text)
        fragmentManager!!.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        itemContents.text.clear()
        return true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_item_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        itemContents.setText(arguments!!.getCharSequence(ARG_ITEM_TEXT))

        setupItemContentsTextField()

        submitButton.setOnClickListener {
            dynalist.addItem(itemContents.text.toString(), parent)
            itemContents.text.clear()
        }
        updateSubmitEnabled()

        advancedItemButton.setOnClickListener {
            val intent = Intent(context, AdvancedItemActivity::class.java)
            intent.putExtra(Intent.EXTRA_SUBJECT, parent as Parcelable)
            intent.putExtra(Intent.EXTRA_TEXT, itemContents.text)
            val activity = activity as AppCompatActivity
            val transition = ActivityOptions.makeSceneTransitionAnimation(activity,
                    UtilPair.create(activity.toolbar as View, "toolbar"),
                    UtilPair.create(itemContents as View, "itemContents"))
            startActivity(intent, transition.toBundle())
            itemContents.text.clear()
        }

        itemList.layoutManager = LinearLayoutManager(context)
        itemList.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        activity!!.title = parent.shortenedName
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedBookmark.value = parent
        itemContents.requestFocus()
    }

    private fun updateSubmitEnabled() {
        submitButton.isEnabled = itemContents.text.isNotEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        if (parent.serverFileId != null && parent.serverItemId != null) {
            inflater!!.inflate(R.menu.item_list_activity_menu, menu)
            menu!!.findItem(R.id.goto_parent).isVisible = !parent.parent.isNull
        }
        if (parent.isInbox && !parent.markedAsPrimaryInbox)
            inflater!!.inflate(R.menu.item_list_activity_primary_inbox_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun setupItemContentsTextField() {
        with(itemContents!!) {
            setupGrowingMultiline(5)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    updateSubmitEnabled()
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

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            R.id.inbox_help -> showInboxHelp()
            R.id.open_in_dynalist -> openInDynalist()
            R.id.goto_parent -> openDynalistItem(parent.parent.target)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openInDynalist(): Boolean {
        context!!.browse("https://dynalist.io/d/${parent.serverFileId}#z=${parent.serverItemId}")
        return true
    }

    private fun showInboxHelp(): Boolean {
        context!!.alert {
            titleResource = R.string.inbox_help
            messageResource = R.string.inbox_help_text
            okButton {}
            show()
        }
        return true
    }

    companion object {
        @JvmStatic
        fun newInstance(parent: DynalistItem, itemText: CharSequence) =
                ItemListFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable(ARG_BOOKMARK, parent)
                        putCharSequence(ARG_ITEM_TEXT, itemText)
                    }
                }
    }
}
