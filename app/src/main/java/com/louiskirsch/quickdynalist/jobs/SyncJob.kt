package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.*
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.DynalistDocument
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.utils.execRespectRateLimit
import com.louiskirsch.quickdynalist.widget.ListAppWidget
import io.objectbox.kotlin.inValues
import io.objectbox.kotlin.query
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.collections.forEachWithIndex
import org.jetbrains.annotations.Nullable
import java.util.*


class SyncJob(requireUnmeteredNetwork: Boolean = true, val isManual: Boolean = false)
    : Job(Params(-1).requireNetwork().setRequiresUnmeteredNetwork(requireUnmeteredNetwork)
        .singleInstanceBy(TAG).addTags(TAG)) {

    companion object {
        const val TAG = "syncJob"

        fun forceSync(isManual: Boolean = true) {
            DynalistApp.instance.jobManager.run {
                cancelJobsInBackground({
                    addJobInBackground(SyncJob(false, isManual))
                }, TagConstraint.ALL, arrayOf(TAG))
            }
        }

        private val delayCallback = { _: Any, delay: Long ->
            EventBus.getDefault().post(RateLimitDelay(delay, TAG))
        }
    }

    override fun onAdded() {}

    private fun syncDocument(service: DynalistService, token: String, file: File,
                             notAssociatedClientItems: MutableSet<DynalistItem>) {
        // TODO not sure what this is useful for
        val timeBeforeQuery = System.currentTimeMillis()
        val contents = service.readDocument(ReadDocumentRequest(file.id!!, token))
                .execRespectRateLimit(delayCallback).body()!!.nodes!!

        val clientItemsById = notAssociatedClientItems.associateBy { it.serverAbsoluteId }
        val clientItemsByContent = notAssociatedClientItems
                .associateBy { Pair(it.name, it.created.time) }

        // Transform server data
        val serverItems = contents.map {
            val serverAbsoluteId = Pair(file.id, it.id)
            val candidate = clientItemsById[serverAbsoluteId]
                    ?: clientItemsByContent[Pair(it.content, it.created)]
            val clientItem = if (candidate in notAssociatedClientItems) candidate else null
            clientItem?.apply {
                childrenIds = it.children ?: emptyList()
                val hasChanged = it.modified > modified.time
                if (hasChanged && syncJob == null) {
                    serverFileId = file.id
                    serverItemId = it.id
                    isChecked = it.checked
                    position = 0  // Will be set later when populating children
                    name = it.content
                    note = it.note
                    created = Date(it.created)
                    modified = Date(it.modified)
                    color = it.color
                    heading = it.heading
                    parent.target = null
                    serverParentId = it.parent  // Currently this does nothing, bug in API
                    if (hasChanged) {
                        updateMetaData()
                    }
                    isInbox = markedAsPrimaryInbox
                    isBookmark = markedAsBookmark
                }
                notAssociatedClientItems.remove(this)
            } ?: DynalistItem(file.id, it.parent, it.id, it.content, it.note,
                    it.children ?: emptyList(), isChecked = it.checked).apply {
                created = Date(it.created)
                modified = Date(it.modified)
                color = it.color
                heading = it.heading
                isInbox = markedAsPrimaryInbox
                isBookmark = markedAsBookmark
            }
        }
        val itemMap = serverItems.associateBy { it.serverAbsoluteId!! }
        serverItems.forEach { it.populateChildren(itemMap) }

        // Store new items in database
        DynalistItem.box.put(serverItems)
    }

    @Throws(Throwable::class)
    override fun onRun() {
        EventBus.getDefault().postSticky(SyncEvent(SyncStatus.RUNNING, isManual))
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        val service = DynalistApp.instance.dynalistService
        val box = DynalistItem.box

        // Query documents from server
        val newVersionedDocuments = findNewVersionedDocuments(token!!)
        val newDocuments = newVersionedDocuments.unzip().first

        // Query items from local database
        val clientItems = DynalistItem.box.query {
            inValues(DynalistItem_.serverFileId, newDocuments.map { it.id!! }.toTypedArray())
        }.find()
        val notAssociatedClientItems = clientItems.toMutableSet()

        newDocuments.forEachWithIndex { idx, doc ->
            syncDocument(service, token, doc, notAssociatedClientItems)
            // Report progress
            val progress = (idx + 1).toFloat() / newDocuments.size
            EventBus.getDefault().post(SyncProgressEvent(progress))
        }

        // Persist deleted items
        DynalistItem.box.remove(notAssociatedClientItems.filter { it.syncJob == null })

        // Update versions
        val localDocuments = DynalistDocument.box.all.associateBy { it.serverFileId!! }
        val updatedDocs = newVersionedDocuments.map { (remoteDoc, version) ->
            val localDoc = localDocuments[remoteDoc.id] ?: DynalistDocument(remoteDoc.id)
            localDoc.version = version
            localDoc
        }
        DynalistDocument.box.put(updatedDocs)

        // Find old inbox
        val previousInboxes = box.query { equal(DynalistItem_.isInbox, true) } .find()
        if (previousInboxes.isEmpty()) {
            val newInbox = detectInbox(token) ?: DynalistItem.box.query {
                equal(DynalistItem_.parentId, 0)
            }.findFirst()!!
            newInbox.apply {
                isInbox = true
                isBookmark = true
            }
            DynalistItem.box.put(newInbox)
        } else if(previousInboxes.size > 1) {
            // Remove duplicates
            DynalistItem.box.put(previousInboxes.sortedByDescending { it.markedAsPrimaryInbox }
                    .drop(1).apply { forEach { it.isInbox = false } })
        }

        dynalist.lastFullSync = Date()
        EventBus.getDefault().apply {
            postSticky(SyncEvent(SyncStatus.NOT_RUNNING, isManual))
            post(SyncEvent(SyncStatus.SUCCESS, isManual))
        }
        ListAppWidget.notifyAllDataChanged(applicationContext)
    }

    private fun detectInbox(token: String): DynalistItem? {
        val service = DynalistApp.instance.dynalistService
        val documents = DynalistDocument.box.all.map { it.serverFileId!! }.toTypedArray()
        val beforeVersions = service.checkForUpdates(VersionsRequest(documents, token))
                .execRespectRateLimit(delayCallback).body()!!.versions!!
        val request = InboxRequest(applicationContext.getString(R.string.detect_inbox_item),
                "", token)
        val response = service.addToInbox(request).execRespectRateLimit(delayCallback).body()!!
        if (response.isInboxNotConfigured)
            return null
        val afterVersions = service.checkForUpdates(VersionsRequest(documents, token))
                .execRespectRateLimit(delayCallback).body()!!.versions!!
        val inboxFileId = afterVersions.keys
                .firstOrNull { afterVersions.getValue(it) > beforeVersions.getValue(it) }
                ?: return null
        val docNodes = service.readDocument(ReadDocumentRequest(inboxFileId, token))
                .execRespectRateLimit(delayCallback).body()!!.nodes!!
        val insertedNodeId = docNodes.firstOrNull { it.id == response.node_id } ?.id
                ?: return null
        val inboxId = docNodes.firstOrNull { it.children!!.contains(insertedNodeId) } ?.id
                ?: return null
        service.deleteItem(DeleteItemRequest(inboxFileId, response.node_id!!, token))
                .execRespectRateLimit(delayCallback)
        return DynalistItem.byServerId(inboxFileId, inboxId)
    }

    private fun findNewVersionedDocuments(token: String): List<Pair<File, Long>> {
        val service = DynalistApp.instance.dynalistService
        val remoteFiles = service.listFiles(AuthenticatedRequest(token))
                .execRespectRateLimit(delayCallback).body()!!.files!!
        val baseVersions = DynalistDocument.box.all
                .associate { Pair(it.serverFileId!!, it.version) }
        val newVersions = service.checkForUpdates(
                VersionsRequest(remoteFiles.map { it.id!! }.toTypedArray(), token))
                .execRespectRateLimit(delayCallback).body()!!.versions!!
        return remoteFiles.filter { it.isDocument && it.isEditable
                    && newVersions[it.id]?.let { v -> v > (baseVersions[it.id] ?: -1) } ?: true }
                .map { it to newVersions[it.id]!! }
    }

    override fun getRetryLimit(): Int = 2
    override fun onCancel(@CancelReason cancelReason: Int, @Nullable throwable: Throwable?) {
        if (cancelReason == CancelReason.REACHED_RETRY_LIMIT) {
            EventBus.getDefault().post(SyncEvent(SyncStatus.NO_SUCCESS, isManual))
        }
    }

    override fun shouldReRunOnThrowable(throwable: Throwable, runCount: Int,
                                        maxRunCount: Int): RetryConstraint {
        return RetryConstraint.createExponentialBackoff(runCount, 10 * 1000)
    }
}

