package com.louiskirsch.quickdynalist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.objectbox.Box
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.inValues
import io.objectbox.kotlin.query

class DynalistItemViewModel: ViewModel() {

    private val box: Box<DynalistItem>
        get() = DynalistApp.instance.boxStore.boxFor()

    val bookmarksLiveData: ObjectBoxLiveData<DynalistItem> by lazy {
        ObjectBoxLiveData(box.query {
            equal(DynalistItem_.isBookmark, true)
            orderDesc(DynalistItem_.isInbox)
            order(DynalistItem_.name)
        })
    }

    private var parent: DynalistItem? = null
    private lateinit var itemsLiveData: ObjectBoxLiveData<DynalistItem>

    fun getItemsLiveData(parent: DynalistItem): ObjectBoxLiveData<DynalistItem> {
        if(this.parent != parent) {
            this.parent = parent
            itemsLiveData = ObjectBoxLiveData(box.query {
                equal(DynalistItem_.parentId, parent.clientId)
                order(DynalistItem_.position)
                eager(20, DynalistItem_.children)
            })
        }
        return itemsLiveData
    }
}

class ItemListFragmentViewModel: ViewModel() {
    val selectedBookmark = MutableLiveData<DynalistItem>()
}