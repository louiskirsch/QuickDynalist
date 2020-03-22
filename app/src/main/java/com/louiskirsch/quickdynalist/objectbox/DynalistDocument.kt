package com.louiskirsch.quickdynalist.objectbox

import com.louiskirsch.quickdynalist.DynalistApp
import io.objectbox.Box
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToOne

@Entity
class DynalistDocument(var serverFileId: String?) {

    constructor() : this(null)

    @Id
    var id: Long = 0
    var version: Long = 0
    var position: Int = 0
    lateinit var folder: ToOne<DynalistFolder>

    val rootItem: DynalistItem? get() = DynalistItem.byServerId(serverFileId!!, "root")

    companion object {
        val box: Box<DynalistDocument> get() = DynalistApp.instance.boxStore.boxFor()
    }
}