package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemMetaData
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import io.objectbox.query.Query
import java.util.*


class CloneItemJob(val item: DynalistItem): ItemJob() {

    override fun addToDatabase() {
        val now = Date()
        DynalistApp.instance.boxStore.runInTx {
            box.get(item.clientId)?.let { item ->
                item.position = item.parent.target.children.size
                if (item.date != null)
                    item.date = Date()
                box.put(cloneRecursively(item, now).apply { syncJob = "$id-root" })
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, item)
    }

    private fun cloneRecursively(item: DynalistItem, time: Date): DynalistItem {
        val children = item.children.map {
            cloneRecursively(it, time).apply { it.parent.targetId = 0 }
        }
        item.clientId = 0
        item.serverItemId = null
        item.syncJob = id
        item.children.clear()
        item.children.addAll(children)
        item.metaData.targetId = 0
        item.notifyModified(time.time)
        return item
    }

    private fun insertRecursively(item: DynalistItem, token: String): List<DynalistItem> {
        if (item.children.isEmpty())
            return emptyList()
        val changes = item.children.mapIndexed { i: Int, it ->
            InsertItemRequest.InsertSpec(item.serverItemId!!, it.name, it.note, i)
        }.toTypedArray()
        val request = BulkInsertItemRequest(item.serverFileId!!, token, changes)
        val response = dynalistService.addToDocument(request).execute().body()!!
        requireSuccess(response)
        response.new_node_ids!!.zip(item.children).forEach { (newItemId, child) ->
            child.serverItemId = newItemId
        }
        return item.children + item.children.flatMap { insertRecursively(it, token) }
    }

    private fun waitForItem(query: Query<DynalistItem>, timeout: Long = 10000): DynalistItem? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() < start + timeout) {
            val item = query.findFirst()
            if (item != null) return item
            Thread.sleep(500)
        }
        return null
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val token = Dynalist(applicationContext).token
        // need to wait for db operations to complete
        val item = waitForItem(box.query { equal(DynalistItem_.syncJob, "$id-root") })
                ?: throw InvalidJobException("Item to clone has vanished")

        val request = InsertItemRequest(item.serverFileId!!, item.serverParentId!!, item.name,
                item.note, token!!)
        val response = dynalistService.addToDocument(request).execute().body()!!
        requireSuccess(response)
        item.serverItemId = response.new_node_ids!![0]
        val children = insertRecursively(item, token)
        box.put((children + item).apply { forEach { it.syncJob = null } })
    }

}
