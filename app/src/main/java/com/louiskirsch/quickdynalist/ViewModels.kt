package com.louiskirsch.quickdynalist

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.louiskirsch.quickdynalist.adapters.CachedDynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
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

    val documentsLiveData: ObjectBoxLiveData<DynalistItem> by lazy {
        ObjectBoxLiveData(box.query {
            equal(DynalistItem_.serverItemId, "root")
            order(DynalistItem_.name)
        })
    }

    private var parent: DynalistItem? = null
    private lateinit var itemsLiveData: TransformedOBLiveData<DynalistItem, CachedDynalistItem>

    fun getItemsLiveData(parent: DynalistItem):
            TransformedOBLiveData<DynalistItem, CachedDynalistItem> {
        if(this.parent != parent) {
            this.parent = parent
            itemsLiveData = TransformedOBLiveData(box.query {
                equal(DynalistItem_.parentId, parent.clientId)
                and()
                notEqual(DynalistItem_.name, "")
                and()
                equal(DynalistItem_.isChecked, false)
                order(DynalistItem_.position)
                eager(20, DynalistItem_.children)
            }) { items ->
                items.forEach { item -> item.children.sortBy { child -> child.position } }
                items.map { CachedDynalistItem(it, getApplication()) }
            }
        }
        return itemsLiveData
    }
}

class ItemListFragmentViewModel: ViewModel() {
    val selectedDynalistItem = MutableLiveData<DynalistItem>()
}