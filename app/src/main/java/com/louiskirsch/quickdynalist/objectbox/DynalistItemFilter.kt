package com.louiskirsch.quickdynalist.objectbox

import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.utils.*
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
class DynalistItemFilter: Parcelable {
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
    var name: String? = null

    @Convert(converter = LogicModeConverter::class, dbType = Integer::class)
    var logicMode: LogicMode = LogicMode.ALL

    var minRelativeDate: Long? = null
    var maxRelativeDate: Long? = null
    var minRelativeModifiedDate: Long? = null
    var maxRelativeModifiedDate: Long? = null

    lateinit var tags: ToMany<DynalistTag>
    @Convert(converter = LogicModeConverter::class, dbType = Integer::class)
    var tagsLogicMode: LogicMode = LogicMode.ALL
    lateinit var parent: ToOne<DynalistItem>
    var searchDepth: Int = 1
    var containsText: String? = null
    var hideIfParentIncluded: Boolean = false
    var showAsChecklist: Boolean = false
    @Convert(converter = OrderConverter::class, dbType = Integer::class)
    var sortOrder: Order = Order.MANUAL
    var isCompleted: Boolean? = null
    var hasImage: Boolean? = null

    override fun toString(): String = name ?: ""
    override fun hashCode(): Int = (id % Int.MAX_VALUE).toInt()
    override fun equals(other: Any?): Boolean {
        return if (id > 0)
            return id == (other as? DynalistItemFilter)?.id
        else
            false
    }

    val symbol: String?
        get() = EmojiFactory.emojis.firstOrNull { name?.contains(it) ?: false }

    val nameWithoutSymbol: String?
        get() = symbol?.let { name?.replace(it, "")?.trim() } ?: name

    private val today get() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private class QueryState { var hasPrecedingCondition: Boolean = false }
    private val query: Query<DynalistItem> get() {
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
        return box.query {
            applyBasicConditions()
            applyNativeConditions()
            // We can not run filters in ANY mode,
            // in that case we need to run the filters on all the data
            if (logicMode == LogicMode.ALL) {
                queryApplyFilters(createFilters())
                applySortOrder()
            }
        }
    }

    private fun QueryBuilder<DynalistItem>.applyNativeConditions() {
        val state = QueryState()
        applyModifiedDateConditions(state)
        if (containsText != null) {
            startCondition(state)
            contains(DynalistItem_.name, containsText!!)
            or()
            contains(DynalistItem_.note, containsText!!)
        }
        if (isCompleted != null) {
            startCondition(state)
            equal(DynalistItem_.isChecked, isCompleted!!)
        }
        if (parent.targetId > 0 && searchDepth == 1) {
            startCondition(state)
            equal(DynalistItem_.parentId, parent.targetId)
        }
        if (!state.hasPrecedingCondition && logicMode == LogicMode.ANY) {
            // If no conditions were applied, return no elements
            equal(DynalistItem_.clientId, 0)
        }
    }

    private fun QueryBuilder<DynalistItem>.applySortOrder() {
        when (sortOrder) {
            Order.MANUAL -> {
                order(DynalistItem_.serverParentId)
                order(DynalistItem_.position)
            }
            Order.MODIFIED_DATE -> {
                order(DynalistItem_.lastModified)
            }
            else -> Unit
        }
    }

    private fun applySortOrder(items: List<DynalistItem>): List<DynalistItem> {
        return when {
            sortOrder == Order.MANUAL && logicMode == LogicMode.ANY ->
                items.sortedWith(compareBy({ it.parent.targetId }, { it.position }))
            sortOrder == Order.DATE -> items.sortedBy { it.metaData.target.date }
            sortOrder == Order.MODIFIED_DATE && logicMode == LogicMode.ANY ->
                items.sortedBy { it.lastModified }
            else -> items
        }
    }

    private fun QueryBuilder<DynalistItem>.applyBasicConditions() {
        notEqual(DynalistItem_.name, "")
        equal(DynalistItem_.hidden, false)
        eager(DynalistItem_.metaData)
    }

    private fun createFilters(): MutableList<(DynalistItem) -> Boolean> {
        val filters = ArrayList<(DynalistItem) -> Boolean>()
        createDateFilter()?.let { filter -> filters.add {
            it.metaData.target.date?.let { date -> filter(date) } ?: false
        }}
        if (hasImage != null) {
            filters.add {
                if (hasImage!!) it.image != null
                else it.image == null
            }
        }
        if (parent.targetId > 0 && searchDepth > 1) {
            filters.add { it.hasParent(parent.targetId, searchDepth) }
        }
        if (tags.isNotEmpty()) {
            filters.add { candidate ->
                // TODO I can't use metadata here because I can't only access the ids
                when (tagsLogicMode) {
                    LogicMode.ALL -> tags.all { candidate.tags.contains(it.fullName) }
                    LogicMode.ANY -> tags.any { candidate.tags.contains(it.fullName) }
                    else -> false
                }
            }
        }
        return filters
    }

    private fun QueryBuilder<DynalistItem>.queryApplyFilters(
            filters: List<(DynalistItem) -> Boolean>) {
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

    private fun dataApplyFilters(filters: List<(DynalistItem) -> Boolean>): List<DynalistItem> {
        return if (filters.isNotEmpty()) {
            DynalistItem.box.query { applyBasicConditions() }.find().filter { item ->
                when (logicMode) {
                    LogicMode.ALL -> filters.all { it(item) }
                    LogicMode.ANY -> filters.any { it(item) }
                    else -> false
                }
            }
        } else {
            emptyList()
        }
    }

    private fun <T> QueryBuilder<T>.startCondition(state: QueryState) {
        // No need to call and(), because it is the default
        //if (state.hasPrecedingCondition && logicMode == LogicMode.ANY) or()
        if (state.hasPrecedingCondition) {
            if (logicMode == LogicMode.ANY) or()
        }
        state.hasPrecedingCondition = true
    }

    private fun createDateFilter(): ((Date) -> Boolean)? {
        if (minRelativeDate != null && maxRelativeDate != null) {
            val today = today
            return { date -> date.time in
                    (today.time + minRelativeDate!!) until (today.time + maxRelativeDate!!) }
        } else if (minRelativeDate != null) {
            return { date -> date.time >= today.time + minRelativeDate!! }
        } else if (maxRelativeDate != null) {
            return { date -> date.time < today.time + maxRelativeDate!! }
        } else {
            return null
        }
    }

    private fun QueryBuilder<DynalistItem>.applyModifiedDateConditions(state: QueryState) {
        if (minRelativeModifiedDate != null && maxRelativeModifiedDate != null) {
            startCondition(state)
            val today = today
            between(DynalistItem_.lastModified,
                    today.time + minRelativeModifiedDate!! - 1,
                    today.time + maxRelativeModifiedDate!!)
        } else if (minRelativeModifiedDate != null) {
            startCondition(state)
            greater(DynalistItem_.lastModified, today.time + minRelativeModifiedDate!! - 1)
        } else if (maxRelativeModifiedDate != null) {
            startCondition(state)
            less(DynalistItem_.lastModified, today.time + maxRelativeModifiedDate!!)
        }
    }

    val items: List<DynalistItem> get() = postQueryFilter(query.find())

    private fun postQueryFilter(origItems: List<DynalistItem>): List<DynalistItem> {
        return origItems.let { items ->
            if (logicMode == LogicMode.ANY) {
                items.union(dataApplyFilters(createFilters())).toList()
            } else {
                items
            }
        }.let { items ->
            if (hideIfParentIncluded) {
                val foundSet = items.map { it.clientId }.toSet()
                items.filter { it.parent.targetId !in foundSet }
            } else {
                items
            }
        }.let { applySortOrder(it) }
    }

    val liveData: LiveData<List<DynalistItem>>
        get() = TransformedOBLiveData(query) { postQueryFilter(it) }

    fun <T> transformedLiveData(transformer: (List<DynalistItem>) -> List<T>)
        = TransformedOBLiveData(query) { transformer(postQueryFilter(it)) }

    companion object {
        const val LOCATION_TYPE = "filter"
        val box get() = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()

        @Suppress("unused")
        @JvmField
        val CREATOR = object : Parcelable.Creator<DynalistItemFilter> {
            override fun createFromParcel(parcel: Parcel): DynalistItemFilter {
                with (parcel) {
                    val filterBox = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
                    val tagBox = DynalistApp.instance.boxStore.boxFor<DynalistTag>()
                    return DynalistItemFilter().apply {
                        id = readLong()
                        name = readString()
                        logicMode = LogicMode.values()[readInt()]
                        minRelativeDate = readNullableLong()
                        maxRelativeDate = readNullableLong()
                        minRelativeModifiedDate = readNullableLong()
                        maxRelativeModifiedDate = readNullableLong()
                        filterBox.attach(this)
                        tags.clear()
                        tags.addAll(tagBox.get(createLongArray()!!))
                        tagsLogicMode = LogicMode.values()[readInt()]
                        parent.targetId = readLong()
                        searchDepth = readInt()
                        containsText = readString()
                        hideIfParentIncluded = readInt() > 0
                        showAsChecklist = readInt() > 0
                        sortOrder = Order.values()[readInt()]
                        isCompleted = readNullableBoolean()
                        hasImage = readNullableBoolean()
                    }
                }
            }
            override fun newArray(size: Int) = arrayOfNulls<DynalistItemFilter>(size)
        }
    }

    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeLong(id)
            writeString(name)
            writeInt(logicMode.ordinal)
            writeNullableLong(minRelativeDate)
            writeNullableLong(maxRelativeDate)
            writeNullableLong(minRelativeModifiedDate)
            writeNullableLong(maxRelativeModifiedDate)
            writeLongArray(tags.map { it.id }.toLongArray())
            writeInt(tagsLogicMode.ordinal)
            writeLong(parent.targetId)
            writeInt(searchDepth)
            writeString(containsText)
            writeInt(hideIfParentIncluded.int)
            writeInt(showAsChecklist.int)
            writeInt(sortOrder.ordinal)
            writeNullableBoolean(isCompleted)
            writeNullableBoolean(hasImage)
        }
    }

}