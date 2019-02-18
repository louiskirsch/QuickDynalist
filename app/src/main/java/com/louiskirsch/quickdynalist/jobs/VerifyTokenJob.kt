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
import com.louiskirsch.quickdynalist.network.AuthenticatedRequest
import org.jetbrains.annotations.Nullable


class VerifyTokenJob(private val token: String)
    : Job(Params(2).requireNetwork().singleInstanceBy("verify_token")) {

    override fun onAdded() {}

    @Throws(Throwable::class)
    override fun onRun() {
        val service = DynalistApp.instance.dynalistService
        val dynalist = Dynalist(applicationContext)

        val call = service.listFiles(AuthenticatedRequest(token))
        val response = call.execute()
        val success = response.isSuccessful && response.body()!!.isOK
        if (!success)
            throw BackendException(response.body()?.errorDesc ?: "")
        dynalist.token = token
        EventBus.getDefault().post(AuthenticatedEvent(success))
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {
        EventBus.getDefault().post(AuthenticatedEvent(false))
    }

    override fun getRetryLimit() = 0

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.CANCEL
    }
}
