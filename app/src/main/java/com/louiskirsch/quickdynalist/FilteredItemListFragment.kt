package com.louiskirsch.quickdynalist


import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
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

    override fun onStart() {
        super.onStart()
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedDynalistFilter.value = filter
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filter_item_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.toggle_saved).isChecked = saved
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
        // TODO open filter activity and listen for result
        return true
    }

    override val showAsChecklist: Boolean get() = filter.showAsChecklist
    override val enableDragging: Boolean get() = false

    override val itemsLiveData: LiveData<List<CachedDynalistItem>> get() {
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsFilter.value = filter
        return model.filteredItemsLiveData
    }

    private fun saveIfNeeded() {
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
        saveIfNeeded()
        return true
    }

    override fun putWidgetExtras(intent: Intent) {
        // TODO implement widget
    }

    override fun putShortcutExtras(intent: Intent) {
        // TODO implement shortcut
    }

    override fun onSetEditingItem(value: DynalistItem) {
        insertBar.visibility = View.VISIBLE
        super.onSetEditingItem(value)
    }

    override fun onClearEditingItem() {
        super.onClearEditingItem()
        insertBar.visibility = View.GONE
    }

    override val activityTitle: String
        get() = filter.name ?: getString(R.string.filter_name_generic)

    companion object {
        private const val ARG_FILTER = "EXTRA_FILTER"

        fun newInstance(filter: DynalistItemFilter,
                        scrollTo: DynalistItem? = null): FilteredItemListFragment {
            return FilteredItemListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FILTER, filter)
                    scrollTo?.let { putParcelable(BaseItemListFragment.ARG_SCROLL_TO, it) }
                }
            }
        }
    }
}
