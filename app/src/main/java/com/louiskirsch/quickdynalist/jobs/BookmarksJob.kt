package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.RetryConstraint
import com.birbit.android.jobqueue.CancelReason
import com.birbit.android.jobqueue.Job
import com.birbit.android.jobqueue.Params
import com.louiskirsch.quickdynalist.BookmarksUpdatedEvent
import com.louiskirsch.quickdynalist.Dynalist
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.network.AuthenticatedRequest
import com.louiskirsch.quickdynalist.network.ReadDocumentRequest
import org.greenrobot.eventbus.EventBus
import org.jetbrains.annotations.Nullable
import java.io.Serializable


class BookmarksJob(val largest_k: Int = 9)
    : Job(Params(-1).requireUnmeteredNetwork().singleInstanceBy("bookmarks")) {

    override fun onAdded() {}

    @Throws(Throwable::class)
    override fun onRun() {
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        val service = DynalistApp.instance.dynalistService

        val files = service.listFiles(AuthenticatedRequest(token!!)).execute().body()!!.files!!
        val documents = files.filter { x -> x.isDocument && x.isEditable }
        val contents = documents.map {
            x -> service.readDocument(ReadDocumentRequest(x.id!!, token)).execute().body()!!.nodes!!
        }
        val candidates = documents.zip(contents).flatMap {
            (doc, content) -> content.map { x ->
                Bookmark(doc.id!!, x.id, x.content, x.note, x.children?.size ?: 0) }
        }
        val markedItems = candidates.filter { x -> x.markedAsBookmark }
        val bookmarks = if (markedItems.isEmpty()) {
            candidates.filter { x -> x.elem_count > 10 && !x.mightBeInbox }
                    .sortedByDescending { x -> x.elem_count }
                    .take(largest_k)
                    .toTypedArray()
        } else {
            markedItems.toTypedArray()
        }
        dynalist.bookmarks = bookmarks
        EventBus.getDefault().post(BookmarksUpdatedEvent(bookmarks))
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {}

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}

class Bookmark(val file_id: String, val id: String,
               val name: String, val note: String, val elem_count: Int) : Serializable {

    override fun toString(): String {
        val label = strippedMarkersName
        return if (label.length > 30)
            "${label.take(27)}..."
        else
            label
    }

    val isInbox: Boolean
        get() = id == "Inbox"

    val mightBeInbox: Boolean
        get() = name.toLowerCase() == "inbox"

    val markedAsBookmark: Boolean
        get() = markers.any { x -> name.contains(x, true)
                                || note.contains(x, true) }

    private val strippedMarkersName: String
        get() = markers.fold(name) { acc, marker ->
            acc.replace(marker, "", true) }
                .replace("# ", "")
                .trim()

    companion object {
        fun newInbox() = Bookmark("none", "Inbox", "Inbox", "", -1)
        private val markers = listOf("#quickdynalist", "#inbox",
                "📒", "📓", "📔", "📕", "📖", "📗", "📘", "📙")
    }
}
