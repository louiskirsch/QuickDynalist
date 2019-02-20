package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import org.jetbrains.anko.collections.forEachWithIndex
import retrofit2.Response


class MoveItemJob(val item: DynalistItem, val parent: DynalistItem, val toPosition: Int): ItemJob() {

    override fun addToDatabase() {
        DynalistApp.instance.boxStore.runInTx {
            box.get(item.clientId)?.let { item ->
                val currentChildren = box.query {
                    equal(DynalistItem_.parentId, parent.clientId)
                    order(DynalistItem_.position)
                }.find()
                if (item.parent.target == parent)
                    currentChildren.remove(item)
                else {
                    item.parent.target = parent
                    item.serverParentId = parent.serverItemId
                    item.serverFileId = parent.serverFileId
                }
                val resolvedPosition = if (toPosition == -1) currentChildren.size else toPosition
                currentChildren.add(resolvedPosition, item)
                currentChildren.forEachWithIndex { i, it ->
                    it.position = i
                    it.syncJob = id
                }
                box.put(currentChildren)
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, item)
        ListAppWidget.notifyItemChanged(applicationContext, parent)
    }

    @Throws(Throwable::class)
    override fun onRun() {
        requireItemId(parent)
        requireItemId(item)
        val token = Dynalist(applicationContext).token
        if (item.serverFileId == parent.serverFileId) {
            val request = MoveItemRequest(parent.serverFileId!!, parent.serverItemId!!,
                    item.serverItemId!!, toPosition, token!!)
            val response = dynalistService.moveItem(request).execute()
            val body = response.body()!!
            requireSuccess(body)
        } else {
            // TODO Can't move into different document with current API. Cloning required.
            box.get(item.clientId)?.also { movedItem ->
                val request = DeleteItemRequest(item.serverFileId!!, item.serverItemId!!, token!!)
                val response = dynalistService.deleteItem(request).execute().body()!!
                requireSuccess(response)
                val treeService = AddItemTreeService(this)
                val updatedItems = treeService.insert(movedItem)
                box.put(updatedItems)
            }
        }
        markItemsCompleted()
    }

}
