package com.louiskirsch.quickdynalist


import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.*
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.louiskirsch.quickdynalist.utils.ImageCache
import com.louiskirsch.quickdynalist.utils.inputMethodManager
import com.louiskirsch.quickdynalist.utils.prependIfNotBlank
import com.louiskirsch.quickdynalist.utils.setupGrowingMultiline
import com.louiskirsch.quickdynalist.views.ScrollFABBehavior
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import com.louiskirsch.quickdynalist.widget.ListAppWidgetConfigurationReceiver
import io.objectbox.kotlin.boxFor
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import android.util.Pair as UtilPair

class ItemListFragment : Fragment() {

    private lateinit var dynalist: Dynalist
    private lateinit var location: DynalistItem
    private lateinit var adapter: ItemListAdapter

    private var editingItem: DynalistItem? = null
        set(value) {
            if (field != null && value == null) {
                itemContents.text.clear()
            }
            if (value != null) {
                itemContents.apply {
                    setText(value.name)
                    requestFocus()
                    setSelection(text.length)
                    val inputResultReceiver = object: ResultReceiver(Handler()) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            postDelayed({
                                val itemPosition = adapter.findPosition(value)
                                if (itemPosition >= 0) itemList.smoothScrollToPosition(itemPosition)
                            }, 500)
                        }
                    }
                    context!!.inputMethodManager.showSoftInput(this, 0,
                            inputResultReceiver)
                }
            }
            adapter.selectedItem = value
            field = value
        }

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
                R.id.action_edit -> editingItem = item
                R.id.action_show_image -> ImageCache(context!!)
                        .openInGallery(item.image!!, context!!)
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
        adapter.onMoveStartListener = { editingItem = null }

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsParent.value = location
        model.itemsLiveData.observe(this, Observer<List<CachedDynalistItem>> { newItems ->
            val initializing = adapter.itemCount == 0
            adapter.updateItems(newItems)
            if (initializing) scrollToIntendedLocation()
        })
    }

    private fun scrollToIntendedLocation() {
        arguments!!.getParcelable<DynalistItem>(ARG_SCROLL_TO)?.let { item: DynalistItem ->
            val index = adapter.findPosition(item)
            if (index >= 0) {
                itemList.scrollToPosition(index)
            }
        }
    }

    private fun deleteItem(item: DynalistItem) {
        if (item == editingItem)
            editingItem = null
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
            setAction(R.string.action_sync) { SyncJob.forceSync() }
            show()
        }
    }

    private fun openDynalistItem(item: DynalistItem): Boolean {
        val scrollTo = if (item == location.parent.target) location else null
        val fragment = newInstance(item, itemContents.text, scrollTo)
        fragmentManager!!.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        clearItemContents()
        return true
    }

    private fun clearItemContents() {
        itemContents.text.clear()
        editingItem = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_item_list, container, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("EDITING_ITEM", editingItem)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        itemContents.setText(arguments!!.getCharSequence(ARG_ITEM_TEXT))

        setupItemContentsTextField()
        editingItem = savedInstanceState?.getParcelable("EDITING_ITEM")

        if (itemContents.text.isNotBlank())
            itemContents.setSelection(itemContents.text.length)

        submitButton.setOnClickListener {
            val text = itemContents.text.toString()
            if (editingItem == null) {
                dynalist.addItem(text, location)
            } else if (editingItem!!.name != text) {
                editingItem!!.name = text
                DynalistApp.instance.jobManager.addJobInBackground(
                        EditItemJob(editingItem!!)
                )
            }
            clearItemContents()
        }
        updateSubmitEnabled()

        advancedItemButton.setOnClickListener {
            val intent = Intent(context, AdvancedItemActivity::class.java)
            if (editingItem != null) {
                editingItem!!.name = itemContents.text.toString()
                intent.putExtra(AdvancedItemActivity.EXTRA_EDIT_ITEM, editingItem as Parcelable)
            } else {
                intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, location as Parcelable)
                intent.putExtra(AdvancedItemActivity.EXTRA_ITEM_TEXT, itemContents.text)
            }
            val activity = activity as AppCompatActivity
            val transition = ActivityOptions.makeSceneTransitionAnimation(activity,
                    UtilPair.create(activity.toolbar as View, "toolbar"),
                    UtilPair.create(itemContents as View, "itemContents"))
            startActivity(intent, transition.toBundle())
            clearItemContents()
        }

        itemList.layoutManager = LinearLayoutManager(context)
        itemList.adapter = adapter
        ItemTouchHelper(ItemTouchCallback(adapter)).attachToRecyclerView(itemList)

        itemListScrollButton.setOnClickListener {
            itemList.smoothScrollToPosition(adapter.itemCount - 1)
            itemListScrollButton.hide(ScrollFABBehavior.hideListener)
        }
        scrollToIntendedLocation()
    }

    override fun onStart() {
        super.onStart()
        activity!!.title = location.strippedMarkersName
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedDynalistItem.value = location
    }

    private fun updateSubmitEnabled() {
        submitButton.isEnabled = itemContents.text.isNotEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (location.isInbox && !location.markedAsPrimaryInbox)
            inflater.inflate(R.menu.item_list_activity_primary_inbox_menu, menu)
        else {
            inflater.inflate(R.menu.item_list_activity_menu, menu)
            menu.findItem(R.id.goto_parent).isVisible = !location.parent.isNull
            val shortcutsSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(context!!)
            menu.findItem(R.id.create_shortcut).isVisible = shortcutsSupported
            menu.findItem(R.id.toggle_bookmark).isChecked = location.isBookmark
            menu.findItem(R.id.toggle_show_checked_items).isChecked = location.areCheckedItemsVisible
            menu.findItem(R.id.toggle_checklist).isChecked = location.isChecklist
            if (location.serverItemId == null) {
                menu.findItem(R.id.open_in_dynalist).isVisible = false
            }
        }
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
            R.id.create_widget -> createWidget()
            R.id.toggle_bookmark -> toggleBookmark(item)
            R.id.toggle_checklist -> toggleChecklist(item)
            R.id.toggle_show_checked_items -> toggleShowChecked(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleBookmark(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        location.markedAsBookmark = checked
        doAsync { DynalistItem.updateGlobally(location) { it.markedAsBookmark = checked }}
        return true
    }

    private fun toggleShowChecked(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        location.areCheckedItemsVisible = checked
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsParent.value = location
        doAsync { DynalistItem.updateLocally(location) { it.areCheckedItemsVisible = checked }}
        return true
    }

    private fun toggleChecklist(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        adapter.showChecklist = checked
        location.isChecklist = checked
        doAsync { DynalistItem.updateLocally(location) { it.isChecklist = checked }}
        return true
    }

    private fun createWidget(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val widgetManager = context!!.getSystemService(AppWidgetManager::class.java)
            if (widgetManager.isRequestPinAppWidgetSupported) {
                val widgetProvider = ComponentName(context!!, ListAppWidget::class.java)
                val callback = Intent(context!!,
                        ListAppWidgetConfigurationReceiver::class.java).apply {
                    putExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID, location.clientId)
                }
                val pendingCallback = PendingIntent.getBroadcast(
                        context!!, 0,callback, 0)
                widgetManager.requestPinAppWidget(widgetProvider, null, pendingCallback)
                return true
            }
        }
        context!!.longToast(R.string.error_create_widget_not_supported)
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
        private const val ARG_SCROLL_TO = "EXTRA_SCROLL_TO"
        private const val ARG_ITEM_TEXT = "EXTRA_ITEM_TEXT"

        @JvmStatic
        fun newInstance(parent: DynalistItem, itemText: CharSequence,
                        scrollTo: DynalistItem? = null): ItemListFragment {
            return ItemListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LOCATION, parent)
                    putCharSequence(ARG_ITEM_TEXT, itemText)
                    scrollTo?.let { putParcelable(ARG_SCROLL_TO, it) }
                }
            }
        }
    }
}
