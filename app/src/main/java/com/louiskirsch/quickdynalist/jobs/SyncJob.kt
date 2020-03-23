package com.louiskirsch.quickdynalist.jobs

import com.birbit.android.jobqueue.*
import com.louiskirsch.quickdynalist.*
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.network.*
import com.louiskirsch.quickdynalist.objectbox.*
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

    private fun syncDocument(service: DynalistService, token: String, file: File, syncStart: Long,
                             notAssociatedClientItems: MutableSet<DynalistItem>,
                             itemsWithInvalidMetaData: LinkedList<DynalistItem>) {
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
                if (syncJob == null) {
                    checkbox = it.checkbox
                }
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
                    isBookmark = markedAsBookmark || isInbox
                    itemsWithInvalidMetaData.add(this)
                }
                notAssociatedClientItems.remove(this)
            } ?: DynalistItem(file.id, it.parent, it.id, it.content, it.note,
                    it.children ?: emptyList(), isChecked = it.checked).apply {
                created = Date(it.created)
                modified = Date(it.modified)
                color = it.color
                heading = it.heading
                checkbox = it.checkbox
                isBookmark = markedAsBookmark || isInbox
                itemsWithInvalidMetaData.add(this)
            }
        }
        val itemMap = serverItems.associateBy { it.serverAbsoluteId!! }
        serverItems.forEach { it.populateChildren(itemMap) }

        // Store new items in database
        val modified = getModifiedAfterSync(syncStart, file.id).toSet()
        DynalistItem.box.put(serverItems.filter { it.clientId !in modified })
    }

    private fun getModifiedAfterSync(syncStart: Long, file: String): LongArray {
        return DynalistItem.box.query {
            equal(DynalistItem_.serverFileId, file)
            greater(DynalistItem_.modified, syncStart)
        }.findIds()
    }

    @Throws(Throwable::class)
    override fun onRun() {
        EventBus.getDefault().postSticky(SyncEvent(SyncStatus.RUNNING, isManual))
        val dynalist = Dynalist(applicationContext)
        val token = dynalist.token
        val service = DynalistApp.instance.dynalistService
        val box = DynalistItem.box

        // Query documents from server
        val remoteFilesResponse = service.listFiles(AuthenticatedRequest(token!!))
                .execRespectRateLimit(delayCallback).body()
        val remoteFiles = when {
            remoteFilesResponse == null -> {
                throw BackendException("Could not query remote files")
            }
            remoteFilesResponse.isInvalidToken -> {
                EventBus.getDefault().post(AuthenticatedEvent(false))
                throw NotAuthenticatedException()
            }
            !remoteFilesResponse.isOK -> {
                throw BackendException("Remote files query returned, ${remoteFilesResponse.errorDesc}")
            }
            else -> remoteFilesResponse.files!!
        }
        val remoteFolders = remoteFiles.filter { it.isFolder }
        val remoteDocuments = remoteFiles.filter { it.isDocument && it.isEditable }
        deleteDisappearedDocuments(remoteDocuments)
        val newVersionedDocuments = findNewVersionedDocuments(token, remoteDocuments)
        val newDocuments = newVersionedDocuments.unzip().first

        // Query items from local database
        val syncStart = System.currentTimeMillis()
        val newFileIds = newDocuments.map { it.id!! }.toTypedArray()
        val clientItems = DynalistItem.box.query {
            inValues(DynalistItem_.serverFileId, newFileIds)
        }.find()
        val notAssociatedClientItems = clientItems.toMutableSet()
        val itemsWithInvalidMetaData = LinkedList<DynalistItem>()

        newDocuments.forEachWithIndex { idx, doc ->
            syncDocument(service, token, doc, syncStart,
                    notAssociatedClientItems, itemsWithInvalidMetaData)
            // Report progress
            val progress = (idx + 1).toFloat() / newDocuments.size
            EventBus.getDefault().post(SyncProgressEvent(progress))
        }

        // Determine deleted items
        val deletedItems = notAssociatedClientItems.filter { it.syncJob == null }

        // Update meta data
        itemsWithInvalidMetaData.forEach { it.updateMetaData() }

        // Update versions
        val localDocuments = DynalistDocument.box.all.associateBy { it.serverFileId!! }
                .toMutableMap()
        newVersionedDocuments.forEach { (remoteDoc, version) ->
            val localDoc = localDocuments[remoteDoc.id] ?: DynalistDocument(remoteDoc.id).also {
                localDocuments[it.serverFileId!!] = it
            }
            localDoc.version = version
        }

        // Update folder structure
        val localFolders = DynalistFolder.box.all.associateBy { it.serverFolderId!! }.toMutableMap()
        val remoteFolderIds = remoteFolders.map { it.id!! }.toSet()
        val removeFolders = localFolders - remoteFolderIds
        localFolders.keys.removeAll(removeFolders.keys)
        remoteFolders.forEach { folder ->
            (localFolders[folder.id] ?: DynalistFolder(folder.id)).apply {
                title = folder.title
                val anyChildren = folder.children?.mapNotNull {
                    localFolders[it] ?: localDocuments[it] ?: if (it in remoteFolderIds)
                        DynalistFolder(it).also { newFolder -> localFolders[it] = newFolder }
                    else null
                } ?: emptyList()
                anyChildren.forEachIndexed { idx, it ->
                    it.position = idx
                    it.parent.target = this
                }
                localFolders[folder.id!!] = this
            }
        }

        // Persist previous changes
        DynalistApp.instance.boxStore.runInTx {
            box.remove(deletedItems)
            box.put(itemsWithInvalidMetaData)
            DynalistFolder.box.remove(removeFolders.values)
            DynalistFolder.box.put(localFolders.values)
            DynalistDocument.box.put(localDocuments.values)
        }

        // Configure inbox
        val newInbox = queryInbox(token)
        val previousInbox = box.query { equal(DynalistItem_.isInbox, true) } .findFirst()
        if (newInbox != previousInbox) {
            previousInbox?.apply {
                isInbox = false
                isBookmark = markedAsBookmark
            }
            newInbox?.apply {
                isInbox = true
                isBookmark = true
            }
            DynalistItem.box.put(listOfNotNull(newInbox, previousInbox))
        }

        dynalist.lastFullSync = Date()
        EventBus.getDefault().apply {
            postSticky(SyncEvent(SyncStatus.NOT_RUNNING, isManual))
            post(SyncEvent(SyncStatus.SUCCESS, isManual))
            postSticky(InboxEvent(configured = newInbox != null))
        }
        ListAppWidget.notifyAllDataChanged(applicationContext)
    }

    private fun queryInbox(token: String): DynalistItem? {
        val service = DynalistApp.instance.dynalistService
        val (inboxFileId, inboxId) = service.getPreference(
                PreferenceRequest("inbox_location", token))
                .execRespectRateLimit(delayCallback).body()!!.parsedItemValue ?: return null
        return DynalistItem.byServerId(inboxFileId, inboxId)
    }

    private fun findNewVersionedDocuments(token: String, remoteFiles: List<File>)
            : List<Pair<File, Long>> {
        val service = DynalistApp.instance.dynalistService
        val baseVersions = DynalistDocument.box.all
                .associate { Pair(it.serverFileId!!, it.version) }
        val newVersions = service.checkForUpdates(
                VersionsRequest(remoteFiles.map { it.id!! }.toTypedArray(), token))
                .execRespectRateLimit(delayCallback).body()!!.versions!!
        return remoteFiles.filter { newVersions[it.id]
                ?.let { v -> v > (baseVersions[it.id] ?: -1) } ?: true }
                .map { it to newVersions[it.id]!! }
    }

    private fun deleteDisappearedDocuments(remoteFiles: List<File>) {
        val remoteFileIds = remoteFiles.map { it.id!! }
        val docsToRemove = DynalistDocument.box.all.associateBy { it.serverFileId!! } - remoteFileIds
        if (docsToRemove.isNotEmpty()) {
            DynalistDocument.box.remove(docsToRemove.values)
            DynalistItem.box.query {
                inValues(DynalistItem_.serverFileId, docsToRemove.keys.toTypedArray())
            }.remove()
        }
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

