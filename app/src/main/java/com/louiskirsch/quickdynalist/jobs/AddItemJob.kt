package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.RetryConstraint
import org.greenrobot.eventbus.EventBus
import com.birbit.android.jobqueue.CancelReason
import com.birbit.android.jobqueue.Job
import com.birbit.android.jobqueue.Params
import com.louiskirsch.quickdynalist.AuthenticatedEvent
import com.louiskirsch.quickdynalist.Dynalist
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.ItemEvent
import com.louiskirsch.quickdynalist.network.DynalistResponse
import com.louiskirsch.quickdynalist.network.InboxRequest
import com.louiskirsch.quickdynalist.network.InsertItemRequest
import org.jetbrains.annotations.Nullable
import retrofit2.Response


class AddItemJob(val text: String, val note: String, val parent: Bookmark)
    : Job(Params(1).requireNetwork().persist().groupBy("add_item")) {

    override fun onAdded() {
        EventBus.getDefault().post(ItemEvent(true))
    }

    private fun insertAPIRequest(): Response<DynalistResponse> {
        val token = Dynalist(applicationContext).token
        val service = DynalistApp.instance.dynalistService

        return if (parent.isInbox) {
            service.addToInbox(InboxRequest(text, note, token!!)).execute()
        } else {
            val request = InsertItemRequest(parent.file_id, parent.id, text, note, token!!)
            val call = service.addToDocument(request)
            call.execute()
        }
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val response = insertAPIRequest()

        if (response.body()!!.isInvalidToken)
            EventBus.getDefault().post(AuthenticatedEvent(false))
        if (!response.body()!!.isOK)
            throw BackendException()
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {
        EventBus.getDefault().post(ItemEvent(false))
    }

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}
