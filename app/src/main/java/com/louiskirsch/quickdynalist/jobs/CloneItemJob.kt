package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.kotlin.query
import org.jetbrains.anko.collections.forEachWithIndex
import retrofit2.Response


class CloneItemJob(val item: DynalistItem): ItemJob() {

    override fun addToDatabase() {
        DynalistApp.instance.boxStore.runInTx {
            box.get(item.clientId)?.let { item ->
                item.position = item.parent.target.children.size
                box.put(cloneRecursively(item).apply { syncJob = "$id-root" })
            }
        }
    }

    private fun cloneRecursively(item: DynalistItem): DynalistItem {
        val children = item.children.map { cloneRecursively(it).apply { it.parent.targetId = 0 } }
        item.clientId = 0
        item.serverItemId = null
        item.syncJob = id
        item.children.clear()
        item.children.addAll(children)
        return item
    }

    private fun insertRecursively(item: DynalistItem, token: String) {
        if (item.children.isEmpty())
            return
        val changes = item.children.mapIndexed { i: Int, it ->
            InsertItemRequest.InsertSpec(item.serverItemId!!, it.name, it.note, i)
        }.toTypedArray()
        val request = BulkInsertItemRequest(item.serverFileId!!, token, changes)
        val response = dynalistService.addToDocument(request).execute()
        val body = response.body()!!
        requireSuccess(body)
        // TODO set itemId of children here
        item.children.forEach { insertRecursively(it, token) }
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val token = Dynalist(applicationContext).token
        val item = box.query { equal(DynalistItem_.syncJob, "$id-root") }.findFirst()!!

        val request = InsertItemRequest(item.serverFileId!!, item.serverParentId!!, item.name,
                item.note, token!!)
        val response = dynalistService.addToDocument(request).execute()
        val body = response.body()!!
        requireSuccess(body)
        // TODO set itemId here
        insertRecursively(item, token)

        box.put(item.apply { syncJob = null })
        markItemsCompleted()
    }

}
