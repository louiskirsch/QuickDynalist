package com.louiskirsch.quickdynalist


import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.birbit.android.jobqueue.TagConstraint
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.adapters.ItemListAdapter
import com.louiskirsch.quickdynalist.adapters.ItemTouchCallback
import com.louiskirsch.quickdynalist.jobs.DeleteItemJob
import com.louiskirsch.quickdynalist.jobs.EditItemJob
import com.louiskirsch.quickdynalist.jobs.MoveItemJob
import com.louiskirsch.quickdynalist.jobs.SyncJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import io.objectbox.kotlin.boxFor
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import android.util.Pair as UtilPair

class ItemListFragment : Fragment() {

    private lateinit var dynalist: Dynalist
    private lateinit var location: DynalistItem
    private lateinit var adapter: ItemListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynalist = Dynalist(context!!)
        arguments!!.let {
            location = it.getParcelable(ARG_LOCATION)!!
            DynalistApp.instance.boxStore.boxFor<DynalistItem>().attach(location)
        }
        setHasOptionsMenu(true)

        adapter = ItemListAdapter(location.isChecklist).apply {
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount == 1 && !adapter.moveInProgress)
                        itemList.scrollToPosition(positionStart)
                }
            })
        }
        adapter.onClickListener = { openDynalistItem(it) }
        adapter.onPopupItemClickListener = { item, menuItem ->
            when (menuItem.itemId) {
                R.id.action_show_details -> showItemDetails(item)
                R.id.action_edit -> editItem(item)
            }
            true
        }
        adapter.onRowMovedIntoListener = { from, to ->
            DynalistApp.instance.jobManager.addJobInBackground(
                    MoveItemJob(from, to, -1)
            )
        }
        adapter.onRowMovedListener = { item, toPosition ->
            DynalistApp.instance.jobManager.addJobInBackground(
                    MoveItemJob(item, item.parent.target, toPosition)
            )
        }
        adapter.onRowMovedOnDropoffListener = { item, dropId ->
            when (dropId) {
                R.id.dropoff_parent -> {
                    if (location.parent.isNull) {
                        context!!.toast(R.string.error_dropoff_no_parent)
                        false
                    } else {
                        DynalistApp.instance.jobManager.addJobInBackground(
                                MoveItemJob(item, location.parent.target, -1)
                        )
                        true
                    }
                }
                R.id.dropoff_duplicate -> {
                    // TODO implement
                    context!!.toast(R.string.error_not_implemented_duplicate)
                    false
                }
                else -> false
            }
        }
        adapter.onRowSwipedListener = { deleteItem(it) }
        adapter.onCheckedStatusChangedListener = { item, checked ->
            item.isChecked = checked
            DynalistApp.instance.jobManager.addJobInBackground(EditItemJob(item))
        }

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsParent.value = location
        model.itemsLiveData.observe(this, Observer<List<CachedDynalistItem>> {
            adapter.updateItems(it)
        })
    }

    private fun editItem(item: DynalistItem) {
        val intent = Intent(context, AdvancedItemActivity::class.java)
        intent.putExtra(AdvancedItemActivity.EXTRA_EDIT_ITEM, item as Parcelable)
        val activity = activity as AppCompatActivity
        val transition = ActivityOptions.makeSceneTransitionAnimation(activity,
                UtilPair.create(activity.toolbar as View, "toolbar"),
                UtilPair.create(itemContents as View, "itemContents"))
        startActivity(intent, transition.toBundle())
    }

    private fun deleteItem(item: DynalistItem) {
        val boxStore = DynalistApp.instance.boxStore
        val box = boxStore.boxFor<DynalistItem>()
        boxStore.runInTxAsync({
            box.put(box.get(item.clientId).apply { hidden = true })
        }, null)
        Snackbar.make(view!!, R.string.delete_item_success, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.undo) {
                boxStore.runInTxAsync({
                    box.put(box.get(item.clientId).apply { hidden = false })
                }, null)
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        DynalistApp.instance.jobManager.addJobInBackground(DeleteItemJob(item))
                    }
                }
            })
            show()
        }
    }

    private fun showItemDetails(item: DynalistItem): Boolean {
        val intent = Intent(context, DetailsActivity::class.java)
        intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, item as Parcelable)
        val activity = activity as AppCompatActivity
        val transition = ActivityOptions.makeSceneTransitionAnimation(
                activity, activity.toolbar, "toolbar")
        startActivity(intent, transition.toBundle())
        return true
    }

    private fun alertRequireSync() {
        Snackbar.make(itemList, R.string.alert_sync_required, Snackbar.LENGTH_SHORT).apply {
            setAction(R.string.action_sync) {
                DynalistApp.instance.jobManager.run {
                    cancelJobsInBackground({
                        val job = SyncJob(false)
                        addJobInBackground(job)
                    }, TagConstraint.ALL, arrayOf(SyncJob.TAG))
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
            dynalist.addItem(itemContents.text.toString(), location)
            itemContents.text.clear()
        }
        updateSubmitEnabled()

        advancedItemButton.setOnClickListener {
            val intent = Intent(context, AdvancedItemActivity::class.java)
            intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, location as Parcelable)
            intent.putExtra(AdvancedItemActivity.EXTRA_ITEM_TEXT, itemContents.text)
            val activity = activity as AppCompatActivity
            val transition = ActivityOptions.makeSceneTransitionAnimation(activity,
                    UtilPair.create(activity.toolbar as View, "toolbar"),
                    UtilPair.create(itemContents as View, "itemContents"))
            startActivity(intent, transition.toBundle())
            itemContents.text.clear()
        }

        itemList.layoutManager = LinearLayoutManager(context)
        itemList.adapter = adapter
        ItemTouchHelper(ItemTouchCallback(adapter)).attachToRecyclerView(itemList)
    }

    override fun onStart() {
        super.onStart()
        activity!!.title = location.strippedMarkersName
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedDynalistItem.value = location
        itemContents.showSoftInputOnFocus = false
        itemContents.requestFocus()
        itemContents.showSoftInputOnFocus = true
    }

    private fun updateSubmitEnabled() {
        submitButton.isEnabled = itemContents.text.isNotEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (location.serverFileId != null && location.serverItemId != null) {
            inflater.inflate(R.menu.item_list_activity_menu, menu)
            menu.findItem(R.id.goto_parent).isVisible = !location.parent.isNull
            val shortcutsSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(context!!)
            menu.findItem(R.id.create_shortcut).isVisible = shortcutsSupported
            menu.findItem(R.id.toggle_show_checked_items).isChecked = location.areCheckedItemsVisible
            menu.findItem(R.id.toggle_checklist).isChecked = location.isChecklist
        }
        if (location.isInbox && !location.markedAsPrimaryInbox)
            inflater.inflate(R.menu.item_list_activity_primary_inbox_menu, menu)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.inbox_help -> showInboxHelp()
            R.id.open_in_dynalist -> openInDynalist()
            R.id.goto_parent -> openDynalistItem(location.parent.target)
            R.id.share -> shareDynalistItem()
            R.id.create_shortcut -> createShortcut()
            R.id.toggle_checklist -> toggleChecklist(item)
            R.id.toggle_show_checked_items -> toggleShowChecked(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleShowChecked(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        location.areCheckedItemsVisible = checked
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsParent.value = location
        doAsync {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
            DynalistApp.instance.boxStore.runInTx {
                box.get(location.clientId)?.apply {
                    areCheckedItemsVisible = checked
                    box.put(this)
                }
            }
        }
        return true
    }

    private fun toggleChecklist(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        adapter.showChecklist = checked
        location.isChecklist = checked
        doAsync {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
            DynalistApp.instance.boxStore.runInTx {
                box.get(location.clientId)?.apply {
                    isChecklist = checked
                    box.put(this)
                }
            }
        }
        return true
    }

    private fun createShortcut(): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context!!))
            return true
        val intent = Intent(context!!, ShortcutActivity::class.java)
        intent.putExtra(ShortcutActivity.EXTRA_LOCATION, location as Parcelable)
        val transition = ActivityOptions.makeSceneTransitionAnimation(activity,
                UtilPair.create(activity!!.toolbar as View, "toolbar"))
        startActivity(intent, transition.toBundle())
        return true
    }

    private fun shareDynalistItem(): Boolean {
        doAsync {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
            val location = box.get(location.clientId)
            val itemText = location.getSpannableText(context!!)
            val itemNotes = location.getSpannableNotes(context!!)
                    .prependIfNotBlank("\n\n")
            val itemChildren = location.getPlainChildren(context!!, 1)
                    .prependIfNotBlank("\n\n")
            val text = TextUtils.concat(itemText, itemNotes, itemChildren)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, location.name)
                type = "text/plain"
            }
            uiThread {
                startActivity(Intent.createChooser(sendIntent,
                        resources.getText(R.string.action_share)))
            }
        }
        return true
    }

    private fun openInDynalist(): Boolean {
        context!!.browse("https://dynalist.io/d/${location.serverFileId}#z=${location.serverItemId}")
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
        private const val ARG_LOCATION = "EXTRA_LOCATION"
        private const val ARG_ITEM_TEXT = "EXTRA_ITEM_TEXT"

        @JvmStatic
        fun newInstance(parent: DynalistItem, itemText: CharSequence) =
                ItemListFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable(ARG_LOCATION, parent)
                        putCharSequence(ARG_ITEM_TEXT, itemText)
                    }
                }
    }
}
