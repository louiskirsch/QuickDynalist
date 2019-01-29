package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.RetryConstraint
import org.greenrobot.eventbus.EventBus
import com.birbit.android.jobqueue.CancelReason
import com.birbit.android.jobqueue.Job
import com.birbit.android.jobqueue.Params
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.network.DynalistResponse
import com.louiskirsch.quickdynalist.network.InboxRequest
import com.louiskirsch.quickdynalist.network.InsertItemRequest
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import org.jetbrains.anko.doAsync
import org.jetbrains.annotations.Nullable
import retrofit2.Response


class AddItemJob(text: String, note: String, val parent: DynalistItem)
    : Job(Params(1).requireNetwork().persist().groupBy("add_item")) {

    private val newItem = DynalistItem(parent.serverFileId, parent.serverItemId,
            null, text, note)

    override fun onAdded() {
        doAsync {
            val box: Box<DynalistItem> = DynalistApp.instance.boxStore.boxFor()
            DynalistApp.instance.boxStore.runInTx {
                box.get(parent.clientId)?.let { parent ->
                    newItem.syncJob = id
                    newItem.position = parent.children.size
                    newItem.parent.target = parent
                    box.put(newItem)
                }
            }
        }
        EventBus.getDefault().post(ItemEvent(true))
    }

    private fun insertAPIRequest(): Response<DynalistResponse> {
        val token = Dynalist(applicationContext).token
        val service = DynalistApp.instance.dynalistService

        return if (parent.isInbox) {
            service.addToInbox(InboxRequest(newItem.name, newItem.note, token!!)).execute()
        } else {
            val request = InsertItemRequest(parent.serverFileId!!, parent.serverItemId!!, newItem.name,
                                            newItem.note, token!!)
            val call = service.addToDocument(request)
            call.execute()
        }
    }

    @Throws(Throwable::class)
    override fun onRun() {
        val response = insertAPIRequest()

        if (response.body()!!.isInvalidToken)
            EventBus.getDefault().post(AuthenticatedEvent(false))
        else if (response.body()!!.isInboxNotConfigured) {
            EventBus.getDefault().post(NoInboxEvent())
            throw NoInboxException()
        }

        if (!response.body()!!.isOK)
            throw BackendException()

        val box: Box<DynalistItem> = DynalistApp.instance.boxStore.boxFor()
        DynalistApp.instance.boxStore.runInTx {
            box.query { equal(DynalistItem_.syncJob, id) }.findFirst()!!.let { newItem ->
                newItem.syncJob = null
                box.put(newItem)
            }
        }
    }

    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {
        EventBus.getDefault().post(ItemEvent(false))
    }

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        if (throwable is NoInboxException) {
            val constraint = RetryConstraint(true)
            constraint.newDelayInMs = 60 * 1000
            return constraint
        }
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}
