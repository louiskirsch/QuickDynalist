package com.louiskirsch.quickdynalist.objectbox

import com.louiskirsch.quickdynalist.DynalistApp
import io.objectbox.Box
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToOne

@Entity
class DynalistDocument(var serverFileId: String?): DocumentTreeNode {

    constructor() : this(null)

    @Id
    var id: Long = 0
    var version: Long = 0
    override var position: Int = 0
    lateinit var folder: ToOne<DynalistFolder>

    val rootItem: DynalistItem? get() = DynalistItem.byServerId(serverFileId!!, "root")
    override val parent: ToOne<DynalistFolder> get() = folder

    companion object {
        val box: Box<DynalistDocument> get() = DynalistApp.instance.boxStore.boxFor()
    }
}