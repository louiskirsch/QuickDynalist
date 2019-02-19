package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.adapters.ItemListAdapter
import com.louiskirsch.quickdynalist.adapters.ItemTouchCallback
import com.louiskirsch.quickdynalist.adapters.SwipeBackgroundDrawer
import com.louiskirsch.quickdynalist.jobs.CloneItemJob
import com.louiskirsch.quickdynalist.jobs.DeleteItemJob
import com.louiskirsch.quickdynalist.jobs.EditItemJob
import com.louiskirsch.quickdynalist.jobs.MoveItemJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.utils.ImageCache
import com.louiskirsch.quickdynalist.utils.inputMethodManager
import com.louiskirsch.quickdynalist.utils.setupGrowingMultiline
import com.louiskirsch.quickdynalist.views.ScrollFABBehavior
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import com.louiskirsch.quickdynalist.widget.ListAppWidgetConfigurationReceiver
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import java.util.*
import android.util.Pair as UtilPair

abstract class BaseItemListFragment : Fragment() {
    protected lateinit var dynalist: Dynalist
    protected lateinit var adapter: ItemListAdapter

    private var editingItem: DynalistItem? = null
        set(value) {
            if (field != null && value == null) {
                onClearEditingItem()
            }
            if (value != null) {
                onSetEditingItem(value)
            }
            adapter.selectedItem = value
            field = value
        }

    protected abstract val showAsChecklist: Boolean
    protected open fun onClearEditingItem() {
        itemContents.text.clear()
    }
    protected open fun onSetEditingItem(value: DynalistItem) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynalist = Dynalist(context!!)
        setHasOptionsMenu(true)

        adapter = ItemListAdapter(showAsChecklist).apply {
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
                R.id.action_show_image -> ImageCache(context!!).openInGallery(item.image!!)
                R.id.action_duplicate -> {
                    DynalistApp.instance.jobManager.addJobInBackground(CloneItemJob(item))
                }
                R.id.action_add_date_today -> {
                    DynalistItem.updateGlobally(item) { it.date = Date() }
                }
                R.id.action_add_date_mod -> {
                    DynalistItem.updateGlobally(item) { it.date = Date(it.lastModified) }
                }
                R.id.action_add_date_choose -> chooseItemDate(item)
                R.id.action_change_date_remove -> {
                    DynalistItem.updateGlobally(item) { it.date = null }
                }
                R.id.action_move_to_bookmark -> {
                    fillMenuBookmarks(menuItem.subMenu) { targetLocation ->
                        moveItem(item, targetLocation)
                        true
                    }
                }
                R.id.action_move_to -> {
                    val requestCode = resources.getInteger(R.integer.request_code_move)
                    showItemPicker(requestCode, item)
                }
                R.id.action_link_to -> {
                    val requestCode = resources.getInteger(R.integer.request_code_link)
                    showItemPicker(requestCode, item)
                }
            }
            true
        }
        adapter.onRowSwipedListener = { item, direction ->
            if (direction == ItemTouchHelper.RIGHT) {
                deleteItem(item)
                true
            } else {
                editingItem = item
                false
            }
        }
        adapter.onCheckedStatusChangedListener = { item, checked ->
            item.isChecked = checked
            DynalistApp.instance.jobManager.addJobInBackground(EditItemJob(item))
        }
        adapter.onMoveStartListener = { editingItem = null }

        itemsLiveData.observe(this, Observer<List<CachedDynalistItem>> { newItems ->
            val initializing = adapter.itemCount == 0
            adapter.updateItems(newItems)
            if (initializing) scrollToIntendedLocation()
        })
    }

    private fun moveItem(item: DynalistItem, targetLocation: DynalistItem) {
        if (item == targetLocation) {
            context!!.toast(R.string.error_move_same_item)
            return
        }
        DynalistApp.instance.jobManager.addJobInBackground(
                MoveItemJob(item, targetLocation, -1)
        )
        Snackbar.make(insertBarCoordinator, R.string.move_item_success,
                Snackbar.LENGTH_LONG).apply {
            setAction(R.string.goto_move_location) {
                openDynalistItem(targetLocation, item)
            }
            show()
        }
    }

    private fun showItemPicker(requestCode: Int, payloadItem: DynalistItem) {
        Intent(context!!, SearchActivity::class.java).apply {
            putExtra("payload", Bundle().apply {
                putParcelable(DynalistApp.EXTRA_DISPLAY_ITEM, payloadItem)
            })
            startActivityForResult(this, requestCode, transitionBundle)
        }
    }

    private val transitionBundle: Bundle
        get() = ActivityOptions.makeSceneTransitionAnimation(
                activity, activity!!.toolbar, "toolbar").toBundle()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                resources.getInteger(R.integer.request_code_move) -> {
                    val item = data!!.getBundleExtra("payload")
                            .getParcelable<DynalistItem>(DynalistApp.EXTRA_DISPLAY_ITEM)!!
                    val targetLocation = data.getParcelableExtra<DynalistItem>(
                            DynalistApp.EXTRA_DISPLAY_ITEM)!!
                    moveItem(item, targetLocation)
                }
                resources.getInteger(R.integer.request_code_link) -> {
                    val fromItem = data!!.getBundleExtra("payload")
                            .getParcelable<DynalistItem>(DynalistApp.EXTRA_DISPLAY_ITEM)!!
                    val linkTarget = data.getParcelableExtra<DynalistItem>(
                            DynalistApp.EXTRA_DISPLAY_ITEM)!!
                    if (linkTarget.serverFileId != null && linkTarget.serverItemId != null) {
                        DynalistItem.updateGlobally(fromItem) { it.linkedItem = linkTarget }
                    } else {
                        context!!.toast(R.string.error_link_offline)
                    }
                }
            }
        }
    }

    protected abstract val itemsLiveData: LiveData<List<CachedDynalistItem>>

    private fun fillMenuBookmarks(menu: SubMenu, onClick: (DynalistItem) -> Boolean) {
        // TODO query this async
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
        val bookmarks = box.query {
            equal(DynalistItem_.isBookmark, true)
            orderDesc(DynalistItem_.isInbox)
            order(DynalistItem_.name)
        }.find()
        menu.clear()
        bookmarks.forEachIndexed { idx, item ->
            menu.add(SubMenu.NONE, SubMenu.NONE, idx, item.strippedMarkersName).apply {
                setOnMenuItemClickListener { onClick(item) }
            }
        }
    }

    private fun chooseItemDate(item: DynalistItem) {
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(context!!, { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            DynalistItem.updateGlobally(item) { it.date = calendar.time }
        }, calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
        dialog.show()
    }

    private fun scrollToIntendedLocation() {
        arguments!!.getParcelable<DynalistItem>(ARG_SCROLL_TO)?.let { item: DynalistItem ->
            val index = adapter.findPosition(item)
            if (index >= 0) {
                itemList.scrollToPosition(index)
                adapter.highlightPosition(index)
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
        Snackbar.make(insertBarCoordinator, R.string.delete_item_success, Snackbar.LENGTH_LONG).apply {
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
        startActivity(intent, transitionBundle)
        return true
    }

    protected fun openDynalistItem(item: DynalistItem, scrollTo: DynalistItem? = null): Boolean {
        val scrollToResolved = scrollTo ?: resolveScrollTo(item)
        val fragment = ItemListFragment.newInstance(item, itemContents.text, scrollToResolved)
        fragmentManager!!.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        clearItemContents()
        return true
    }

    protected open fun resolveScrollTo(openItem: DynalistItem): DynalistItem? = null

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

    protected open val addItemLocation: DynalistItem? get() = null
    protected abstract val enableDragging: Boolean

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupItemContentsTextField()
        editingItem = savedInstanceState?.getParcelable("EDITING_ITEM")

        if (itemContents.text.isNotBlank())
            itemContents.setSelection(itemContents.text.length)

        submitButton.setOnClickListener {
            val text = itemContents.text.toString()
            if (editingItem == null) {
                dynalist.addItem(text, addItemLocation!!)
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
                intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, addItemLocation as Parcelable)
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
        val swipeBackgroundDrawer = SwipeBackgroundDrawer(context!!, R.drawable.ic_swipe_edit,
                R.drawable.ic_swipe_delete, R.color.editColor, R.color.deleteColor)
        ItemTouchHelper(ItemTouchCallback(adapter, enableDragging, swipeBackgroundDrawer))
                .attachToRecyclerView(itemList)

        itemListScrollButton.setOnClickListener {
            itemList.scrollToPosition(adapter.itemCount - 1)
            itemListScrollButton.hide(ScrollFABBehavior.hideListener)
        }
        scrollToIntendedLocation()
    }

    protected abstract val activityTitle: String

    override fun onStart() {
        super.onStart()
        activity!!.title = activityTitle
    }

    private fun updateSubmitEnabled() {
        submitButton.isEnabled = itemContents.text.isNotEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val shortcutsSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(context!!)
        menu.findItem(R.id.create_shortcut).isVisible = shortcutsSupported
        menu.findItem(R.id.toggle_checklist).isChecked = showAsChecklist
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
            R.id.create_shortcut -> createShortcut()
            R.id.create_widget -> createWidget()
            R.id.toggle_checklist -> toggleChecklist(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    protected abstract fun toggleChecklist(menuItem: MenuItem): Boolean
    protected abstract fun putWidgetExtras(intent: Intent)
    protected abstract fun putShortcutExtras(intent: Intent)

    private fun createWidget(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val widgetManager = context!!.getSystemService(AppWidgetManager::class.java)
            if (widgetManager.isRequestPinAppWidgetSupported) {
                val widgetProvider = ComponentName(context!!, ListAppWidget::class.java)
                val callback = Intent(context!!, ListAppWidgetConfigurationReceiver::class.java)
                        .also { putWidgetExtras(it) }
                val requestCode = resources.getInteger(R.integer.request_code_create_widget)
                val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT
                val pendingCallback = PendingIntent.getBroadcast(
                        context!!, requestCode, callback, flags)
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
        putShortcutExtras(intent)
        startActivity(intent, transitionBundle)
        return true
    }

    companion object {
        const val ARG_SCROLL_TO = "EXTRA_SCROLL_TO"
    }
}