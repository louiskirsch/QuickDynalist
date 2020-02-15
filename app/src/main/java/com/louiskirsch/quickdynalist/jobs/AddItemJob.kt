package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query


class AddItemJob(text: String, note: String, val parent: DynalistItem): ItemJob() {

    private val newItem = DynalistItem(parent.serverFileId, parent.serverItemId,
            null, text, note)

    override fun addToDatabase() {
        DynalistApp.instance.boxStore.runInTx {
            box.get(parent.clientId)?.also { parent ->
                newItem.syncJob = id
                newItem.position = parent.children.size
                newItem.parent.target = parent
                newItem.notifyModified()
                box.put(newItem)
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, newItem)
    }

    private fun insertAPIRequest(): DynalistResponse {
        val token = Dynalist(applicationContext).token
        requireItemId(parent)
        val request = InsertItemRequest(parent.serverFileId!!, parent.serverItemId!!,
                newItem.name, newItem.note, token!!)
        return dynalistService.addToDocument(request).execute().body()!!
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val response = insertAPIRequest()
        val newItemId = when (response) {
            is InboxItemResponse -> response.node_id
            is InsertedItemsResponse -> response.new_node_ids!![0]
            else -> throw BackendException("Invalid response - no itemId included")
        }
        requireSuccess(response)
        DynalistApp.instance.boxStore.runInTx {
            val updatedItem = box.query { equal(DynalistItem_.syncJob, id) }.findFirst()?.apply {
                syncJob = null
                serverItemId = newItemId
            }
            updatedItem?.let { box.put(it) }
        }
    }

}
