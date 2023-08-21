package com.louiskirsch.quickdynalist


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.boxFor
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import android.util.Pair as UtilPair

class FilteredItemListFragment : BaseItemListFragment() {
    private lateinit var filter: DynalistItemFilter
    private var saved: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            arguments!!.let {
                filter = it.getParcelable(ARG_FILTER)!!
                val box = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
                saved = filter.id > 0 && box.get(filter.id) != null
            }
        } else {
            filter = savedInstanceState.getParcelable("filter")!!
            saved = savedInstanceState.getBoolean("saved")
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("filter", filter)
        outState.putBoolean("saved", saved)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedLocation.value =
                FilterLocation(filter, context!!)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filter_item_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.toggle_saved).isChecked = saved
    }

    private fun ensureSaved() {
        if (saved) return
        saved = true
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
        box.put(filter)
        activity!!.invalidateOptionsMenu()
    }

    private fun toggleSaved(menuItem: MenuItem): Boolean {
        saved = !menuItem.isChecked
        menuItem.isChecked = saved
        doAsync {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
            if (saved)
                box.put(filter)
            else
                box.remove(filter)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit_filter -> editFilter()
            R.id.toggle_saved -> toggleSaved(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun editFilter(): Boolean {
        Intent(context!!, EditFilterActivity::class.java).apply {
            putExtra(DynalistApp.EXTRA_DISPLAY_FILTER, filter)
            val requestCode = resources.getInteger(R.integer.request_code_edit_filter)
            startActivityForResult(this, requestCode)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val editFilterRequest = resources.getInteger(R.integer.request_code_edit_filter)
        if (requestCode == editFilterRequest && resultCode == Activity.RESULT_OK) {
            filter = data!!.getParcelableExtra(DynalistApp.EXTRA_DISPLAY_FILTER)!!
            applyUpdatedFilter()
        }
    }

    override val showAsChecklist: Boolean get() = filter.showAsChecklist
    override val enableDragging: Boolean get() = false
    override val showItemParentText: Boolean get() = true
    override val hideIfNotEditing: Boolean get() = true

    override val itemsLiveData: LiveData<List<CachedDynalistItem>> get() {
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsFilter.value = filter
        return model.filteredItemsLiveData
    }

    private fun applyUpdatedFilter() {
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsFilter.value = filter
        adapter.showChecklist = filter.showAsChecklist
        adapter.updateItems(emptyList())
        itemListNoItems.visibility = View.GONE
        itemListProgress.show()
        activity!!.invalidateOptionsMenu()
        ListAppWidget.notifyFilterChanged(context!!, filter)
        doAsync {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
            if (saved) box.put(filter)
        }
    }

    override fun toggleChecklist(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        adapter.showChecklist = checked
        filter.showAsChecklist = checked

        applyUpdatedFilter()
        return true
    }

    override fun putWidgetExtras(intent: Intent) {
        ensureSaved()
        intent.putExtra(DynalistApp.EXTRA_DISPLAY_FILTER_ID, filter.id)
    }

    override fun putShortcutExtras(intent: Intent) {
        ensureSaved()
        intent.putExtra(ShortcutActivity.EXTRA_LOCATION, filter)
    }

    override fun activityTitle(context: Context): CharSequence {
        return filter.name ?: getString(R.string.filter_name_generic)
    }

    override fun activityAppBarNotes(context: Context): CharSequence? {
        // TODO generate description of filter
        return null
    }

    companion object {
        private const val ARG_FILTER = "EXTRA_FILTER"

        fun newInstance(filter: DynalistItemFilter,
                        scrollTo: DynalistItem? = null): FilteredItemListFragment {
            return FilteredItemListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FILTER, filter)
                    scrollTo?.let { putParcelable(ARG_SCROLL_TO, it) }
                }
            }
        }
    }
}
