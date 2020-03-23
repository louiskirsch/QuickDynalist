package com.louiskirsch.quickdynalist.objectbox

import com.louiskirsch.quickdynalist.DynalistApp
import io.objectbox.Box
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.kotlin.boxFor
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
class DynalistFolder(var serverFolderId: String?): DocumentTreeNode {

    constructor() : this(null)

    @Id
    var id: Long = 0
    var title: String? = null
    override var position: Int = 0
    override lateinit var parent: ToOne<DynalistFolder>
    @Backlink(to = "parent")
    lateinit var children: ToMany<DynalistFolder>
    @Backlink(to = "folder")
    lateinit var documents: ToMany<DynalistDocument>

    override fun hashCode(): Int = (id % Int.MAX_VALUE).toInt()

    companion object {
        const val LOCATION_TYPE = "folder"
        val box: Box<DynalistFolder> get() = DynalistApp.instance.boxStore.boxFor()
    }
}