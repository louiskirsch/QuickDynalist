package com.louiskirsch.quickdynalist

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.*
import io.objectbox.Box
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.OrderFlags
import org.jetbrains.anko.doAsync

class DynalistItemViewModel(app: Application): AndroidViewModel(app) {

    private val box: Box<DynalistItem>
        get() = DynalistApp.instance.boxStore.boxFor()

    val bookmarksLiveData: ObjectBoxLiveData<DynalistItem> by lazy {
        ObjectBoxLiveData(box.query {
            equal(DynalistItem_.isBookmark, true)
            orderDesc(DynalistItem_.isInbox)
            order(DynalistItem_.name)
        })
    }

    val documentsLiveData: ObjectBoxLiveData<DynalistItem> by lazy {
        ObjectBoxLiveData(box.query {
            equal(DynalistItem_.serverItemId, "root")
            order(DynalistItem_.name)
        })
    }

    val bookmarksAndDocsLiveData: ObjectBoxLiveData<DynalistItem> by lazy {
        ObjectBoxLiveData(box.query {
            equal(DynalistItem_.isBookmark, true)
            or()
            equal(DynalistItem_.serverItemId, "root")
            orderDesc(DynalistItem_.isInbox)
            orderDesc(DynalistItem_.isBookmark)
            order(DynalistItem_.name)
        })
    }

    val recentLocationsLiveData: LiveData<List<DynalistItem>> by lazy {
        val recentItemsQuery = box.query { orderDesc(DynalistItem_.modified) }
        SubscriberOBLiveData(DynalistApp.instance.boxStore, DynalistItem::class.java,
                recentItemsQuery) { query ->
            query.find(0, 20).mapNotNull { it.parent.target }.distinct().take(5)
        }
    }

    val targetLocationsLiveData: LiveData<List<DynalistItem>> by lazy {
        MediatorLiveData<List<DynalistItem>>().apply {
            addSource(bookmarksAndDocsLiveData) { bookmarks ->
                value = recentLocationsLiveData.value?.let { (it + bookmarks).distinct() }
                        ?: bookmarks
            }
            addSource(recentLocationsLiveData) { recentLocations ->
                value = bookmarksAndDocsLiveData.value?.let { (recentLocations + it).distinct() }
                        ?: recentLocations
            }
        }
    }

    class GroupedTargetLocations(var recent: List<DynalistItem>?,
                                 var bookmarks: List<DynalistItem>?,
                                 var documents: List<DynalistItem>?)
    val groupedTargetLocationsLiveData: LiveData<GroupedTargetLocations> by lazy {
        MediatorLiveData<GroupedTargetLocations>().apply {
            value = GroupedTargetLocations(null, null, null)
            addSource(recentLocationsLiveData) {
                value = value!!.apply { recent = it }
            }
            addSource(bookmarksLiveData) {
                value = value!!.apply { bookmarks = it }
            }
            addSource(documentsLiveData) {
                value = value!!.apply { documents = it }
            }
        }
    }

    val singleItem = MutableLiveData<DynalistItem>()
    val singleItemLiveData: LiveData<List<DynalistItem>> by lazy {
        Transformations.switchMap(singleItem) { item ->
            ObjectBoxLiveData(box.query {
                equal(DynalistItem_.clientId, item.clientId)
            })
        }
    }

    val itemsParent = MutableLiveData<DynalistItem>()
    val itemsLiveData: LiveData<List<CachedDynalistItem>> by lazy {
        Transformations.switchMap(itemsParent) { parent ->
            val dynalist = Dynalist(getApplication())
            TransformedOBLiveData(box.query {
                equal(DynalistItem_.parentId, parent.clientId)
                if (dynalist.displayLinksInline) {
                    // Forward links
                    if (!parent.metaLinkedItem.isNull) {
                        or().equal(DynalistItem_.parentId, parent.metaLinkedItem.targetId)
                    }
                    // Backward links
                    or().equal(DynalistItem_.metaLinkedItemId, parent.clientId)
                }

                notEqual(DynalistItem_.name, "")
                equal(DynalistItem_.hidden, false)
                if (!parent.areCheckedItemsVisible) {
                    equal(DynalistItem_.isChecked, false)
                }

                // Ordering doesn't make much sense with inline links, we do it manually later
                if (!dynalist.displayLinksInline)
                    order(DynalistItem_.position)
                eager(DynalistItem_.children)
            }) { list ->
                val transformed = if (dynalist.displayLinksInline) {
                    val backwardLink = DynalistItem.LinkingChildType.BACKWARD_LINK
                    list.mapNotNull {
                        if (it.getLinkingChildType(parent) == backwardLink)
                            it.parent.target
                        else
                            it
                    }.distinct()
                    .sortedWith(compareBy({ it.getLinkingChildType(parent) }, { it.position }))
                } else {
                    list
                }
                createCachedDynalistItems(transformed, false, parent)
            }
        }
    }

    val itemsFilter = MutableLiveData<DynalistItemFilter>()
    val filteredItemsLiveData: LiveData<List<CachedDynalistItem>> by lazy {
        Transformations.switchMap(itemsFilter) { filter ->
            filter.transformedLiveData { createCachedDynalistItems(it, true) }
        }
    }

    private fun createCachedDynalistItems(items: List<DynalistItem>, includeParent: Boolean,
                                          displayParent: DynalistItem? = null): List<CachedDynalistItem> {
        val context: Context = getApplication()
        val dynalist = Dynalist(context)
        val maxChildren = dynalist.displayChildrenCount
        val maxDepth = dynalist.displayChildrenDepth
        val linksInline = dynalist.displayLinksInline
        val newDisplayParent = if (linksInline) displayParent else null
        items.forEach { item -> item.children.sortBy { child -> child.position } }
        return items.map {
            CachedDynalistItem(it, context, maxChildren, linksInline, maxDepth, newDisplayParent)
        }.apply {
            // The first few visible items should be eagerly initialized
            take(50).forEach { it.eagerInitialize(includeParent) }
            doAsync { forEach { it.eagerInitialize(includeParent) } }
        }
    }

    val searchTerm = MutableLiveData<String>()
    val searchItemsLiveData: LiveData<List<CachedDynalistItem>> by lazy {
        Transformations.switchMap(searchTerm) { search ->
            val query = box.query {
                if (search.isNotBlank()) {
                    contains(DynalistItem_.name, search)
                    or()
                    contains(DynalistItem_.note, search)
                }
                notEqual(DynalistItem_.name, "")
                equal(DynalistItem_.hidden, false)
                orderDesc(DynalistItem_.modified)
                eager(DynalistItem_.children, DynalistItem_.parent)
            }
            SubscriberOBLiveData(DynalistApp.instance.boxStore, DynalistItem::class.java, query) {
                createCachedDynalistItems(it.find(0, 100), true)
            }
        }
    }

    private val filterBox: Box<DynalistItemFilter>
        get() = DynalistApp.instance.boxStore.boxFor()

    private val filtersLiveData: ObjectBoxLiveData<DynalistItemFilter> by lazy {
        ObjectBoxLiveData(filterBox.query { })
    }

    val locationsLiveData: LiveData<List<Location>> by lazy {
        val bookmarks = Transformations.map(bookmarksLiveData) {
            it.map { bookmark -> ItemLocation(bookmark) }
        }
        val filters = Transformations.map(filtersLiveData) {
            it.map { filter -> FilterLocation(filter, getApplication()) }
        }
        MediatorLiveData<List<Location>>().apply {
            addSource(bookmarks) { bookmarks ->
                value = filters.value?.let { bookmarks + it } ?: bookmarks }
            addSource(filters) { filters ->
                value = bookmarks.value?.let { it + filters } ?: filters }
        }
    }
}

class DynalistItemFilterViewModel(app: Application): AndroidViewModel(app) {

    private val box: Box<DynalistItemFilter>
        get() = DynalistApp.instance.boxStore.boxFor()

    val filtersLiveData: ObjectBoxLiveData<DynalistItemFilter> by lazy {
        ObjectBoxLiveData(box.query { })
    }
}

class DynalistTagViewModel(app: Application): AndroidViewModel(app) {

    private val box: Box<DynalistTag>
        get() = DynalistApp.instance.boxStore.boxFor()

    val tagsLiveData: ObjectBoxLiveData<DynalistTag> by lazy {
        ObjectBoxLiveData(box.query {
            order(DynalistTag_.type)
            order(DynalistTag_.name)
        })
    }
}

class ItemListFragmentViewModel: ViewModel() {
    val selectedLocation = MutableLiveData<Location>()
}