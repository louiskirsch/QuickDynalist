package com.louiskirsch.quickdynalist.jobs

import android.util.Log
import com.birbit.android.jobqueue.*
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.DynalistResponse
import com.louiskirsch.quickdynalist.network.DynalistService
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.doAsync

abstract class ItemJob: Job(Params(1)
        .requireNetwork().persist().groupBy("itemJob")) {

    protected val box: Box<DynalistItem>
        get() = DynalistApp.instance.boxStore.boxFor()

    protected val dynalistService: DynalistService
        get() = DynalistApp.instance.dynalistService

    protected fun requireItemId(item: DynalistItem) {
        if (item.serverItemId != null)
            return
        val updatedItem = box.get(item.clientId)
                ?: throw InvalidJobException("Can not find item in database but requires the itemId")
        item.serverItemId = updatedItem.serverItemId
        if (item.serverItemId == null) {
            if (currentRunCount > 3)
                throw InvalidJobException("Can not retrieve itemId after 3 trials")
            SyncJob.forceSync(false)
            Log.e("ItemJob", "Had to trigger force sync to retrieve itemId!")
            throw ItemIdUnavailableException()
        }
    }

    fun requireSuccess(body: DynalistResponse) {
        when {
            body.isInvalidToken -> {
                EventBus.getDefault().post(AuthenticatedEvent(false))
                throw NotAuthenticatedException()
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
        get() = box.query {
            equal(DynalistItem_.syncJob, id, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        }.find()

    protected fun markItemsCompleted() {
        DynalistApp.instance.boxStore.runInTx {
            box.put(jobItems.apply { forEach { it.syncJob = null } } )
        }
    }

    override fun onCancel(cancelReason: Int, throwable: Throwable?) {
        EventBus.getDefault().post(ItemEvent(false, retrying = false))
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
        if (constraint.shouldRetry())
            EventBus.getDefault().post(ItemEvent(false, retrying = true))
        return constraint
    }

    protected fun minPosition(parentId: Long): Int? {
        val minimum = box.query { equal(DynalistItem_.parentId, parentId) }
                .property(DynalistItem_.position).min()
        return if (minimum == Long.MAX_VALUE) null else minimum.toInt()
    }

    protected fun maxPosition(parentId: Long): Int? {
        val maximum = box.query { equal(DynalistItem_.parentId, parentId) }
                .property(DynalistItem_.position).max()
        return if (maximum == Long.MIN_VALUE) null else maximum.toInt()
    }

    protected fun childIndex(itemId: Long): Int {
        val item = box.get(itemId)
        return box.query {
            equal(DynalistItem_.parentId, item.parent.targetId)
            less(DynalistItem_.position, item.position.toLong())
        }.count().toInt()
    }

    companion object {
        const val TAG = "ItemJob"
    }
}
