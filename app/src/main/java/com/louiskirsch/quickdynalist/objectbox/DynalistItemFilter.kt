package com.louiskirsch.quickdynalist.objectbox

import androidx.lifecycle.LiveData
import com.louiskirsch.quickdynalist.DynalistApp
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.converter.PropertyConverter
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.Query
import io.objectbox.query.QueryBuilder
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.util.*

@Entity
class DynalistItemFilter {
    enum class LogicMode { UNKNOWN, ALL, ANY }
    class LogicModeConverter: PropertyConverter<LogicMode, Int> {
        override fun convertToDatabaseValue(entityProperty: LogicMode?): Int
                = entityProperty?.ordinal ?: 0
        override fun convertToEntityProperty(databaseValue: Int?): LogicMode
                = databaseValue?.let { LogicMode.values()[it] } ?: LogicMode.UNKNOWN
    }
    enum class Order { UNKNOWN, MANUAL, DATE, MODIFIED_DATE }
    class OrderConverter: PropertyConverter<Order, Int> {
        override fun convertToDatabaseValue(entityProperty: Order?): Int
                = entityProperty?.ordinal ?: 0
        override fun convertToEntityProperty(databaseValue: Int?): Order
                = databaseValue?.let { Order.values()[it] } ?: Order.UNKNOWN
    }

    @Id var id: Long = 0

    @Convert(converter = LogicModeConverter::class, dbType = Integer::class)
    var logicMode: LogicMode = LogicMode.ALL

    var minRelativeDate: Long? = null
    var maxRelativeDate: Long? = null
    var minRelativeModifiedDate: Long? = null
    var maxRelativeModifiedDate: Long? = null

    lateinit var tags: ToMany<DynalistTag>
    lateinit var parent: ToOne<DynalistItem>
    var searchDepth: Int = 1
    var containsText: String? = null
    var hideIfParentIncluded: Boolean = false
    @Convert(converter = OrderConverter::class, dbType = Integer::class)
    var sortOrder: Order = Order.MANUAL
    var isCompleted: Boolean? = null
    var hasImage: Boolean? = null

    private val query: Query<DynalistItem> get() {
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
        val now = Date()
        return box.query {
            val filters = ArrayList<(DynalistItem) -> Boolean>()
            if (hasImage != null || minRelativeDate != null || maxRelativeDate != null) {
                link(DynalistItem_.metaData).run {
                    applyDateConditions(now)
                    if (hasImage != null) {
                        if (hasImage!!)
                            notNull(DynalistItemMetaData_.image)
                        else
                            isNull(DynalistItemMetaData_.image)
                    }
                }
                nextCondition()
            }
            applyModifiedDateConditions(now)
            if (containsText != null) {
                contains(DynalistItem_.name, containsText!!)
                or()
                contains(DynalistItem_.note, containsText!!)
                nextCondition()
            }
            if (isCompleted != null) {
                equal(DynalistItem_.isChecked, isCompleted!!)
                nextCondition()
            }
            if (parent.targetId > 0 && searchDepth == 1) {
                equal(DynalistItem_.parentId, parent.targetId)
                nextCondition()
            }
            if (parent.targetId > 0 && searchDepth > 1) {
                filters.add { it.hasParent(parent.targetId, searchDepth) }
            }
            if (tags.isNotEmpty()) {
                filters.add { candidate ->
                    val metaData = candidate.metaData.target
                    when (logicMode) {
                        LogicMode.ALL -> tags.all { metaData.tags.contains(it) }
                        LogicMode.ANY -> tags.any { metaData.tags.contains(it) }
                        else -> false
                    }
                }
            }
            applyFilters(filters)
            when (sortOrder) {
                Order.MANUAL -> {
                    order(DynalistItem_.parentId)
                    order(DynalistItem_.position)
                }
                Order.DATE -> {
                    link(DynalistItem_.metaData).run {
                        // Not sure this order is applied correctly
                        order(DynalistItemMetaData_.date)
                    }
                }
                Order.MODIFIED_DATE -> {
                    order(DynalistItem_.lastModified)
                }
                else -> Unit
            }
        }
    }

    private fun <T> QueryBuilder<T>.applyFilters(filters: List<(T) -> Boolean>) = {
        if (filters.isNotEmpty()) {
            filter { item ->
                when (logicMode) {
                    LogicMode.ALL -> filters.all { it(item) }
                    LogicMode.ANY -> filters.any { it(item) }
                    else -> false
                }
            }
        }
    }

    private fun <T> QueryBuilder<T>.nextCondition() = {
        // No need to call and(), because it is the default
        if (logicMode == LogicMode.ANY) or()
    }

    private fun QueryBuilder<DynalistItemMetaData>.applyDateConditions(now: Date) {
        if (minRelativeDate != null && maxRelativeDate != null) {
            between(DynalistItemMetaData_.date,
                    now.time + minRelativeDate!!,
                    now.time + maxRelativeDate!!)
            nextCondition()
        } else if (minRelativeDate != null) {
            greater(DynalistItemMetaData_.date, now.time + minRelativeDate!!)
            nextCondition()
        } else if (maxRelativeDate != null) {
            less(DynalistItemMetaData_.date, now.time + maxRelativeDate!!)
            nextCondition()
        }
    }

    private fun QueryBuilder<DynalistItem>.applyModifiedDateConditions(now: Date) {
        if (minRelativeModifiedDate != null && maxRelativeModifiedDate != null) {
            between(DynalistItem_.lastModified,
                    now.time + minRelativeModifiedDate!!,
                    now.time + maxRelativeModifiedDate!!)
            nextCondition()
        } else if (minRelativeModifiedDate != null) {
            greater(DynalistItem_.lastModified, now.time + minRelativeModifiedDate!!)
            nextCondition()
        } else if (maxRelativeModifiedDate != null) {
            less(DynalistItem_.lastModified, now.time + maxRelativeModifiedDate!!)
            nextCondition()
        }
    }

    val items: List<DynalistItem> get() = postQueryFilter(query.find())

    private fun postQueryFilter(items: List<DynalistItem>) = if (hideIfParentIncluded) {
            val foundSet = items.toSet()
            items.filter { it.parent.target !in foundSet }
        } else {
            items
        }

    val liveData: LiveData<List<DynalistItem>>
        get() = TransformedOBLiveData(query) { postQueryFilter(it) }

    fun <T> transformedLiveData(transformer: (List<DynalistItem>) -> List<T>)
        = TransformedOBLiveData(query) { transformer(postQueryFilter(it)) }
}