package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.DynalistResponse
import com.louiskirsch.quickdynalist.network.InboxRequest
import com.louiskirsch.quickdynalist.network.InsertItemRequest
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.kotlin.query
import retrofit2.Response


class AddItemJob(text: String, note: String, val parent: DynalistItem): ItemJob() {

    private val newItem = DynalistItem(parent.serverFileId, parent.serverItemId,
            null, text, note)

    override fun addToDatabase() {
        DynalistApp.instance.boxStore.runInTx {
            box.get(parent.clientId)?.let { parent ->
                newItem.syncJob = id
                newItem.position = parent.children.size
                newItem.parent.target = parent
                box.put(newItem)
            }
        }
    }

    private fun insertAPIRequest(): Response<DynalistResponse> {
        val token = Dynalist(applicationContext).token
        return if (parent.isInbox) {
            dynalistService.addToInbox(InboxRequest(newItem.name, newItem.note, token!!)).execute()
        } else {
            requireItemId(parent)
            val request = InsertItemRequest(parent.serverFileId!!, parent.serverItemId!!,
                    newItem.name, newItem.note, token!!)
            val call = dynalistService.addToDocument(request)
            call.execute()
        }
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val response = insertAPIRequest()
        val body = response.body()!!
        requireSuccess(body)
        markItemsCompleted()
    }

}
