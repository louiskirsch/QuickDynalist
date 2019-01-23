package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.RetryConstraint
import com.birbit.android.jobqueue.CancelReason
import com.birbit.android.jobqueue.Job
import com.birbit.android.jobqueue.Params
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.AuthenticatedRequest
import com.louiskirsch.quickdynalist.network.ReadDocumentRequest
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import org.jetbrains.annotations.Nullable
import java.util.*


class BookmarksJob
    : Job(Params(-1).requireUnmeteredNetwork().singleInstanceBy("dynalistItems")) {

    override fun onAdded() {}

    @Throws(Throwable::class)
    override fun onRun() {
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        val service = DynalistApp.instance.dynalistService
        val box: Box<DynalistItem> = DynalistApp.instance.boxStore.boxFor()

        // Query from server
        val files = service.listFiles(AuthenticatedRequest(token!!)).execute().body()!!.files!!
        val documents = files.filter { it.isDocument && it.isEditable }
        val contents = documents.map {
            service.readDocument(ReadDocumentRequest(it.id!!, token)).execute().body()!!.nodes!!
        }

        // Query from local database
        val clientItems = box.query{}.find()
        val previousInbox = box.query { equal(DynalistItem_.isInbox, true) } .findUnique()!!
        val clientItemsById = clientItems.associateBy { it.serverAbsoluteId }
        val clientItemsByName = clientItems.associateBy { it.name }
        val notAssociatedClientItems = clientItems.toMutableSet()

        // Transform server data
        val serverItems = documents.zip(contents).flatMap {
            (doc, content) -> content.map {
                val serverAbsoluteId = Pair(doc.id!!, it.id)
                val candidate = clientItemsById[serverAbsoluteId] ?: clientItemsByName[it.content]
                val clientItem = if (candidate in notAssociatedClientItems) candidate else null
                clientItem?.apply {
                    serverFileId = doc.id
                    serverParentId = it.parent  // Currently this does nothing, bug in API
                    serverItemId = it.id
                    name = it.content
                    note = it.note
                    childrenIds = it.children ?: emptyList()
                    isInbox = false
                    isBookmark = false
                    position = 0
                    parent.target = null
                    notAssociatedClientItems.remove(this)
                } ?: DynalistItem(doc.id, it.parent, it.id, it.content, it.note,
                        it.children ?: emptyList())
            }
        }
        val itemMap = serverItems.associateBy { it.serverAbsoluteId!! }
        val markedItems = serverItems.filter { it.markedAsBookmark }
        serverItems.forEach { it.populateChildren(itemMap) }

        // Find old inbox
        val newInbox: DynalistItem = markedItems.firstOrNull { it.markedAsPrimaryInbox } ?: run {
            if (previousInbox in notAssociatedClientItems) {
                previousInbox.apply {
                    serverFileId = null
                    serverParentId = null
                    serverItemId = null
                    name = "\uD83D\uDCE5 Inbox"
                    note = ""
                    parent.target = null
                }
            } else {
                DynalistItem.newInbox()
            }
        }
        newInbox.apply {
            isInbox = true
            isBookmark = true
        }

        // Define bookmarks
        val bookmarks = markedItems.filter { !it.markedAsPrimaryInbox }
        bookmarks.forEach { it.isBookmark = true }

        // Store new items in database
        DynalistApp.instance.boxStore.runInTx {
            box.remove(notAssociatedClientItems - newInbox)
            box.put(serverItems)
        }
        dynalist.lastBookmarkQuery = Date()
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {}

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}

