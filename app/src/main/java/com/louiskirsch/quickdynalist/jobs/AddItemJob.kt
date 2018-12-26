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
import com.louiskirsch.quickdynalist.network.InboxRequest
import org.jetbrains.annotations.Nullable


class AddItemJob(private val text: String)
    : Job(Params(1).requireNetwork().persist().groupBy("add_item")) {

    override fun onAdded() {
        EventBus.getDefault().post(ItemEvent(true))
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val token = Dynalist(applicationContext).token
        val service = DynalistApp.instance.dynalistService
        val response = service.addToInbox(InboxRequest(text, token!!)).execute()
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
        return RetryConstraint.RETRY
    }
}
