package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import org.greenrobot.eventbus.EventBus


class AddItemJob(text: String, note: String, val parent: DynalistItem): ItemJob() {

    private val newItem = DynalistItem(parent.serverFileId, parent.serverItemId,
            null, text, note)

    override fun addToDatabase() {
        val dynalist = Dynalist(applicationContext)
        DynalistApp.instance.boxStore.runInTx {
            box.get(parent.clientId)?.also { parent ->
                newItem.syncJob = id
                newItem.position = if (dynalist.addToTopOfList) {
                    (minPosition(parent.clientId) ?: 1) - 1
                } else {
                    (maxPosition(parent.clientId) ?: -1) + 1
                }
                newItem.parent.target = parent
                newItem.isChecklist = parent.isChecklist
                newItem.checkbox = parent.isChecklist
                newItem.notifyModified()
                box.put(newItem)
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, newItem)
        EventBus.getDefault().post(ItemAddedEvent(newItem))
    }

    private fun insertAPIRequest(): DynalistResponse {
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        requireItemId(parent)
        val request = InsertItemRequest(parent.serverFileId!!, parent.serverItemId!!,
                newItem.name, newItem.note, parent.isChecklist, token!!,
                index = dynalist.insertPosition)
        return dynalistService.addToDocument(request).execute().body()!!
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val response = insertAPIRequest()
        requireSuccess(response)
        val newItemId = when (response) {
            is InboxItemResponse -> response.node_id
            is InsertedItemsResponse -> response.new_node_ids!![0]
            else -> throw BackendException("Invalid response - no itemId included")
        }
        DynalistApp.instance.boxStore.runInTx {
            val updatedItem = box.query {
                equal(DynalistItem_.syncJob, id, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            }.findFirst()?.apply {
                syncJob = null
                serverItemId = newItemId
            }
            updatedItem?.let { box.put(it) }
        }
    }

}
