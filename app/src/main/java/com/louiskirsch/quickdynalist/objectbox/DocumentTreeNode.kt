package com.louiskirsch.quickdynalist.objectbox

import io.objectbox.relation.ToOne

interface DocumentTreeNode {
    val parent: ToOne<DynalistFolder>
    var position: Int
}