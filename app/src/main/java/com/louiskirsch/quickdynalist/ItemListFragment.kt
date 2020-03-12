package com.louiskirsch.quickdynalist


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.jobs.MoveItemJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.ImageCache
import com.louiskirsch.quickdynalist.utils.prependIfNotBlank
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.objectbox.kotlin.boxFor
import kotlinx.android.synthetic.main.app_bar_navigation.*
import kotlinx.android.synthetic.main.fragment_item_list.*
import org.jetbrains.anko.*

class ItemListFragment : BaseItemListFragment() {
    private lateinit var location: DynalistItem

    override fun onCreate(savedInstanceState: Bundle?) {
        arguments!!.let { args ->
            location = args.getParcelable(ARG_LOCATION)!!
            DynalistApp.instance.boxStore.boxFor<DynalistItem>().attach(location)
            val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
            model.singleItem.value = location
            model.singleItemLiveData.observe(this, Observer { data ->
                if (data.isNotEmpty()) {
                    val newLocation = data.first()
                    val isModified = location.modified != newLocation.modified
                    if (isModified) {
                        location = newLocation
                        updateAppBar(refresh = true)
                        activity!!.invalidateOptionsMenu()
                    }
                } else {
                    // Item disappeared - close this fragment
                    fragmentManager?.popBackStack()
                }
            })
        }
        super.onCreate(savedInstanceState)
        adapter.onRowMovedIntoListener = { from, to ->
            DynalistApp.instance.jobManager.addJobInBackground(
                    MoveItemJob(from, to, Int.MIN_VALUE)
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
                                MoveItemJob(item, location.parent.target, Int.MIN_VALUE)
                        )
                        true
                    }
                }
                else -> false
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments!!.getCharSequence(ARG_ITEM_TEXT)?.let { insertBarFragment.setText(it) }
        val model = ViewModelProviders.of(activity!!).get(ItemListFragmentViewModel::class.java)
        model.selectedLocation.value = ItemLocation(location)
    }

    override fun updateAppBar(refresh: Boolean) {
        super.updateAppBar(refresh)
        val itemImage = activity!!.itemImage
        val appBar = activity!!.appBar
        val editItemFab = activity!!.editItemFab
        location.image?.also { image ->
            val picasso = Picasso.get()
            val imageCache = ImageCache(context!!)
            val request = imageCache.getFile(image)?.let { picasso.load(it) } ?: picasso.load(image)
            request.apply {
                into(itemImage, object: Callback {
                    override fun onError(e: Exception?) {}
                    override fun onSuccess() {
                        itemImage.visibility = View.VISIBLE
                        itemListCoordinator.isNestedScrollingEnabled = true
                    }
                })
                into(imageCache.getPutInCacheCallback(image))
            }
        }
        appBar.setOnClickListener {
            val image = location.image
            val notes = activity!!.itemNotes.text
            val title = activity!!.title
            if (image != null && notes.isBlank() && title.length < 20) {
                ImageCache(context!!).openInGallery(image)
            } else {
                showItemDetails(location)
            }
        }
        editItemFab.visibility = View.VISIBLE
        editItemFab.setOnClickListener {
            val intent = Intent(context, AdvancedItemActivity::class.java).apply {
                putExtra(AdvancedItemActivity.EXTRA_EDIT_ITEM, location as Parcelable)
            }
            startActivity(intent)
        }
    }

    override fun onDetach() {
        super.onDetach()
        activity!!.apply {
            editItemFab.setOnClickListener(null)
            appBar.setOnClickListener(null)
        }
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
        doAsync {
            DynalistItem.updateGlobally(location) { it.markedAsBookmark = checked }
            uiThread {
                context!!.toast(if (checked) R.string.bookmark_added else R.string.bookmark_removed)
            }
        }
        return true
    }

    private fun shareDynalistItem(): Boolean {
        doAsync {
            val location = DynalistItem.box.get(location.clientId)
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

    override fun activityTitle(context: Context): CharSequence {
        return location.getSpannableText(context)
    }

    override fun activityAppBarNotes(context: Context): CharSequence? {
        return location.getSpannableNotes(context)
    }

    companion object {
        private const val ARG_LOCATION = "EXTRA_LOCATION"
        private const val ARG_ITEM_TEXT = "EXTRA_ITEM_TEXT"

        @JvmStatic
        fun newInstance(parent: DynalistItem, itemText: CharSequence? = null,
                        scrollTo: DynalistItem? = null): ItemListFragment {
            return ItemListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LOCATION, parent)
                    itemText?.let { putCharSequence(ARG_ITEM_TEXT, it.toString()) }
                    scrollTo?.let { putParcelable(ARG_SCROLL_TO, it) }
                }
            }
        }
    }
}
