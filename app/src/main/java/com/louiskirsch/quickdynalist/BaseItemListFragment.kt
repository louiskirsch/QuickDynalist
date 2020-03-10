package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.*
import android.view.*
import android.widget.TextView
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.louiskirsch.quickdynalist.DynalistApp.Companion.MAIN_UI_FRAGMENT
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
import com.louiskirsch.quickdynalist.views.ScrollFABBehavior
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import com.louiskirsch.quickdynalist.widget.ListAppWidgetConfigurationReceiver
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import java.util.*

abstract class BaseItemListFragment :Fragment(),
        InsertBarFragment.InteractionListener, InsertBarFragment.LocationProvider {
    protected lateinit var dynalist: Dynalist
    protected lateinit var adapter: ItemListAdapter
    protected lateinit var insertBarFragment: InsertBarFragment

    protected abstract val showAsChecklist: Boolean
    protected abstract val showItemParentText: Boolean

    override fun onEditingItemChanged(view: TextView, editingItem: DynalistItem?) {
        adapter.selectedItem = editingItem
        if (editingItem != null) {
            val inputResultReceiver = object : ResultReceiver(Handler()) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    view.postDelayed({
                        val itemPosition = adapter.findPosition(editingItem)
                        if (itemPosition >= 0) itemList.smoothScrollToPosition(itemPosition)
                    }, 500)
                }
            }
            context!!.inputMethodManager.showSoftInput(view, 0, inputResultReceiver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynalist = Dynalist(context!!)
        setHasOptionsMenu(true)

        adapter = ItemListAdapter(context!!, showAsChecklist, showItemParentText).apply {
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
                R.id.action_edit -> insertBarFragment.editingItem = item
                R.id.action_show_image -> ImageCache(context!!).openInGallery(item.image!!)
                R.id.action_duplicate -> {
                    DynalistApp.instance.jobManager.addJobInBackground(CloneItemJob(item))
                }
                R.id.action_add_date_yesterday -> {
                    DynalistItem.updateGlobally(item) {
                        it.date = Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time
                    }
                }
                R.id.action_add_date_today -> {
                    DynalistItem.updateGlobally(item) { it.date = Date() }
                }
                R.id.action_add_date_tomorrow -> {
                    DynalistItem.updateGlobally(item) {
                        it.date = Calendar.getInstance().apply { add(Calendar.DATE, 1) }.time
                    }
                }
                R.id.action_add_date_mod -> {
                    DynalistItem.updateGlobally(item) { it.date = it.modified }
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
                insertBarFragment.editingItem = item
                false
            }
        }
        adapter.onCheckedStatusChangedListener = { item, checked ->
            item.isChecked = checked
            DynalistApp.instance.jobManager.addJobInBackground(EditItemJob(item))
        }
        adapter.onMoveStartListener = { insertBarFragment.editingItem = null }
        adapter.onColorSelected = { item, color ->
            DynalistItem.updateGlobally(item) { it.color = color }
        }

        itemsLiveData.observe(this, Observer<List<CachedDynalistItem>> { newItems ->
            val initializing = adapter.itemCount == 0
            adapter.updateItems(newItems)
             if (newItems.isEmpty()) {
                 activity!!.appBar.setExpanded(activity!!.itemNotes.text.isNotBlank(), true)
                 itemListNoItems.apply {
                     if (visibility != View.VISIBLE) {
                         visibility = View.VISIBLE
                         alpha = 0.0f
                         animate().alpha(1.0f).setStartDelay(200).setDuration(200).start()
                     }
                 }
             } else {
                 itemListNoItems.visibility = View.GONE
             }
            itemListProgress.hide()
            if (initializing) scrollToIntendedLocation()
        })
    }

    private fun moveItem(item: DynalistItem, targetLocation: DynalistItem) {
        if (item == targetLocation) {
            context!!.toast(R.string.error_move_same_item)
            return
        }
        DynalistApp.instance.jobManager.addJobInBackground(
                MoveItemJob(item, targetLocation, Int.MIN_VALUE)
        )
        Snackbar.make(itemListCoordinator, R.string.move_item_success,
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
                activity, activity!!.appBar, "toolbar").toBundle()

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
        if (item == insertBarFragment.editingItem)
            insertBarFragment.editingItem = null
        val boxStore = DynalistApp.instance.boxStore
        val box = boxStore.boxFor<DynalistItem>()
        boxStore.runInTxAsync({
            box.put(box.get(item.clientId).apply { hidden = true })
        }, null)
        Snackbar.make(itemListCoordinator, R.string.delete_item_success, Snackbar.LENGTH_LONG).apply {
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

    protected fun showItemDetails(item: DynalistItem): Boolean {
        val intent = Intent(context, DetailsActivity::class.java)
        intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM, item as Parcelable)
        startActivity(intent, transitionBundle)
        return true
    }

    private fun replaceMainUiFragment(fragment: Fragment) {
        fragmentManager?.beginTransaction()?.apply {
            fragmentManager?.findFragmentByTag(MAIN_UI_FRAGMENT)?.let { remove(it) }
            add(R.id.fragment_container, fragment, MAIN_UI_FRAGMENT)
            addToBackStack(null)
            commit()
        }
    }

    protected fun openDynalistItem(item: DynalistItem, scrollTo: DynalistItem? = null): Boolean {
        val scrollToResolved = scrollTo ?: resolveScrollTo(item)
        val fragment = ItemListFragment.newInstance(item, insertBarFragment.text, scrollToResolved)
        replaceMainUiFragment(fragment)
        return true
    }

    protected open fun resolveScrollTo(openItem: DynalistItem): DynalistItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_item_list, container, false)
    }

    override val addItemLocation: DynalistItem? get() = null
    protected open val hideIfNotEditing: Boolean get() = false
    protected abstract val enableDragging: Boolean

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        itemList.layoutManager = LinearLayoutManager(context)
        itemList.adapter = adapter
        if (adapter.itemCount == 0)
            itemListProgress.show()
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

    protected abstract fun activityTitle(context: Context): CharSequence
    protected abstract fun activityAppBarNotes(context: Context): CharSequence?

    protected open fun updateAppBar(refresh: Boolean = false) {
        val themedContext = ContextThemeWrapper(context, R.style.AppTheme_AppBarOverlay)
        val title = activityTitle(themedContext)
        val notes = activityAppBarNotes(themedContext) ?: ""
        itemListCoordinator.isNestedScrollingEnabled = !notes.isBlank()
        activity!!.apply {
            this.title = title
            collapsingToolbar.title = title
            itemNotes.text = notes
            if (!refresh || notes.isBlank()) {
                appBar.setExpanded(false, false)
            }
            editItemFab.visibility = View.GONE
            itemImage.visibility = View.GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        insertBarFragment = fragmentManager?.
                findFragmentById(R.id.insertBarFragment) as InsertBarFragment
        insertBarFragment.configure(this, hideIfNotEditing)
        insertBarFragment.registerListener(this)
        updateAppBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        insertBarFragment.configure(null, true)
        insertBarFragment.unregisterListener(this)
    }

    override fun onDetach() {
        super.onDetach()
        activity!!.apply {
            val title = getString(R.string.app_name)
            this.title = title
            collapsingToolbar.title = title
            itemNotes.text = ""
            appBar.setExpanded(false, false)
            editItemFab.visibility = View.GONE
            itemImage.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val shortcutsSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(context!!)
        menu.findItem(R.id.create_shortcut).isVisible = shortcutsSupported
        menu.findItem(R.id.toggle_checklist).isChecked = showAsChecklist
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