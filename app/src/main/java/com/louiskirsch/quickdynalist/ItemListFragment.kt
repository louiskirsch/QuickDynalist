package com.louiskirsch.quickdynalist


import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.jobs.CloneItemJob
import com.louiskirsch.quickdynalist.jobs.MoveItemJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.prependIfNotBlank
import io.objectbox.kotlin.boxFor
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*
import android.util.Pair as UtilPair

class ItemListFragment : BaseItemListFragment() {
    private lateinit var location: DynalistItem

    override fun onCreate(savedInstanceState: Bundle?) {
        arguments!!.let {
            location = it.getParcelable(ItemListFragment.ARG_LOCATION)!!
            DynalistApp.instance.boxStore.boxFor<DynalistItem>().attach(location)
        }
        super.onCreate(savedInstanceState)
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
                else -> false
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        itemContents.setText(arguments!!.getCharSequence(ARG_ITEM_TEXT))
        super.onActivityCreated(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedLocation.value = ItemLocation(location)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.item_list_activity_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.goto_parent).isVisible = !location.parent.isNull
        menu.findItem(R.id.toggle_bookmark).isChecked = location.isBookmark
        menu.findItem(R.id.toggle_show_checked_items).isChecked = location.areCheckedItemsVisible
        if (location.serverItemId == null) {
            menu.findItem(R.id.open_in_dynalist).isVisible = false
        }
    }

    private fun toggleBookmark(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        location.markedAsBookmark = checked
        doAsync { DynalistItem.updateGlobally(location) { it.markedAsBookmark = checked }}
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.open_in_dynalist -> openInDynalist()
            R.id.goto_parent -> openDynalistItem(location.parent.target)
            R.id.share -> shareDynalistItem()
            R.id.toggle_bookmark -> toggleBookmark(item)
            R.id.toggle_show_checked_items -> toggleShowChecked(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override val showAsChecklist: Boolean get() = location.isChecklist
    override val showItemParentText: Boolean get() = false
    override val enableDragging: Boolean get() = true

    override val itemsLiveData: LiveData<List<CachedDynalistItem>> get() {
        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.itemsParent.value = location
        return model.itemsLiveData
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

    override fun toggleChecklist(menuItem: MenuItem): Boolean {
        val checked = !menuItem.isChecked
        menuItem.isChecked = checked
        adapter.showChecklist = checked
        location.isChecklist = checked
        doAsync { DynalistItem.updateGlobally(location) { it.isChecklist = checked }}
        return true
    }

    private fun openInDynalist(): Boolean {
        context!!.browse("https://dynalist.io/d/${location.serverFileId}#z=${location.serverItemId}")
        return true
    }

    override fun resolveScrollTo(openItem: DynalistItem): DynalistItem? {
        return if (openItem == location.parent.target) location else null
    }

    override fun putWidgetExtras(intent: Intent) {
        intent.putExtra(DynalistApp.EXTRA_DISPLAY_ITEM_ID, location.clientId)
    }

    override fun putShortcutExtras(intent: Intent) {
        intent.putExtra(ShortcutActivity.EXTRA_LOCATION, location as Parcelable)
    }

    override val addItemLocation: DynalistItem? get() = location
    override val activityTitle: String get() = location.strippedMarkersName

    companion object {
        private const val ARG_LOCATION = "EXTRA_LOCATION"
        private const val ARG_ITEM_TEXT = "EXTRA_ITEM_TEXT"

        @JvmStatic
        fun newInstance(parent: DynalistItem, itemText: CharSequence,
                        scrollTo: DynalistItem? = null): ItemListFragment {
            return ItemListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LOCATION, parent)
                    putCharSequence(ARG_ITEM_TEXT, itemText)
                    scrollTo?.let { putParcelable(BaseItemListFragment.ARG_SCROLL_TO, it) }
                }
            }
        }
    }
}
