package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import org.jetbrains.anko.collections.forEachWithIndex
import retrofit2.Response


class EditItemJob(val item: DynalistItem): ItemJob() {

    override fun addToDatabase() {
        item.syncJob = id
        box.attach(item)
        item.notifyModified()
        box.put(item)
        metaBox.put(item.metaData.target)
        ListAppWidget.notifyItemChanged(applicationContext, item)
    }

    @Throws(Throwable::class)
    override fun onRun() {
        requireItemId(item)
        val token = Dynalist(applicationContext).token
        val request = EditItemRequest(item.serverFileId!!, item.serverItemId!!, item.name,
                item.note, item.isChecked, token!!)
        val response = dynalistService.editItem(request).execute()
        val body = response.body()!!
        requireSuccess(body)
        markItemsCompleted()
    }

}
