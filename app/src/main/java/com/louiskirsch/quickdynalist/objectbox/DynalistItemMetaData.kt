package com.louiskirsch.quickdynalist.objectbox

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.util.*

@Entity
class DynalistItemMetaData() {

    @Id var id: Long = 0

    var date: Date? = null
    var image: String? = null
    var symbol: String? = null
    lateinit var tags: ToMany<DynalistTag>
    lateinit var linkedItem: ToOne<DynalistItem>

    constructor(fromItem: DynalistItem) : this() {
        update(fromItem)
    }

    fun update(fromItem: DynalistItem) {
        date = fromItem.date
        image = fromItem.image
        symbol = fromItem.symbol
        tags.clear()
        tags.addAll(fromItem.tags.map { DynalistTag.find(it) })
        linkedItem.target = fromItem.linkedItem
    }
}