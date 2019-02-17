package com.louiskirsch.quickdynalist

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.objectbox.TransformedOBLiveData
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

    val recentItemsLiveData: ObjectBoxLiveData<DynalistItem> by lazy {
        ObjectBoxLiveData(box.query {
            notEqual(DynalistItem_.name, "")
            and()
            equal(DynalistItem_.hidden, false)
            and()
            equal(DynalistItem_.isChecked, false)
            orderDesc(DynalistItem_.lastModified)
            // TODO currently limiting number of items is not supported
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
}

class ItemListFragmentViewModel: ViewModel() {
    val selectedDynalistItem = MutableLiveData<DynalistItem>()
    val selectedDynalistFilter = MutableLiveData<DynalistItemFilter>()
}