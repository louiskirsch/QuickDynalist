package com.louiskirsch.quickdynalist

import android.app.Application
import androidx.lifecycle.*
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.*
import io.objectbox.Box
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query

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

    val itemsParent = MutableLiveData<DynalistItem>()
    val itemsLiveData: LiveData<List<CachedDynalistItem>> by lazy {
        Transformations.switchMap(itemsParent) { parent ->
            TransformedOBLiveData(box.query {
                equal(DynalistItem_.parentId, parent.clientId)
                and()
                notEqual(DynalistItem_.name, "")
                and()
                equal(DynalistItem_.hidden, false)
                if (!parent.areCheckedItemsVisible) {
                    and()
                    equal(DynalistItem_.isChecked, false)
                }
                order(DynalistItem_.position)
                eager(100, DynalistItem_.children)
            }) { items ->
                items.forEach { item -> item.children.sortBy { child -> child.position } }
                items.map { CachedDynalistItem(it, getApplication()) }
            }
        }
    }

    val itemsFilter = MutableLiveData<DynalistItemFilter>()
    val filteredItemsLiveData: LiveData<List<CachedDynalistItem>> by lazy {
        Transformations.switchMap(itemsFilter) { filter ->
            filter.transformedLiveData { items ->
                items.forEach { item -> item.children.sortBy { child -> child.position } }
                items.map { CachedDynalistItem(it, getApplication()) }
            }
        }
    }

    val searchTerm = MutableLiveData<String>()
    val searchItemsLiveData: LiveData<List<CachedDynalistItem>> by lazy {
        Transformations.switchMap(searchTerm) { search ->
            TransformedOBLiveData(box.query {
                if (search.isNotBlank()) {
                    contains(DynalistItem_.name, search)
                    or()
                    contains(DynalistItem_.note, search)
                }
                notEqual(DynalistItem_.name, "")
                equal(DynalistItem_.hidden, false)
                order(DynalistItem_.lastModified)
            }) { items ->
                val limitedItems = items.take(100)
                limitedItems.forEach { item -> item.children.sortBy { child -> child.position } }
                limitedItems.map { CachedDynalistItem(it, getApplication()) }
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
    val selectedDynalistObject = MutableLiveData<Location>()
}