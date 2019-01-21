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


class BookmarksJob(private val largest_k: Int = 9)
    : Job(Params(-1).requireUnmeteredNetwork().singleInstanceBy("dynalistItems")) {

    override fun onAdded() {}

    @Throws(Throwable::class)
    override fun onRun() {
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        val service = DynalistApp.instance.dynalistService
        val box: Box<DynalistItem> = DynalistApp.instance.boxStore.boxFor()

        val files = service.listFiles(AuthenticatedRequest(token!!)).execute().body()!!.files!!
        val documents = files.filter { it.isDocument && it.isEditable }
        val contents = documents.map {
            service.readDocument(ReadDocumentRequest(it.id!!, token)).execute().body()!!.nodes!!
        }

        val candidates = documents.zip(contents).flatMap {
            (doc, content) -> content.map {
            DynalistItem(doc.id, it.parent, it.id, it.content, it.note,
                    it.children ?: emptyList())
        }
        }
        val itemMap = candidates.associateBy { it.serverAbsoluteId!! }
        val itemByNameMap = candidates.groupBy { it.name }
        val markedItems = candidates.filter { it.markedAsBookmark }

        // TEMPORARY FIX - MISTAKE IN API SPEC
        candidates.forEach { it.fixParents(itemMap) }

        val newInbox: DynalistItem = markedItems.firstOrNull { it.markedAsPrimaryInbox } ?: run {
            val previousInbox = box.query { equal(DynalistItem_.isInbox, true) }
                                    .findUnique()!!
            val guessedParents = previousInbox.children.filter { it.serverItemId == null }
                    .flatMap {
                        itemByNameMap[it.name]?.mapNotNull { match ->
                            itemMap[Pair(match.serverFileId, match.serverParentId)]
                        }
                        ?: emptyList()
                    }
            val guessedParentCounts = guessedParents.groupingBy { it }.eachCount()
            val inbox = guessedParentCounts.maxBy { it.value }?.key
                    ?: previousInbox.serverAbsoluteId?.let { itemMap[it] }
                    ?: previousInbox
            inbox.run {
                DynalistItem(serverFileId, serverParentId, serverItemId,
                        "\uD83D\uDCE5 Inbox", "",
                        childrenIds ?: emptyList(), true)
            }
        }
        newInbox.apply {
            isInbox = true
            isBookmark = true
            populateChildren(itemMap)
        }

        val bookmarks = if (markedItems.isEmpty()) {
            candidates.filter { it.childrenCount > 10 && !it.mightBeInbox }
                    .sortedByDescending { it.childrenCount }
                    .take(largest_k)
        } else {
            markedItems.filter { !it.markedAsPrimaryInbox }
        }
        bookmarks.forEach {
            it.isBookmark = true
            it.populateChildren(itemMap)
        }

        DynalistApp.instance.boxStore.runInTx {
            box.removeAll()
            box.put(listOf(newInbox) + bookmarks)
        }
        dynalist.lastBookmarkQuery = Date()
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {}

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}

