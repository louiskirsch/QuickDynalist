package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import org.jetbrains.anko.collections.forEachWithIndex
import retrofit2.Response


class DeleteItemJob(val item: DynalistItem): ItemJob() {

    override fun addToDatabase() {
        val parent = item.parent.target
        DynalistApp.instance.boxStore.runInTx {
            box.get(item.clientId)?.let { item ->
                val items = getChildrenRecursively(item) + listOf(item)
                items.forEach {
                    it.hidden = true
                    it.syncJob = id
                }
                box.put(items)
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, parent)
    }

    private fun getChildrenRecursively(item: DynalistItem): List<DynalistItem> {
        val children = item.children
        return children.flatMap { getChildrenRecursively(it) } + children
    }

    @Throws(Throwable::class)
    override fun onRun() {
        requireItemId(item)
        val token = Dynalist(applicationContext).token
        val request = DeleteItemRequest(item.serverFileId!!, item.serverItemId!!, token!!)
        val response = dynalistService.deleteItem(request).execute()
        val body = response.body()!!
        requireSuccess(body)
        DynalistApp.instance.boxStore.runInTx {
            val items = box.query {
                equal(DynalistItem_.syncJob, id, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            }.find()
            box.remove(items)
        }
    }

}
