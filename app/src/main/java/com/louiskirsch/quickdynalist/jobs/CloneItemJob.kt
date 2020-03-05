package com.louiskirsch.quickdynalist.jobs

import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.query
import io.objectbox.query.Query
import java.util.*


class CloneItemJob(val item: DynalistItem): ItemJob() {

    override fun addToDatabase() {
        val now = Date()
        val dynalist = Dynalist(applicationContext)
        DynalistApp.instance.boxStore.runInTx {
            box.get(item.clientId)?.let { item ->
                item.position = if (dynalist.addToTopOfList) {
                    (minPosition(item.parent.targetId) ?: 1) - 1
                } else {
                    (maxPosition(item.parent.targetId) ?: -1) + 1
                }
                if (item.date != null)
                    item.date = Date()
                box.put(cloneRecursively(item, now).apply { syncJob = "$id-root" })
            }
        }
        ListAppWidget.notifyItemChanged(applicationContext, item)
    }

    private fun cloneRecursively(item: DynalistItem, time: Date): DynalistItem {
        val newChildren = item.children.map {
            cloneRecursively(it, time).apply { it.parent.targetId = 0 }
        }
        item.apply {
            clientId = 0
            serverItemId = null
            syncJob = id
            isChecked = false
            children.clear()
            children.addAll(newChildren)
            notifyModified(time)
        }
        return item
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
        // need to wait for db operations to complete
        val item = waitForItem(box.query { equal(DynalistItem_.syncJob, "$id-root") })
                ?: throw InvalidJobException("Item to clone has vanished")

        val treeService = AddItemTreeService( this)
        val updatedItems = treeService.insert(item)
        box.put(updatedItems.apply { forEach { it.syncJob = null } })
    }

}
