package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import org.jetbrains.anko.collections.forEachWithIndex
import retrofit2.Response
import java.lang.IllegalArgumentException


class BulkEditItemJob(val items: List<DynalistItem>): ItemJob() {

    init {
        require(items.isNotEmpty()) { "Items to edit must not be empty" }
        require(items.all { it.serverFileId == items[0].serverFileId }) { "Items mut be in the same file" }
    }

    private val serverFileId = items[0].serverFileId!!

    override fun addToDatabase() {
        items.forEach { item ->
            item.syncJob = id
            box.attach(item)
            item.notifyModified()
        }
        box.put(items)
        val parents = items.mapNotNull { it.parent.target }.distinct()
        if (parents.size == 1)
            ListAppWidget.notifyItemChanged(applicationContext, parents[0])
        else
            items.forEach { ListAppWidget.notifyItemChanged(applicationContext, it) }
    }

    @Throws(Throwable::class)
    override fun onRun() {
        items.forEach { requireItemId(it) }
        val token = Dynalist(applicationContext).token
        val edits = items.map { item ->
            EditItemRequest.EditSpec(item.serverItemId!!, item.name,
                    item.note, item.isChecked, item.checkbox, item.heading, item.color)
        }.toTypedArray()
        val request = BulkEditItemRequest(serverFileId, token!!, edits)
        val response = dynalistService.editItems(request).execute()
        val body = response.body()!!
        requireSuccess(body)
        markItemsCompleted()
    }

}
