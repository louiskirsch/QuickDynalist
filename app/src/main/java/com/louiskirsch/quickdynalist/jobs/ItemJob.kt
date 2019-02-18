package com.louiskirsch.quickdynalist.jobs

import android.util.Log
import com.birbit.android.jobqueue.*
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.DynalistResponse
import com.louiskirsch.quickdynalist.network.DynalistService
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemMetaData
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import okhttp3.internal.Util.equal
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.doAsync

abstract class ItemJob: Job(Params(1)
        .requireNetwork().persist().groupBy("itemJob")) {

    protected val box: Box<DynalistItem>
        get() = DynalistApp.instance.boxStore.boxFor()

    protected val metaBox: Box<DynalistItemMetaData>
        get() = DynalistApp.instance.boxStore.boxFor()

    protected val dynalistService: DynalistService
        get() = DynalistApp.instance.dynalistService

    protected fun requireItemId(item: DynalistItem) {
        val updatedItem = box.get(item.clientId)
        if (updatedItem == null && item.serverItemId == null)
            throw InvalidJobException("Can not find item in database but requires the itemId")
        item.serverItemId = updatedItem?.serverItemId ?: item.serverItemId
        if (item.serverItemId == null) {
            if (currentRunCount > 3)
                throw InvalidJobException("Can not retrieve itemId after 3 trials")
            SyncJob.forceSync(false)
            Log.e("ItemJob", "Had to trigger force sync to retrieve itemId!")
            throw ItemIdUnavailableException()
        }
    }

    protected fun requireSuccess(body: DynalistResponse) {
        when {
            body.isInvalidToken -> {
                EventBus.getDefault().post(AuthenticatedEvent(false))
                throw NotAuthenticatedException()
            }
            body.isInboxNotConfigured -> {
                EventBus.getDefault().post(NoInboxEvent())
                throw NoInboxException()
            }
            body.isRequestUnfulfillable -> throw InvalidJobException(body.errorDesc)
            !body.isOK -> throw BackendException(body.errorDesc)
        }
    }

    protected abstract fun addToDatabase()

    override fun onAdded() {
        EventBus.getDefault().post(ItemEvent(true))
        doAsync { addToDatabase() }
    }

    protected val jobItems: List<DynalistItem>
        get() = box.query { equal(DynalistItem_.syncJob, id) }.find()

    protected fun markItemsCompleted() {
        DynalistApp.instance.boxStore.runInTx {
            box.put(jobItems.apply { forEach { it.syncJob = null } } )
        }
    }

    override fun onCancel(cancelReason: Int, throwable: Throwable?) {
        EventBus.getDefault().post(ItemEvent(false))
        markItemsCompleted()
    }

    override fun getRetryLimit(): Int = 13

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        val constraint = when (throwable) {
            is InvalidJobException -> RetryConstraint.CANCEL
            is NoInboxException -> {
                val constraint = RetryConstraint(true)
                constraint.newDelayInMs = 60 * 1000
                return constraint
            }
            // Wait 24 hours at maximum
            else -> RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
        }
        constraint.setApplyNewDelayToGroup(true)
        return constraint
    }
}
