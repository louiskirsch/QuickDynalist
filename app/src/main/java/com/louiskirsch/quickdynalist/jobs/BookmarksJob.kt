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
        val bookmarks = documents.zip(contents).flatMap {
            (doc, content) -> content.map { x ->
                Bookmark(doc.id!!, x.id, x.content, x.children?.size ?: 0) }
        }.filter { x -> x.elem_count > 10 && !x.mightBeInbox }
        val sortedBookmarks = bookmarks.sortedByDescending { x -> x.elem_count }
        val newBookmarks = sortedBookmarks.take(largest_k).toTypedArray()
        dynalist.bookmarks = newBookmarks
        EventBus.getDefault().post(BookmarksUpdatedEvent(newBookmarks))
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {}

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}

class Bookmark(val file_id: String, val id: String,
               val name: String, val elem_count: Int) : Serializable {
    override fun toString(): String {
        val displayName = if (name.length > 30)
            "${name.take(27)}..."
        else
            name
        return if (isInbox) displayName else "$displayName ($elem_count)"
    }

    val isInbox: Boolean
        get() = id == "Inbox"

    val mightBeInbox: Boolean
        get() = name.toLowerCase() == "inbox"

    companion object {
        fun newInbox() = Bookmark("none", "Inbox", "Inbox", -1)
    }
}
