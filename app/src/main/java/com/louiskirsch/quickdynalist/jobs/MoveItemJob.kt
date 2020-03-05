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
        val dynalist = Dynalist(applicationContext)
        DynalistApp.instance.boxStore.runInTx {
            box.get(item.clientId)?.let { item ->
                val currentChildren = box.query {
                    equal(DynalistItem_.parentId, parent.clientId)
                    order(DynalistItem_.position)
                }.find()
                val previousPosition = if (item.parent.target == parent) {
                    currentChildren.indexOf(item)
                } else null
                if (previousPosition != null) {
                    currentChildren.removeAt(previousPosition)
                } else {
                    item.parent.target = parent
                    item.serverParentId = parent.serverItemId
                    item.serverFileId = parent.serverFileId
                }
                val resolvedPosition = when (toPosition) {
                    Int.MIN_VALUE -> if(dynalist.addToTopOfList) 0 else currentChildren.size
                    else -> currentChildren.indexOfFirst { it.position == toPosition }.let {
                        // Account for the fact that we removed this item prior to taking the index
                        if (previousPosition != null && previousPosition <= it)
                            it + 1
                        else
                            it
                    }
                }
                currentChildren.add(resolvedPosition, item)
                currentChildren.forEachWithIndex { i, it ->
                    it.position = i
                    it.syncJob = id
                }
                box.put(currentChildren + updateLocationRecursively(item))
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, item)
        ListAppWidget.notifyItemChanged(applicationContext, parent)
    }

    private fun updateLocationRecursively(item: DynalistItem): List<DynalistItem> {
        item.children.forEach {
            it.syncJob = id
            it.serverFileId = parent.serverFileId
        }
        return item.children + item.children.flatMap { updateLocationRecursively(it) }
    }

    @Throws(Throwable::class)
    override fun onRun() {
        requireItemId(parent)
        requireItemId(item)
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        if (item.serverFileId == parent.serverFileId) {
            val newIndex = when (toPosition) {
                Int.MIN_VALUE -> dynalist.insertPosition
                else -> childIndex(item.clientId)
            }
            val request = MoveItemRequest(parent.serverFileId!!, parent.serverItemId!!,
                    item.serverItemId!!, newIndex, token!!)
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
