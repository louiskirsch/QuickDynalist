package com.louiskirsch.quickdynalist.network

import com.louiskirsch.quickdynalist.Dynalist
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.RateLimitDelay
import com.louiskirsch.quickdynalist.jobs.ItemJob
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.execRespectRateLimit
import org.greenrobot.eventbus.EventBus

class AddItemTreeService(private val itemJob: ItemJob) {

    private val dynalist = Dynalist(itemJob.applicationContext)
    private val token = dynalist.token!!

    private val dynalistService
        get() = DynalistApp.instance.dynalistService

    private val delayCallback = { _: Any, delay: Long ->
        EventBus.getDefault().post(RateLimitDelay(delay, ItemJob.TAG))
    }

    private fun insertRecursively(item: DynalistItem): List<DynalistItem> {
        if (item.children.isEmpty())
            return emptyList()
        // TODO API has weird bug that items are inserted in reverse order
        val children = item.children.sortedBy { it.position }.reversed()
        val changes = children.mapIndexed { i: Int, it ->
            // TODO we could define the index here, but the API is bugged
            InsertItemRequest.InsertSpec(item.serverItemId!!, it.name, it.note, it.checkbox,
                    it.color)
        }.toTypedArray()
        val request = BulkInsertItemRequest(item.serverFileId!!, token, changes)
        val response = dynalistService.addToDocument(request)
                .execRespectRateLimit(delayCallback).body()!!
        itemJob.requireSuccess(response)
        response.new_node_ids!!.zip(children).forEach { (newItemId, child) ->
            child.serverItemId = newItemId
        }
        return children + children.flatMap { insertRecursively(it) }
    }

    fun insert(item: DynalistItem): List<DynalistItem> {
        val request = InsertItemRequest(item.serverFileId!!, item.serverParentId!!, item.name,
                item.note, item.checkbox, token, item.color, dynalist.insertPosition)
        val response = dynalistService.addToDocument(request)
                .execRespectRateLimit(delayCallback).body()!!
        itemJob.requireSuccess(response)
        item.serverItemId = response.new_node_ids!![0]
        val children = insertRecursively(item)
        return children + item
    }
}